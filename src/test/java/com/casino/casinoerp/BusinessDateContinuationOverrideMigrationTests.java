package com.casino.casinoerp;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class BusinessDateContinuationOverrideMigrationTests {
    @Autowired JdbcTemplate jdbc;

    @Test
    void v25CreatesPersistentOverrideTableAndUnrevokedUniqueness() {
        Integer tableCount = jdbc.queryForObject("""
                select count(*) from information_schema.tables
                where table_schema = 'casino' and table_name = 'business_date_continuation_overrides'
                """, Integer.class);
        String indexDefinition = jdbc.queryForObject("""
                select indexdef from pg_indexes
                where schemaname = 'casino'
                  and indexname = 'uq_business_date_continuation_override_unrevoked'
                """, String.class);

        assertThat(tableCount).isEqualTo(1);
        assertThat(indexDefinition).contains("UNIQUE").contains("revoked_at IS NULL");
    }
}
