package com.casino.casinoerp.repository;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import com.casino.casinoerp.exception.ResourceNotFoundException;
import java.util.*;

@Repository
public class AccountsRepository {
    private final NamedParameterJdbcTemplate jdbc;
    public AccountsRepository(NamedParameterJdbcTemplate jdbc) { this.jdbc=jdbc; }
    public List<Map<String,Object>> rows(String sql,Map<String,?> args) { var rows=jdbc.queryForList(sql,args);
        // JDBC Date serialization would discard PostgreSQL timestamp microseconds.
        rows.forEach(row->row.replaceAll((key,value)->value instanceof java.sql.Timestamp t?t.toLocalDateTime():value instanceof java.sql.Date d?d.toLocalDate():value));
        return rows; }
    public Map<String,Object> row(String sql,Map<String,?> args) { var r=rows(sql,args);return r.isEmpty()?null:r.getFirst(); }
    public Map<String,Object> required(String sql,Map<String,?> args) {
        var r=row(sql,args);if(r==null)throw new ResourceNotFoundException("Accounts record not found.");return r;
    }
    public int update(String sql,Map<String,?> args) {return jdbc.update(sql,args);}
    public void retryLock(String key) { rows("select pg_advisory_xact_lock(hashtextextended(:key,37001))",Map.of("key",key)); }
    public Map<String,Object> bill(UUID id,boolean lock) {
        return required("select * from casino.accounts_bills where id=:id"+(lock?" for update":""),Map.of("id",id));
    }
    public static Map<String,Object> args(Object... values) {
        Map<String,Object> m=new HashMap<>();for(int n=0;n<values.length;n+=2)m.put((String)values[n],values[n+1]);return m;
    }
}
