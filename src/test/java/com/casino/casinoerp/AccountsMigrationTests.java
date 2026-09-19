package com.casino.casinoerp;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

/** Real V36 followed by unchanged V37, isolated schema and synthetic pre-existing users/stock. */
class AccountsMigrationTests {
    @Test void forwardMigrationPreservesV36EraUsersAndStoreData()throws Exception {
        String url=System.getenv("AC1_TEST_JDBC_URL");
        if(!"jdbc:postgresql://127.0.0.1:55437/casino_ac1_test".equals(url))throw new IllegalStateException("Disposable AC1 database required.");
        var jdbc=new JdbcTemplate(new DriverManagerDataSource(url,System.getProperty("user.name"),""));
        String schema="ac1_migration_"+UUID.randomUUID().toString().replace("-","");jdbc.execute("create schema "+schema);
        try {
            // Existing User mapping uses core.users UUID IDs; role is text, not a database enum.
            jdbc.execute("create table "+schema+".users(id uuid primary key,username varchar(255),password_hash varchar(255),full_name varchar(255),email varchar(255),status varchar(255),role varchar(255),created_at timestamp,updated_at timestamp)");
            jdbc.execute("create table "+schema+".departments(id uuid primary key)");jdbc.execute("create table "+schema+".staff_profiles(id uuid primary key)");
            UUID actor=UUID.randomUUID(),item=UUID.randomUUID();
            jdbc.update("insert into "+schema+".users(id,username,status,role) values(?,'existing','ACTIVE','SUPER_ADMIN')",actor);
            apply(jdbc,schema,"V36__add_store_purchase_foundation.sql");
            jdbc.update("insert into "+schema+".store_items(id,code,name,category,unit,quantity_balance,created_at,created_by,updated_at,updated_by) values(?,'OLD','Existing stock','Goods','PCS',7,now(),?,now(),?)",item,actor,actor);
            jdbc.update("insert into "+schema+".store_movements(id,reference,item_id,movement_type,quantity,balance_after,performed_by,performed_at,idempotency_key,request_fingerprint) values(?,'OLD-OPEN',?,'OPENING',7,7,?,now(),'old-operation','old-fingerprint')",UUID.randomUUID(),item,actor);
            var beforeUsers=jdbc.queryForList("select * from "+schema+".users");var beforeStock=jdbc.queryForList("select * from "+schema+".store_items");var beforeMovements=jdbc.queryForList("select * from "+schema+".store_movements");
            apply(jdbc,schema,"V37__add_accounts_bill_workflow.sql");
            assertThat(jdbc.queryForList("select * from "+schema+".users")).isEqualTo(beforeUsers);assertThat(jdbc.queryForList("select * from "+schema+".store_items")).isEqualTo(beforeStock);assertThat(jdbc.queryForList("select * from "+schema+".store_movements")).isEqualTo(beforeMovements);
            assertThat(jdbc.queryForObject("select count(*) from "+schema+".accounts_bills",Long.class)).isZero();
            jdbc.update("insert into "+schema+".accounts_parties values(?,'EXISTING','Existing supplier','SUPPLIER',?,now())",UUID.randomUUID(),actor);
            assertThatThrownBy(()->jdbc.update("delete from "+schema+".users where id=?",actor)).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        } finally {jdbc.execute("drop schema "+schema+" cascade");}
    }
    private void apply(JdbcTemplate jdbc,String schema,String resource)throws Exception {
        try(var in=getClass().getResourceAsStream("/db/migration/"+resource)) {
            jdbc.execute(new String(in.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8).replace("casino.",schema+".").replace("core.",schema+"."));
        }
    }
}
