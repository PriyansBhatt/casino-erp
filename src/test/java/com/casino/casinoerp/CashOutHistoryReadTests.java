package com.casino.casinoerp;

import com.casino.casinoerp.repository.ChipCashOutRepository;
import com.casino.casinoerp.repository.LosingReturnRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;

/** Executes the production history queries with read-only connections. No fixtures, DDL or migrations. */
@DataJpaTest(properties = {"spring.flyway.enabled=false", "spring.jpa.hibernate.ddl-auto=none",
        "spring.datasource.hikari.read-only=true", "spring.sql.init.mode=never"})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(readOnly = true)
class CashOutHistoryReadTests {
    @Autowired ChipCashOutRepository cashOuts;
    @Autowired LosingReturnRepository returns;

    @Test void absentCustomerAndSessionHaveEmptyPersistedHistory() {
        UUID absent = UUID.randomUUID();
        assertThat(cashOuts.findHistoryBySession(absent)).isEmpty();
        assertThat(returns.findHistory(absent, LocalDate.of(2026, 9, 2))).isEmpty();
    }
}
