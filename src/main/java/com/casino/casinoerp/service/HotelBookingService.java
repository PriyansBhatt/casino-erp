package com.casino.casinoerp.service;

import com.casino.casinoerp.dto.*;
import com.casino.casinoerp.entity.*;
import com.casino.casinoerp.exception.*;
import com.casino.casinoerp.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class HotelBookingService {
    private final HotelBookingRepository bookings; private final CustomerRepository customers;
    private final CustomerSessionRepository sessions; private final BusinessDateService dates;
    private final SystemLockService lock; private final AuthenticatedUserService authenticatedUsers;
    private final CurrentUserRoleService roles; private final RolePermissionService permissions;
    private final UserRepository users; private final AuditLogService audit;

    public HotelBookingService(HotelBookingRepository bookings, CustomerRepository customers,
            CustomerSessionRepository sessions, BusinessDateService dates, SystemLockService lock,
            AuthenticatedUserService authenticatedUsers, CurrentUserRoleService roles,
            RolePermissionService permissions, UserRepository users, AuditLogService audit) {
        this.bookings=bookings; this.customers=customers; this.sessions=sessions; this.dates=dates;
        this.lock=lock; this.authenticatedUsers=authenticatedUsers; this.roles=roles;
        this.permissions=permissions; this.users=users; this.audit=audit;
    }

    @Transactional
    public HotelBookingResponse create(CreateHotelBookingRequest request) {
        validateRole(); validateDates(request.checkInDate(), request.checkOutDate());
        HotelBooking replay = bookings.findByIdempotencyKey(request.idempotencyKey().trim()).orElse(null);
        if (replay != null) { validateReplay(replay, request); return response(replay); }
        dates.validateBusinessDateIsOpen(); LocalDate businessDate = dates.getCurrentBusinessDate(); validateUnlocked();
        Customer customer = customer(request.customerId());
        CustomerSession session = validateSession(request.customerSessionId(), customer.getId(), businessDate);
        User actor = authenticatedUsers.getRequiredUser(); LocalDateTime now = LocalDateTime.now();
        HotelBooking value = new HotelBooking();
        value.setBookingCode("HB-" + businessDate.toString().replace("-", "") + "-" + UUID.randomUUID());
        value.setCustomerId(customer.getId()); value.setCustomerSessionId(session == null ? null : session.getId());
        value.setBusinessDate(businessDate); value.setHotelName(request.hotelName().trim());
        value.setRoomType(request.roomType().trim()); value.setCheckInDate(request.checkInDate());
        value.setCheckOutDate(request.checkOutDate()); value.setNumberOfGuests(request.numberOfGuests());
        value.setEstimatedCost(request.estimatedCost()); value.setBillingType(request.billingType());
        value.setStatus(HotelBookingStatus.REQUESTED); value.setRemarks(normalize(request.remarks()));
        value.setCreatedAt(now); value.setUpdatedAt(now); value.setCreatedBy(actor.getId());
        value.setIdempotencyKey(request.idempotencyKey().trim()); HotelBooking saved = bookings.save(value);
        log("CREATE_HOTEL_BOOKING", saved, actor, "status=REQUESTED"); return response(saved, customer, session, Map.of(actor.getId(), actor));
    }

    @Transactional(readOnly=true)
    public List<HotelBookingResponse> current() {
        validateRole(); LocalDate date = dates.getCurrentOpenBusinessDate()
                .orElseThrow(() -> new IllegalStateException("Current business date is not opened."))
                .getBusinessDate();
        return responses(bookings.findByBusinessDateOrderByCreatedAtDesc(date));
    }

    @Transactional(readOnly=true)
    public HotelBookingResponse get(UUID id) { validateRole(); return response(required(id)); }

    @Transactional
    public HotelBookingResponse approve(UUID id) { return decide(id, true); }

    @Transactional
    public HotelBookingResponse reject(UUID id) { return decide(id, false); }

    @Transactional
    public HotelBookingResponse updateStatus(UUID id, UpdateHotelBookingStatusRequest request) {
        validateRole(); validateUnlocked(); HotelBooking value = required(id);
        HotelBookingStatus next = request.status();
        boolean valid = (value.getStatus()==HotelBookingStatus.APPROVED && next==HotelBookingStatus.BOOKED)
                || (value.getStatus()==HotelBookingStatus.BOOKED && next==HotelBookingStatus.CHECKED_IN)
                || (value.getStatus()==HotelBookingStatus.CHECKED_IN && next==HotelBookingStatus.COMPLETED)
                || (!List.of(HotelBookingStatus.COMPLETED, HotelBookingStatus.REJECTED, HotelBookingStatus.CANCELLED)
                    .contains(value.getStatus()) && next==HotelBookingStatus.CANCELLED);
        if (!valid) throw new ResourceConflictException("Invalid hotel booking status transition.");
        if (request.actualCost()!=null && request.actualCost().signum()<0) throw new IllegalArgumentException("Actual cost cannot be negative.");
        if (next==HotelBookingStatus.COMPLETED && request.actualCost()==null) throw new IllegalArgumentException("Actual cost is required when completing a booking.");
        value.setStatus(next); if (request.actualCost()!=null) value.setActualCost(request.actualCost()); value.setUpdatedAt(LocalDateTime.now());
        HotelBooking saved=bookings.save(value); User actor=authenticatedUsers.getRequiredUser();
        log("UPDATE_HOTEL_BOOKING_STATUS", saved, actor, "status="+next); return response(saved);
    }

    private HotelBookingResponse decide(UUID id, boolean approved) {
        validateRole(); validateUnlocked(); HotelBooking value=required(id);
        if (value.getStatus()!=HotelBookingStatus.REQUESTED) throw new ResourceConflictException("Only a requested booking can be approved or rejected.");
        User actor=authenticatedUsers.getRequiredUser(); LocalDateTime now=LocalDateTime.now();
        value.setStatus(approved?HotelBookingStatus.APPROVED:HotelBookingStatus.REJECTED);
        value.setApprovedBy(actor.getId()); value.setApprovedAt(now); value.setUpdatedAt(now);
        HotelBooking saved=bookings.save(value); log(approved?"APPROVE_HOTEL_BOOKING":"REJECT_HOTEL_BOOKING", saved, actor, "status="+saved.getStatus());
        return response(saved);
    }
    private void validateRole(){ if(!roles.getCurrentRole().map(permissions::canManageHotelBooking).orElse(false)) throw new RuntimeException("Access denied. Hotel Booking is restricted to Director or Super Admin."); }
    private void validateUnlocked(){ if(lock.isSystemLocked()) throw new ResourceConflictException("System is locked. Hotel Booking changes are not allowed."); }
    private Customer customer(UUID id){ Customer value=customers.findById(id).orElseThrow(()->new ResourceNotFoundException("Customer not found.")); if(value.getStatus()!=CustomerStatus.ACTIVE) throw new IllegalArgumentException("Customer must be ACTIVE."); return value; }
    private CustomerSession validateSession(UUID id, UUID customerId, LocalDate date){ if(id==null)return null; CustomerSession value=sessions.findById(id).orElseThrow(()->new ResourceNotFoundException("Customer session not found.")); if(!customerId.equals(value.getCustomerId()))throw new IllegalArgumentException("Customer session does not belong to the supplied customer."); if(!"OPEN".equalsIgnoreCase(value.getStatus())||!date.equals(value.getBusinessDate()))throw new IllegalArgumentException("Customer session must be OPEN for the current Business Date."); return value; }
    private void validateDates(LocalDate in, LocalDate out){ if(in!=null&&out!=null&&out.isBefore(in))throw new IllegalArgumentException("Check-out date cannot be before check-in date."); }
    private HotelBooking required(UUID id){ return bookings.findById(id).orElseThrow(()->new ResourceNotFoundException("Hotel booking not found.")); }
    private String normalize(String value){return value==null||value.isBlank()?null:value.trim();}
    private void validateReplay(HotelBooking v, CreateHotelBookingRequest r){ if(!v.getCustomerId().equals(r.customerId())||!Objects.equals(v.getCustomerSessionId(),r.customerSessionId())||!v.getHotelName().equals(r.hotelName().trim())||!v.getRoomType().equals(r.roomType().trim())||!v.getCheckInDate().equals(r.checkInDate())||!v.getCheckOutDate().equals(r.checkOutDate())||!v.getNumberOfGuests().equals(r.numberOfGuests())||v.getEstimatedCost().compareTo(r.estimatedCost())!=0||v.getBillingType()!=r.billingType())throw new ResourceConflictException("Idempotency key has already been used for a different Hotel Booking.");}
    private void log(String action,HotelBooking value,User actor,String detail){audit.log(action,"HOTEL_BOOKING",value.getId(),actor.getId(),"Booking "+value.getBookingCode()+", businessDate="+value.getBusinessDate()+", "+detail);}
    private List<HotelBookingResponse> responses(List<HotelBooking> values){Set<UUID> customerIds=values.stream().map(HotelBooking::getCustomerId).collect(Collectors.toSet()); Set<UUID> sessionIds=values.stream().map(HotelBooking::getCustomerSessionId).filter(Objects::nonNull).collect(Collectors.toSet()); Set<UUID> actorIds=values.stream().flatMap(v->java.util.stream.Stream.of(v.getCreatedBy(),v.getApprovedBy())).filter(Objects::nonNull).collect(Collectors.toSet()); Map<UUID,Customer> customerMap=customers.findAllById(customerIds).stream().collect(Collectors.toMap(Customer::getId,Function.identity())); Map<UUID,CustomerSession> sessionMap=sessions.findAllById(sessionIds).stream().collect(Collectors.toMap(CustomerSession::getId,Function.identity())); Map<UUID,User> actorMap=users.findAllById(actorIds).stream().collect(Collectors.toMap(User::getId,Function.identity())); return values.stream().map(v->response(v,customerMap.get(v.getCustomerId()),sessionMap.get(v.getCustomerSessionId()),actorMap)).toList();}
    private HotelBookingResponse response(HotelBooking v){Customer c=customers.findById(v.getCustomerId()).orElse(null);CustomerSession s=v.getCustomerSessionId()==null?null:sessions.findById(v.getCustomerSessionId()).orElse(null);Set<UUID> ids=new HashSet<>();ids.add(v.getCreatedBy());if(v.getApprovedBy()!=null)ids.add(v.getApprovedBy());Map<UUID,User> actors=users.findAllById(ids).stream().collect(Collectors.toMap(User::getId,Function.identity()));return response(v,c,s,actors);}
    private HotelBookingResponse response(HotelBooking v,Customer c,CustomerSession s,Map<UUID,User>a){return new HotelBookingResponse(v.getId(),v.getBookingCode(),v.getCustomerId(),c==null?null:c.getCustomerCode(),c==null?null:c.getFullName(),v.getCustomerSessionId(),s==null?null:s.getSessionCode(),v.getBusinessDate(),v.getHotelName(),v.getRoomType(),v.getCheckInDate(),v.getCheckOutDate(),v.getNumberOfGuests(),v.getEstimatedCost(),v.getActualCost(),v.getBillingType(),v.getStatus(),v.getRemarks(),actor(a.get(v.getCreatedBy())),actor(v.getApprovedBy()==null?null:a.get(v.getApprovedBy())),v.getCreatedAt(),v.getUpdatedAt(),v.getApprovedAt());}
    private ActorReferenceResponse actor(User u){return u==null?null:new ActorReferenceResponse(u.getId(),u.getUsername());}
}
