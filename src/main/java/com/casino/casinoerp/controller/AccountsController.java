package com.casino.casinoerp.controller;

import com.casino.casinoerp.dto.ApiResponse;
import com.casino.casinoerp.dto.AccountsDtos.*;
import com.casino.casinoerp.service.AccountsService;
import com.casino.casinoerp.service.AccountsEvidenceStorage;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.*;

@RestController
@RequestMapping("/api/accounts")
public class AccountsController {
    private final AccountsService service;
    public AccountsController(AccountsService service) {this.service=service;}
    private <T>ApiResponse<T> ok(T value) {return ApiResponse.success("Accounts",value);}
    @GetMapping("/context") public ApiResponse<?> context(){return ok(service.context());}
    @GetMapping("/parties") public ApiResponse<?> parties(@RequestParam(defaultValue="")String q,@RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="50")int size){return ok(service.parties(q,page,size));}
    @PostMapping("/parties") public ApiResponse<?> party(@RequestBody PartyWrite request){return ok(service.party(request));}
    @GetMapping("/sources") public ApiResponse<?> sources(@RequestParam String type,@RequestParam(defaultValue="")String q,@RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="50")int size){return ok(service.sources(type,q,page,size));}
    @GetMapping("/bills") public ApiResponse<?> bills(@RequestParam(defaultValue="")String q,@RequestParam(defaultValue="")String status,@RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="50")int size){return ok(service.bills(q,status,page,size));}
    @GetMapping("/bills/{id}") public ApiResponse<?> detail(@PathVariable UUID id){return ok(service.detail(id));}
    @GetMapping("/bills/{id}/history/{kind}") public ApiResponse<?> history(@PathVariable UUID id,@PathVariable String kind,@RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="50")int size){return ok(service.history(id,kind,page,size));}
    @PostMapping("/bills") public ApiResponse<?> create(@RequestBody BillWrite request){return ok(service.write(null,request));}
    @PostMapping("/bills/{id}/corrections") public ApiResponse<?> correct(@PathVariable UUID id,@RequestBody BillWrite request){return ok(service.write(id,request));}
    @PostMapping("/bills/{id}/submit") public ApiResponse<?> submit(@PathVariable UUID id,@RequestBody Action request){return ok(service.action(id,"SUBMIT",request));}
    @PostMapping("/bills/{id}/verify") public ApiResponse<?> verify(@PathVariable UUID id,@RequestBody Action request){return ok(service.action(id,"VERIFY",request));}
    @PostMapping("/bills/{id}/approve") public ApiResponse<?> approve(@PathVariable UUID id,@RequestBody Action request){return ok(service.action(id,"APPROVE",request));}
    @PostMapping("/bills/{id}/hold") public ApiResponse<?> hold(@PathVariable UUID id,@RequestBody Action request){return ok(service.action(id,"HOLD",request));}
    @PostMapping("/bills/{id}/resume") public ApiResponse<?> resume(@PathVariable UUID id,@RequestBody Action request){return ok(service.action(id,"RESUME",request));}
    @PostMapping("/bills/{id}/return") public ApiResponse<?> returnBill(@PathVariable UUID id,@RequestBody Action request){return ok(service.action(id,"RETURN",request));}
    @PostMapping("/bills/{id}/reject") public ApiResponse<?> reject(@PathVariable UUID id,@RequestBody Action request){return ok(service.action(id,"REJECT",request));}
    @PostMapping(value="/bills/{id}/evidence",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<?> upload(@PathVariable UUID id,@RequestParam String idempotencyKey,@RequestParam Long expectedVersion,
            @RequestParam boolean invoiceDocument,@RequestParam(required=false)UUID replacesId,@RequestPart MultipartFile file)throws java.io.IOException {
        if(file.getSize()>AccountsEvidenceStorage.MAX_BYTES)throw new IllegalArgumentException("Maximum evidence size is 5 MiB (5,242,880 bytes).");
        return ok(service.upload(id,new Action(idempotencyKey,expectedVersion,null),file.getOriginalFilename(),file.getContentType(),invoiceDocument,replacesId,file.getBytes()));
    }
    @GetMapping("/bills/{id}/evidence/{evidenceId}") public ResponseEntity<byte[]> document(@PathVariable UUID id,@PathVariable UUID evidenceId) {
        var d=service.document(id,evidenceId);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(d.type())).cacheControl(CacheControl.noStore())
            .header("X-Content-Type-Options","nosniff").header("Content-Security-Policy","default-src 'none'; sandbox")
            .header(HttpHeaders.CONTENT_DISPOSITION,ContentDisposition.attachment().filename(d.name(),java.nio.charset.StandardCharsets.UTF_8).build().toString()).body(d.bytes());
    }
}
