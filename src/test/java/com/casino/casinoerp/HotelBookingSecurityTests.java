package com.casino.casinoerp;

import com.casino.casinoerp.config.*;
import com.casino.casinoerp.controller.HotelBookingController;
import com.casino.casinoerp.service.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(HotelBookingController.class) @Import({SecurityConfig.class, JwtAuthenticationFilter.class})
class HotelBookingSecurityTests {
    private static final String REQUEST="{\"customerId\":\"00000000-0000-0000-0000-000000000001\",\"hotelName\":\"Hotel\",\"roomType\":\"Deluxe\",\"checkInDate\":\"2026-08-08\",\"checkOutDate\":\"2026-08-09\",\"numberOfGuests\":1,\"estimatedCost\":0,\"billingType\":\"CASINO_COMPLIMENTARY\",\"idempotencyKey\":\"key\"}";
    @Autowired MockMvc mvc; @MockitoBean HotelBookingService service; @MockitoBean JwtService jwt;
    @ParameterizedTest @ValueSource(strings={"DIRECTOR","SUPER_ADMIN"}) void managementAllowed(String role)throws Exception{mvc.perform(post("/api/hotel-bookings").with(user(role).roles(role)).contentType("application/json").content(REQUEST)).andExpect(status().isCreated());mvc.perform(get("/api/hotel-bookings/current").with(user(role).roles(role))).andExpect(status().isOk());}
    @ParameterizedTest @ValueSource(strings={"CASHIER","RECEPTIONIST","PIT_SUPERVISOR","DEALER"}) void operationalRolesDenied(String role)throws Exception{mvc.perform(get("/api/hotel-bookings/current").with(user(role).roles(role))).andExpect(status().isForbidden());mvc.perform(post("/api/hotel-bookings").with(user(role).roles(role)).contentType("application/json").content(REQUEST)).andExpect(status().isForbidden());}
    @Test void invalidGuestCountAndNegativeCostRejected()throws Exception{mvc.perform(post("/api/hotel-bookings").with(user("director").roles("DIRECTOR")).contentType("application/json").content(REQUEST.replace("\"numberOfGuests\":1","\"numberOfGuests\":0"))).andExpect(status().isBadRequest());mvc.perform(post("/api/hotel-bookings").with(user("director").roles("DIRECTOR")).contentType("application/json").content(REQUEST.replace("\"estimatedCost\":0","\"estimatedCost\":-1"))).andExpect(status().isBadRequest());}
}
