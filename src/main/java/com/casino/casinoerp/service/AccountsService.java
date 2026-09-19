package com.casino.casinoerp.service;

import com.casino.casinoerp.dto.AccountsDtos.*;
import com.casino.casinoerp.repository.AccountsRepository;
import com.casino.casinoerp.repository.BusinessDateRepository;
import com.casino.casinoerp.security.Role;
import com.casino.casinoerp.exception.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.time.*;
import java.util.*;
import static com.casino.casinoerp.repository.AccountsRepository.args;

@Service
@Transactional
public class AccountsService {
    private final AccountsRepository db;
    private final AuthenticatedUserService users;
    private final BusinessDateService dates;
    private final BusinessDateRepository dateRepository;
    private final AuditLogService audit;
    private final AccountsEvidenceStorage storage;
    private final ObjectMapper json;
    public AccountsService(AccountsRepository db,AuthenticatedUserService users,BusinessDateService dates,
            BusinessDateRepository dateRepository,AuditLogService audit,AccountsEvidenceStorage storage,ObjectMapper json) {
        this.db=db;this.users=users;this.dates=dates;this.dateRepository=dateRepository;this.audit=audit;this.storage=storage;this.json=json;
    }
    private record Actor(UUID id,Role role,String username) {}
    private Actor actor() {
        var u=users.getRequiredUser();var role=AuthenticatedUserService.requireActiveRole(u);
        if(!Set.of(Role.STORE_MANAGER,Role.ACCOUNTANT_HEAD,Role.ACCOUNTS_MANAGER,Role.DIRECTOR).contains(role))throw new AccessDeniedException("No Accounts workflow access.");
        return new Actor(u.getId(),role,u.getUsername());
    }
    private boolean preparer(Actor a) {return a.role()==Role.STORE_MANAGER||a.role()==Role.ACCOUNTANT_HEAD;}
    private String visibility(Actor a) {
        if(preparer(a))return "b.recorded_by=:actor";
        return a.role()==Role.ACCOUNTS_MANAGER?"b.ever_submitted":"b.ever_verified";
    }
    private void visible(Actor a,Map<String,Object> b) {
        boolean allowed=preparer(a)?a.id().equals(b.get("recorded_by")):Boolean.TRUE.equals(b.get(a.role()==Role.ACCOUNTS_MANAGER?"ever_submitted":"ever_verified"));
        if(!allowed)throw new ResourceNotFoundException("Accounts record not found.");
    }
    private static String text(String s,int max,boolean required) {
        String v=s==null?"":s.trim();if(v.length()>max || (required&&v.isEmpty()) || v.chars().anyMatch(c->c<32&&c!='\n'))throw new IllegalArgumentException("Invalid Accounts text field.");return v;
    }
    private static void conflict(boolean bad,String message) {if(bad)throw new ResourceConflictException(message);}
    private static long number(Map<String,Object> b,String key) {return ((Number)b.get(key)).longValue();}
    private void version(Map<String,Object> b,Long expected) {conflict(expected==null||expected!=number(b,"version"),"Bill changed. Refresh before continuing.");}
    private String encode(Object value) {try{return json.writeValueAsString(value);}catch(Exception e){throw new IllegalStateException(e);}}
    private <T>T decode(Object value,Class<T> type) {try{return json.readValue(value.toString(),type);}catch(Exception e){throw new IllegalStateException("Invalid persisted Accounts record.",e);}}
    private String hash(Object value) {return AccountsEvidenceStorage.checksum(encode(value).getBytes(java.nio.charset.StandardCharsets.UTF_8));}
    private String fingerprint(Object... values) {return hash(Arrays.asList(values));}
    private Receipt replay(String rawKey,Actor a,String fingerprint) { return replay(rawKey,a,fingerprint,true); }
    private Receipt replay(String rawKey,Actor a,String fingerprint,boolean billReceipt) {
        String key=text(rawKey,100,true);db.retryLock(key);
        var r=db.row("select actor_id,fingerprint,response from casino.accounts_operations where retry_key=:key",Map.of("key",key));
        if(r==null)return null;
        conflict(!a.id().equals(r.get("actor_id"))||!fingerprint.equals(r.get("fingerprint")),"Retry key belongs to a different actor or intent.");
        var result=decode(r.get("response"),Receipt.class);
        if(billReceipt)visible(a,db.bill(result.id(),false));
        return result;
    }
    private Receipt complete(String key,Actor a,String fp,UUID id,long version,String action,LocalDate date) {
        var receipt=new Receipt(id,version,UUID.randomUUID());
        db.update("insert into casino.accounts_operations values(:key,:actor,:fp,cast(:response as jsonb),:now)",args("key",key.trim(),"actor",a.id(),"fp",fp,"response",encode(receipt),"now",dates.currentCasinoDateTime()));
        audit.logForBusinessDate(date,"ACCOUNTS_"+action,"ACCOUNTS",id,a.id(),"Accounts bill workflow only; no payment or ledger posting.");return receipt;
    }
    private LocalDate date(Map<String,Object>b) {return (LocalDate)b.get("business_date");}
    private Snapshot snapshot(UUID id,long revision) {
        return decode(db.required("select snapshot from casino.accounts_bill_revisions where bill_id=:id and revision=:revision",args("id",id,"revision",revision)).get("snapshot"),Snapshot.class);
    }
    private void revision(UUID id,int revision,Snapshot value,Actor actor) {
        db.update("insert into casino.accounts_bill_revisions values(:id,:revision,cast(:snapshot as jsonb),:actor,:now)",args("id",id,"revision",revision,"snapshot",encode(value),"actor",actor.id(),"now",dates.currentCasinoDateTime()));
    }
    private void editable(Actor a,Map<String,Object> b) {
        visible(a,b);
        if(!preparer(a)||!a.id().equals(b.get("recorded_by")))throw new AccessDeniedException("Only the original preparer may correct this bill.");
        conflict(!Set.of("DRAFT","RETURNED").contains(b.get("status")),"Only Draft or Returned bills can be corrected.");
    }
    private String normalized(String reference) {return text(reference,150,true).replaceAll("\\s+"," ").toUpperCase(Locale.ROOT);}
    private void duplicate(UUID party,String normalized,UUID id) {
        db.retryLock("invoice:"+party+":"+normalized);
        var other=db.row("select id from casino.accounts_bills where party_id=:party and normalized_invoice_reference=:ref",args("party",party,"ref",normalized));
        conflict(other!=null&&!other.get("id").equals(id),"Supplier invoice reference already exists, including rejected bills. Numbering exceptions require a policy decision.");
    }
    private Invoice canonicalInvoice(Invoice i,boolean identity) {
        AccountsAmounts.validate(i);
        var sources=i.sources()==null?List.<Source>of():i.sources();
        if(sources.size()>20 || sources.stream().anyMatch(s->s==null||s.id()==null||s.type()==null||!Set.of("PROCUREMENT","RECEIPT","HOTEL").contains(s.type())))throw new IllegalArgumentException("Invalid source association.");
        var lines=i.lines().stream().map(l->new Line(l.description().trim(),AccountsAmounts.amount(l.amount()))).toList();
        var ordered=sources.stream().sorted(Comparator.comparing(s->s.type()+s.id())).toList();
        return new Invoice(i.partyId(),identity?normalized(i.invoiceReference()):i.invoiceReference(),i.invoiceDate(),i.dueDate(),i.currency(),
            AccountsAmounts.amount(i.subtotal()),AccountsAmounts.amount(i.discount()),AccountsAmounts.amount(i.tax()),AccountsAmounts.amount(i.total()),lines,ordered);
    }
    private Snapshot validate(Invoice i,List<UUID> evidence) {
        AccountsAmounts.validate(i);text(i.invoiceReference(),150,true);
        if(i.partyId()==null||i.invoiceDate()==null||i.dueDate()!=null&&i.dueDate().isBefore(i.invoiceDate()))throw new IllegalArgumentException("Select a party and valid invoice/due dates.");
        var party=db.required("select * from casino.accounts_parties where id=:id",Map.of("id",i.partyId()));
        var sources=i.sources()==null?List.<Source>of():i.sources();if(sources.size()>20||new HashSet<>(sources).size()!=sources.size())throw new IllegalArgumentException("At most 20 distinct source links are allowed.");
        List<SourceSnapshot> snapshots=new ArrayList<>();
        // Bounded source set, grouped into at most three reads; no transaction-history hydration.
        for(String type:List.of("PROCUREMENT","RECEIPT","HOTEL")) {
            var ids=sources.stream().filter(s->s!=null&&type.equals(s.type())).map(Source::id).toList();
            if(ids.isEmpty())continue;if(ids.contains(null))throw new IllegalArgumentException("Invalid source ID.");
            var found=db.rows(sourceSql(type)+" and id in (:ids)",Map.of("ids",ids));
            if(found.size()!=ids.size())throw new IllegalArgumentException("Source record unavailable.");
            found.forEach(r->snapshots.add(new SourceSnapshot(type,(UUID)r.get("id"),(String)r.get("reference"))));
        }
        if(snapshots.size()!=sources.size())throw new IllegalArgumentException("Unsupported source type.");
        snapshots.sort(Comparator.comparing(s->s.type()+s.id()));
        var canonical=canonicalInvoice(i,false);
        return new Snapshot(canonical,(String)party.get("code"),(String)party.get("name"),(String)party.get("kind"),snapshots,List.copyOf(evidence));
    }
    private String sourceSql(String type) {
        return switch(type) {
            case "PROCUREMENT" -> "select id,reference from casino.store_procurements where true";
            case "RECEIPT" -> "select id,reference from casino.store_movements where movement_type='RECEIPT'";
            case "HOTEL" -> "select id,booking_code as reference from customer.hotel_bookings where true";
            default -> throw new IllegalArgumentException("Unsupported source type.");
        };
    }
    private void page(int page,int size) {if(page<0||size<1||size>100||(long)page*size>1000000)throw new IllegalArgumentException("Invalid page/size.");}
    private <T> Page<T> paged(List<T> rows,int page,int size) {return new Page<>(rows.stream().limit(size).toList(),rows.size()>size,page,size);}
    @Transactional(readOnly=true) public Map<String,Object> context() {
        var a=actor();return args("actorId",a.id(),"username",a.username(),"role",a.role().name(),"businessDate",dates.getCurrentOpenBusinessDate().map(d->d.getBusinessDate()).orElse(null),"currency","NPR");
    }
    @Transactional(readOnly=true) public Page<Map<String,Object>> parties(String q,int page,int size) {
        var a=actor();if(!preparer(a))throw new AccessDeniedException("Party selection is for preparers.");page(page,size);
        return paged(db.rows("select id,code,name,kind from casino.accounts_parties where position(lower(:q) in lower(code||' '||name))>0 order by code,id limit :limit offset :offset",args("q",text(q,100,false),"limit",size+1,"offset",page*size)),page,size);
    }
    public Receipt party(PartyWrite r) {
        var a=actor();if(!preparer(a))throw new AccessDeniedException("Party creation is for preparers.");
        String code=text(r.code(),60,true).toUpperCase(Locale.ROOT),name=text(r.name(),200,true),kind=text(r.kind(),20,true);
        if(!code.matches("[A-Z0-9_-]+")||!Set.of("SUPPLIER","HOTEL","OTHER").contains(kind))throw new IllegalArgumentException("Invalid party code or kind.");
        String fp=fingerprint("PARTY",a.id(),code,name,kind);var replay=replay(r.idempotencyKey(),a,fp,false);if(replay!=null)return replay;
        db.retryLock("party:"+code);conflict(db.row("select id from casino.accounts_parties where code=:code",Map.of("code",code))!=null,"Party code already exists. Select the existing stable identity.");
        UUID id=UUID.randomUUID();db.update("insert into casino.accounts_parties values(:id,:code,:name,:kind,:actor,:now)",args("id",id,"code",code,"name",name,"kind",kind,"actor",a.id(),"now",dates.currentCasinoDateTime()));
        return complete(r.idempotencyKey(),a,fp,id,0,"PARTY_CREATED",null);
    }
    @Transactional(readOnly=true) public Page<Map<String,Object>> sources(String type,String q,int page,int size) {
        if(!preparer(actor()))throw new AccessDeniedException("Source selection is for preparers.");page(page,size);
        return paged(db.rows("select * from ("+sourceSql(type)+") s where position(lower(:q) in lower(reference))>0 order by reference,id limit :limit offset :offset",args("q",text(q,100,false),"limit",size+1,"offset",page*size)),page,size);
    }
    public Receipt write(UUID id,BillWrite r) {
        var a=actor();if(!preparer(a))throw new AccessDeniedException("Only preparers may record bills.");
        String fp=fingerprint(id==null?"CREATE":"EDIT",a.id(),id,r.expectedVersion(),r.expectedBusinessDate(),canonicalInvoice(r.invoice(),true));
        var replay=replay(r.idempotencyKey(),a,fp);if(replay!=null)return replay;
        Map<String,Object> b=null;List<UUID> evidence=List.of();int revision=1;long version=0;LocalDate date;
        if(id==null) {
            dateRepository.acquireLifecycleLock();date=dates.getCurrentOpenBusinessDate().orElseThrow(()->new ResourceConflictException("No Business Date is OPEN.")).getBusinessDate();
            conflict(!date.equals(r.expectedBusinessDate()),"Recording Business Date changed. Refresh before creating a bill.");id=UUID.randomUUID();
        } else {
            b=db.bill(id,true);editable(a,b);version(b,r.expectedVersion());date=date(b);revision=(int)number(b,"revision")+1;version=number(b,"version")+1;evidence=snapshot(id,revision-1).evidenceIds();
        }
        Snapshot value=validate(r.invoice(),evidence);duplicate(r.invoice().partyId(),normalized(r.invoice().invoiceReference()),id);
        var params=args("id",id,"party",r.invoice().partyId(),"ref",r.invoice().invoiceReference().trim(),"normalized",normalized(r.invoice().invoiceReference()),"actor",a.id(),"now",dates.currentCasinoDateTime(),"date",date,"revision",revision,"version",version);
        if(b==null) db.update("insert into casino.accounts_bills(id,party_id,invoice_reference,normalized_invoice_reference,recorded_by,recorded_at,business_date,status) values(:id,:party,:ref,:normalized,:actor,:now,:date,'DRAFT')",params);
        else db.update("update casino.accounts_bills set party_id=:party,invoice_reference=:ref,normalized_invoice_reference=:normalized,revision=:revision,version=:version,verification_id=null where id=:id",params);
        revision(id,revision,value,a);return complete(r.idempotencyKey(),a,fp,id,version,b==null?"CREATED":"CORRECTED",date);
    }
    @Transactional(readOnly=true) public Page<Map<String,Object>> bills(String q,String status,int page,int size) {
        var a=actor();page(page,size);String st=text(status,40,false);
        if(!st.isEmpty()&&!Set.of("DRAFT","SUBMITTED","AWAITING_DIRECTOR_APPROVAL","HELD","RETURNED","REJECTED","APPROVED_FOR_PAYMENT").contains(st))throw new IllegalArgumentException("Invalid status.");
        var rows=db.rows("""
            select b.id,b.invoice_reference,b.business_date,b.recorded_at,b.recorded_by,b.status,b.held_stage,b.version,b.revision,
            r.snapshot->>'partyName' as party_name,r.snapshot->'invoice'->>'total' as total
            from casino.accounts_bills b join casino.accounts_bill_revisions r on r.bill_id=b.id and r.revision=b.revision
            where ("""+visibility(a)+") and (:status='' or b.status=:status) and position(lower(:q) in lower(b.invoice_reference||' '||(r.snapshot->>'partyName')))>0 order by b.recorded_at desc,b.id limit :limit offset :offset",
            args("actor",a.id(),"status",st,"q",text(q,100,false),"limit",size+1,"offset",page*size));return paged(rows,page,size);
    }
    private List<Map<String,Object>> evidence(UUID bill,List<UUID> ids) {
        if(ids.isEmpty())return List.of();
        return db.rows("select e.id,e.original_name,e.media_type,e.byte_size,e.checksum,e.invoice_document,e.replaces_id,e.uploaded_by,u.username as uploader,e.uploaded_at from casino.accounts_evidence e join core.users u on u.id=e.uploaded_by where e.bill_id=:bill and e.id in (:ids) order by e.uploaded_at,e.id",args("bill",bill,"ids",ids));
    }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ) public Map<String,Object> detail(UUID id) {
        var a=actor();var b=db.bill(id,false);visible(a,b);var s=snapshot(id,number(b,"revision"));
        var verification=b.get("verification_id")==null?null:db.required("select d.*,u.username as actor_name from casino.accounts_decisions d join core.users u on u.id=d.actor_id where d.id=:id",Map.of("id",b.get("verification_id")));
        return args("bill",b,"snapshot",s,"evidence",evidence(id,s.evidenceIds()),"verification",verification);
    }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ) public Page<Map<String,Object>> history(UUID id,String kind,int page,int size) {
        var a=actor();visible(a,db.bill(id,false));page(page,size);
        String sql=switch(kind) {
            case "revisions" -> "select r.revision,r.snapshot,r.created_by,r.created_at,u.username as actor_name from casino.accounts_bill_revisions r join core.users u on u.id=r.created_by where r.bill_id=:id order by r.revision desc";
            case "decisions" -> "select d.*,u.username as actor_name from casino.accounts_decisions d join core.users u on u.id=d.actor_id where d.bill_id=:id order by d.decided_at desc,d.id";
            case "evidence" -> "select e.id,e.original_name,e.media_type,e.byte_size,e.checksum,e.invoice_document,e.replaces_id,e.uploaded_by,e.uploaded_at,u.username as uploader from casino.accounts_evidence e join core.users u on u.id=e.uploaded_by where e.bill_id=:id order by e.uploaded_at desc,e.id";
            default -> throw new IllegalArgumentException("Invalid history kind.");
        };
        var rows=db.rows(sql+" limit :limit offset :offset",args("id",id,"limit",size+1,"offset",page*size));
        if(kind.equals("revisions"))rows.forEach(r->r.put("snapshot",decode(r.get("snapshot"),Snapshot.class)));
        return paged(rows,page,size);
    }
    public Receipt upload(UUID id,Action request,String filename,String mediaType,boolean invoiceDocument,UUID replaces,byte[] bytes) {
        var a=actor();String name=AccountsEvidenceStorage.filename(filename);String checksum=AccountsEvidenceStorage.checksum(bytes);
        String fp=fingerprint("UPLOAD",a.id(),id,request.expectedVersion(),name,mediaType,invoiceDocument,replaces,checksum);
        var replay=replay(request.idempotencyKey(),a,fp);if(replay!=null)return replay;
        var b=db.bill(id,true);editable(a,b);version(b,request.expectedVersion());var previous=snapshot(id,number(b,"revision"));var ids=new ArrayList<>(previous.evidenceIds());
        if(replaces!=null)conflict(!ids.remove(replaces),"Only current evidence on this bill may be replaced.");
        conflict(ids.size()>=20,"At most 20 current evidence files are allowed.");
        UUID key=storage.store(bytes,mediaType),evidenceId=UUID.randomUUID();ids.add(evidenceId);
        db.update("insert into casino.accounts_evidence values(:id,:bill,:key,:name,:type,:size,:checksum,:invoice,:replaces,:actor,:now)",args("id",evidenceId,"bill",id,"key",key,"name",name,"type",mediaType,"size",bytes.length,"checksum",checksum,"invoice",invoiceDocument,"replaces",replaces,"actor",a.id(),"now",dates.currentCasinoDateTime()));
        int revision=(int)number(b,"revision")+1;long v=number(b,"version")+1;
        revision(id,revision,new Snapshot(previous.invoice(),previous.partyCode(),previous.partyName(),previous.partyKind(),previous.sourceSnapshots(),ids),a);
        db.update("update casino.accounts_bills set revision=:revision,version=:v,verification_id=null where id=:id",args("id",id,"revision",revision,"v",v));
        return complete(request.idempotencyKey(),a,fp,id,v,"EVIDENCE_STORED",date(b));
    }
    public record Document(String name,String type,byte[] bytes) {}
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ) public Document document(UUID id,UUID evidenceId) {
        var a=actor();visible(a,db.bill(id,false));
        var e=db.required("select * from casino.accounts_evidence where bill_id=:bill and id=:id",args("bill",id,"id",evidenceId));
        return new Document((String)e.get("original_name"),(String)e.get("media_type"),storage.read((UUID)e.get("storage_key"),(String)e.get("checksum"),(int)number(e,"byte_size")));
    }
    private void checkStoredInvoice(UUID id,Snapshot s) {
        conflict(s.evidenceIds().isEmpty(),"A successfully stored invoice document is required.");
        var rows=db.rows("select * from casino.accounts_evidence where bill_id=:bill and id in (:ids)",args("bill",id,"ids",s.evidenceIds()));
        conflict(rows.size()!=s.evidenceIds().size()||rows.stream().noneMatch(r->Boolean.TRUE.equals(r.get("invoice_document"))),"Stored invoice evidence is required.");
        for(var e:rows)storage.read((UUID)e.get("storage_key"),(String)e.get("checksum"),(int)number(e,"byte_size"));
    }
    public Receipt action(UUID id,String action,Action r) {
        var a=actor();String reason=text(r.reason(),1000,false);
        if(!Set.of("SUBMIT","VERIFY","APPROVE","HOLD","RESUME","RETURN","REJECT").contains(action))throw new IllegalArgumentException("Unknown bill action.");
        String fp=fingerprint(action,a.id(),id,r.expectedVersion(),reason);var replay=replay(r.idempotencyKey(),a,fp);if(replay!=null)return replay;
        var b=db.bill(id,true);visible(a,b);version(b,r.expectedVersion());
        String state=(String)b.get("status"),stage="PREPARATION",next=state,held=null;var s=snapshot(id,number(b,"revision"));
        UUID verification=(UUID)b.get("verification_id"),approval=null,decision=UUID.randomUUID();boolean submitted=Boolean.TRUE.equals(b.get("ever_submitted")),verified=Boolean.TRUE.equals(b.get("ever_verified"));
        if(action.equals("SUBMIT")) {
            editable(a,b);next="SUBMITTED";submitted=true;verification=null;
        } else {
            stage=state.equals("HELD")?(String)b.get("held_stage"):state.equals("SUBMITTED")?"VERIFICATION":state.equals("AWAITING_DIRECTOR_APPROVAL")?"APPROVAL":null;
            conflict(stage==null,"Bill is not at a review stage.");
            if(a.role()!=(stage.equals("VERIFICATION")?Role.ACCOUNTS_MANAGER:Role.DIRECTOR))throw new AccessDeniedException("This action requires the reviewer for the current stage.");
            if(a.id().equals(b.get("recorded_by")))throw new AccessDeniedException("The original preparer cannot review their own bill.");
            Map<String,Object> verificationRow=null;
            if(stage.equals("APPROVAL")) {
                conflict(verification==null,"Current revision requires verification.");
                verificationRow=db.required("select * from casino.accounts_decisions where id=:id",Map.of("id",verification));
                conflict(number(verificationRow,"revision")!=number(b,"revision")||!hash(s.evidenceIds()).equals(verificationRow.get("evidence_digest")),"Current revision/evidence is not verified.");
                if(a.id().equals(verificationRow.get("actor_id")))throw new AccessDeniedException("The verifier cannot perform Director review of the same revision.");
            }
            if(Set.of("HOLD","RETURN","REJECT").contains(action)&&reason.isBlank())throw new IllegalArgumentException("A reason is required.");
            switch(action) {
                case "VERIFY" -> {conflict(!state.equals("SUBMITTED"),"Only a submitted bill can be verified.");checkStoredInvoice(id,s);next="AWAITING_DIRECTOR_APPROVAL";verified=true;verification=decision;}
                case "APPROVE" -> {conflict(!state.equals("AWAITING_DIRECTOR_APPROVAL"),"Only a currently verified bill can be approved.");checkStoredInvoice(id,s);next="APPROVED_FOR_PAYMENT";approval=decision;}
                case "HOLD" -> {conflict(state.equals("HELD"),"Bill is already held.");next="HELD";held=stage;}
                case "RESUME" -> {conflict(!state.equals("HELD"),"Only held bills can be resumed.");next=stage.equals("VERIFICATION")?"SUBMITTED":"AWAITING_DIRECTOR_APPROVAL";}
                case "RETURN" -> {next="RETURNED";verification=null;}
                case "REJECT" -> next="REJECTED";
                default -> throw new IllegalArgumentException("Action does not apply to this review stage.");
            }
        }
        db.update("insert into casino.accounts_decisions values(:id,:bill,:revision,:action,:stage,:actor,:now,:reason,:digest,:verification)",args("id",decision,"bill",id,"revision",number(b,"revision"),"action",action,"stage",stage,"actor",a.id(),"now",dates.currentCasinoDateTime(),"reason",reason,"digest",hash(s.evidenceIds()),"verification",action.equals("APPROVE")?b.get("verification_id"):null));
        // Returning creates a new unverified revision even before a content edit, allowing unchanged resubmission without reusing a verification.
        long revision=number(b,"revision");if(action.equals("RETURN")){revision++;revision(id,(int)revision,s,a);}
        long v=number(b,"version")+1;
        db.update("update casino.accounts_bills set status=:status,held_stage=:held,ever_submitted=:submitted,ever_verified=:verified,verification_id=:verification,approval_id=:approval,version=:v,revision=:revision where id=:id",args("id",id,"status",next,"held",held,"submitted",submitted,"verified",verified,"verification",verification,"approval",approval,"v",v,"revision",revision));
        return complete(r.idempotencyKey(),a,fp,id,v,action,date(b));
    }
}
