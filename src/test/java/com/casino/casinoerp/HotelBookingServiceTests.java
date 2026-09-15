package com.casino.casinoerp;

import com.casino.casinoerp.dto.*;
import com.casino.casinoerp.entity.*;
import com.casino.casinoerp.repository.*;
import com.casino.casinoerp.security.Role;
import com.casino.casinoerp.service.*;
import org.junit.jupiter.api.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class HotelBookingServiceTests {
    private final HotelBookingRepository bookings=mock(HotelBookingRepository.class);
    private final CustomerRepository customers=mock(CustomerRepository.class);
    private final CustomerSessionRepository sessions=mock(CustomerSessionRepository.class);
    private final BusinessDateService dates=mock(BusinessDateService.class);
    private final SystemLockService lock=mock(SystemLockService.class);
    private final AuthenticatedUserService auth=mock(AuthenticatedUserService.class);
    private final CurrentUserRoleService roles=mock(CurrentUserRoleService.class);
    private final UserRepository users=mock(UserRepository.class);
    private final AuditLogService audit=mock(AuditLogService.class);
    private final HotelBookingService service=new HotelBookingService(bookings,customers,sessions,dates,lock,auth,roles,new RolePermissionService(),users,audit,mock(org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate.class));
    private final UUID customerId=UUID.randomUUID(),sessionId=UUID.randomUUID(),actorId=UUID.randomUUID();
    private final LocalDate businessDate=LocalDate.of(2026,8,8);

    @BeforeEach void setup(){when(roles.getCurrentRole()).thenReturn(Optional.of(Role.SUPER_ADMIN));when(dates.getCurrentBusinessDate()).thenReturn(businessDate);when(customers.findById(customerId)).thenReturn(Optional.of(customer()));when(sessions.findById(sessionId)).thenReturn(Optional.of(session(customerId)));when(auth.getRequiredUser()).thenReturn(actor());when(bookings.save(any())).thenAnswer(i->{HotelBooking b=i.getArgument(0);if(b.getId()==null)b.setId(UUID.randomUUID());return b;});when(users.findAllById(any())).thenReturn(List.of(actor()));}

    @Test void successfulCreationUsesBackendDateAndRequestedStatus(){var result=service.create(request());assertThat(result.businessDate()).isEqualTo(businessDate);assertThat(result.status()).isEqualTo(HotelBookingStatus.REQUESTED);assertThat(result.bookingCode()).startsWith("HB-20260808-");verify(dates).validateNewOperationalMutationAllowed();verify(audit).log(eq("CREATE_HOTEL_BOOKING"),eq("HOTEL_BOOKING"),any(),eq(actorId),contains("REQUESTED"));}
    @Test void invalidCustomerRejected(){when(customers.findById(customerId)).thenReturn(Optional.empty());assertThatThrownBy(()->service.create(request())).hasMessage("Customer not found.");}
    @Test void mismatchedSessionRejected(){when(sessions.findById(sessionId)).thenReturn(Optional.of(session(UUID.randomUUID())));assertThatThrownBy(()->service.create(request())).hasMessageContaining("does not belong");}
    @Test void invalidDateRangeRejected(){CreateHotelBookingRequest r=new CreateHotelBookingRequest(customerId,sessionId,"Hotel","Room",LocalDate.of(2026,8,10),LocalDate.of(2026,8,9),1,BigDecimal.ZERO,HotelBillingType.CASINO_COMPLIMENTARY,null,"key");assertThatThrownBy(()->service.create(r)).hasMessageContaining("cannot be before");}
    @Test void unauthorizedRoleDenied(){when(roles.getCurrentRole()).thenReturn(Optional.of(Role.CASHIER));assertThatThrownBy(()->service.create(request())).hasMessageContaining("SUPER_ADMIN");}
    @Test void requestedBookingCanBeApproved(){HotelBooking value=existing(HotelBookingStatus.REQUESTED);when(bookings.lockById(value.getId())).thenReturn(Optional.of(value));assertThat(service.approve(value.getId()).status()).isEqualTo(HotelBookingStatus.APPROVED);assertThat(value.getApprovedBy()).isEqualTo(actorId);verify(audit).log(eq("APPROVE_HOTEL_BOOKING"),eq("HOTEL_BOOKING"),eq(value.getId()),eq(actorId),contains("APPROVED"));}
    @Test void requestedBookingCanBeRejected(){HotelBooking value=existing(HotelBookingStatus.REQUESTED);when(bookings.lockById(value.getId())).thenReturn(Optional.of(value));assertThat(service.reject(value.getId()).status()).isEqualTo(HotelBookingStatus.REJECTED);}
    @Test void currentListUsesOpenBusinessDateAndRepositoryReload(){BusinessDate open=new BusinessDate();open.setBusinessDate(businessDate);when(dates.getCurrentOpenBusinessDate()).thenReturn(Optional.of(open));when(bookings.findByBusinessDateOrderByCreatedAtDescIdDesc(businessDate)).thenReturn(List.of());when(customers.findAllById(any())).thenReturn(List.of());when(sessions.findAllById(any())).thenReturn(List.of());assertThat(service.current()).isEmpty();verify(bookings).findByBusinessDateOrderByCreatedAtDescIdDesc(businessDate);}

    @Test void directorCannotCreateOrChangeHotel(){when(roles.getCurrentRole()).thenReturn(Optional.of(Role.DIRECTOR));assertThatThrownBy(()->service.create(request())).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);assertThatThrownBy(()->service.approve(UUID.randomUUID())).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);}
    @Test void exitedSessionCannotBeLinked(){CustomerSession s=session(customerId);s.setExitTime(java.time.LocalDateTime.now());when(sessions.findById(sessionId)).thenReturn(Optional.of(s));assertThatThrownBy(()->service.create(request())).hasMessageContaining("OPEN");}
    @Test void optionalSessionRemainsOptional(){var r=request();service.create(new CreateHotelBookingRequest(customerId,null,r.hotelName(),r.roomType(),r.checkInDate(),r.checkOutDate(),r.numberOfGuests(),r.estimatedCost(),r.billingType(),r.remarks(),r.idempotencyKey()));verify(sessions,never()).findById(any());}
    @Test void repeatedApprovalIsIdempotent(){HotelBooking value=existing(HotelBookingStatus.APPROVED);when(bookings.lockById(value.getId())).thenReturn(Optional.of(value));assertThat(service.approve(value.getId()).status()).isEqualTo(HotelBookingStatus.APPROVED);verify(bookings,never()).save(any());}
    @Test void retryCannotReturnAnotherActorsBooking(){var value=existing(HotelBookingStatus.REQUESTED);value.setCreatedBy(UUID.randomUUID());when(bookings.findByIdempotencyKey("hotel-key")).thenReturn(Optional.of(value));assertThatThrownBy(()->service.create(request())).hasMessageContaining("another recording user");verify(bookings,never()).save(any());}
    @Test void retryMatchesRemarksAndPreservesHistoricalDate(){var r=request();var value=existing(HotelBookingStatus.REQUESTED);value.setHotelName(r.hotelName());value.setRoomType(r.roomType());value.setNumberOfGuests(r.numberOfGuests());value.setEstimatedCost(r.estimatedCost());value.setRemarks(r.remarks());value.setBusinessDate(businessDate.minusDays(1));when(bookings.findByIdempotencyKey("hotel-key")).thenReturn(Optional.of(value));assertThat(service.create(r).businessDate()).isEqualTo(businessDate.minusDays(1));verify(dates,never()).validateNewOperationalMutationAllowed();value.setRemarks("Different");assertThatThrownBy(()->service.create(r)).hasMessageContaining("different Hotel Booking");}
    private CreateHotelBookingRequest request(){return new CreateHotelBookingRequest(customerId,sessionId,"Summit Hotel","Deluxe",businessDate,businessDate.plusDays(1),2,new BigDecimal("5000"),HotelBillingType.CASINO_COMPLIMENTARY,"Test","hotel-key");}
    private Customer customer(){Customer c=new Customer();c.setId(customerId);c.setCustomerCode("CUS-1001");c.setFullName("Test Customer");c.setStatus(CustomerStatus.ACTIVE);return c;}
    private CustomerSession session(UUID owner){CustomerSession s=new CustomerSession();s.setId(sessionId);s.setCustomerId(owner);s.setSessionCode("SES-1");s.setStatus("OPEN");s.setBusinessDate(businessDate);return s;}
    private User actor(){User u=new User();u.setId(actorId);u.setUsername("director");return u;}
    private HotelBooking existing(HotelBookingStatus status){HotelBooking b=new HotelBooking();b.setId(UUID.randomUUID());b.setBookingCode("HB-1");b.setCustomerId(customerId);b.setCustomerSessionId(sessionId);b.setBusinessDate(businessDate);b.setHotelName("Hotel");b.setRoomType("Room");b.setCheckInDate(businessDate);b.setCheckOutDate(businessDate.plusDays(1));b.setNumberOfGuests(1);b.setEstimatedCost(BigDecimal.TEN);b.setBillingType(HotelBillingType.CASINO_COMPLIMENTARY);b.setStatus(status);b.setCreatedBy(actorId);b.setCreatedAt(java.time.LocalDateTime.now());b.setUpdatedAt(java.time.LocalDateTime.now());return b;}
}
