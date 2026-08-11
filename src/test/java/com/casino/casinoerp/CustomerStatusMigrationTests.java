package com.casino.casinoerp;

import com.casino.casinoerp.entity.Customer;
import com.casino.casinoerp.entity.CustomerStatus;
import com.casino.casinoerp.repository.CustomerRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class CustomerStatusMigrationTests {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private MockMvc mockMvc;

    @Test
    void customerStatusesAreCanonicalAndLoadThroughEnumMapping() {
        Long nonCanonicalCount = jdbcTemplate.queryForObject("""
                select count(*)
                from customer.customers
                where status is null
                   or status not in ('ACTIVE', 'INACTIVE', 'BLOCKED')
                """, Long.class);
        Boolean constraintValidated = jdbcTemplate.queryForObject("""
                select convalidated
                from pg_constraint
                where conrelid = 'customer.customers'::regclass
                  and conname = 'chk_customers_status'
                """, Boolean.class);
        List<Customer> customers = customerRepository.findAll();

        assertThat(nonCanonicalCount).isZero();
        assertThat(constraintValidated).isTrue();
        assertThat(customers).isNotEmpty();
        assertThat(customers)
                .extracting(Customer::getStatus)
                .allMatch(status -> status == CustomerStatus.ACTIVE
                        || status == CustomerStatus.INACTIVE
                        || status == CustomerStatus.BLOCKED);
    }

    @Test
    void customerDirectoryLoadsThroughHttpWithoutEnumConversionFailure() throws Exception {
        mockMvc.perform(get("/api/customers")
                        .with(user("superadmin").roles("SUPER_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("CUS-1001")))
                .andExpect(content().string(containsString("CUS-1002")))
                .andExpect(content().string(containsString("CUS-1003")));
    }
}
