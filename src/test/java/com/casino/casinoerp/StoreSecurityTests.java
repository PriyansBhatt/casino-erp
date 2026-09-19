package com.casino.casinoerp;

import com.casino.casinoerp.config.*;
import com.casino.casinoerp.controller.StoreController;
import com.casino.casinoerp.service.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.util.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(StoreController.class) @Import({SecurityConfig.class,JwtAuthenticationFilter.class})
class StoreSecurityTests {
    @Autowired MockMvc mvc; @MockitoBean StoreService service; @MockitoBean JwtService jwt;
    static final String ID="00000000-0000-0000-0000-000000000001";
    static final String QUANTITY="{\"quantity\":2,\"idempotencyKey\":\"key\"}";
    @ParameterizedTest @ValueSource(strings={"SUPER_ADMIN","DIRECTOR","ADMIN","AUDITOR","STORE_KEEPER","MANAGER","RECEPTIONIST","CASHIER","DEALER","PIT_SUPERVISOR","COMPLIANCE_OFFICER","SURVEILLANCE_OFFICER"})
    void allEndpointFamilies(String role)throws Exception {
        for(String path:List.of("items","staff","requests","requests/"+ID,"procurements","movements"))mvc.perform(get("/api/store/"+path).with(user(role).roles(role))).andExpect(role.equals("SUPER_ADMIN")||role.equals("DIRECTOR")?status().isOk():status().isForbidden());
        var writes=new LinkedHashMap<String,String>();
        writes.put("items","{\"code\":\"A\",\"name\":\"Item\",\"category\":\"Goods\",\"unit\":\"PCS\",\"active\":true}");
        writes.put("requests","{\"staffProfileId\":\""+ID+"\",\"lines\":[{\"itemId\":\""+ID+"\",\"quantity\":2}],\"idempotencyKey\":\"key\"}");
        for(String path:List.of("items/"+ID+"/opening-stock","request-lines/"+ID+"/issues","request-lines/"+ID+"/procurements","procurements/"+ID+"/receive"))writes.put(path,QUANTITY);
        writes.put("items/"+ID+"/adjustments","{\"type\":\"ADJUSTMENT_IN\",\"quantity\":2,\"reason\":\"count\",\"idempotencyKey\":\"key\"}");
        for(String path:List.of("requests/"+ID+"/cancel-remaining","procurements/"+ID+"/order","procurements/"+ID+"/cancel"))writes.put(path,"{\"expectedVersion\":0,\"reason\":\"cancel\"}");
        for(var entry:writes.entrySet())mvc.perform(post("/api/store/"+entry.getKey()).with(user(role).roles(role)).contentType("application/json").content(entry.getValue())).andExpect(role.equals("SUPER_ADMIN")?status().isOk():status().isForbidden());
        mvc.perform(patch("/api/store/items/"+ID).with(user(role).roles(role)).contentType("application/json").content(writes.get("items"))).andExpect(role.equals("SUPER_ADMIN")?status().isOk():status().isForbidden());
    }
    @ParameterizedTest @ValueSource(strings={"1e0","1.0","\"1\"","\"5\"","1.5","\"2\"","0","-1","null","2147483648","99999999999999999999","true","{}","[]"})
    void strictQuantities(String q)throws Exception {
        for(String path:List.of("items/"+ID+"/opening-stock","request-lines/"+ID+"/issues","request-lines/"+ID+"/procurements","procurements/"+ID+"/receive"))mvc.perform(post("/api/store/"+path).with(user("sa").roles("SUPER_ADMIN")).contentType("application/json").content(QUANTITY.replace(":2",":"+q))).andExpect(status().isBadRequest());
    }
    @ParameterizedTest @ValueSource(strings={"1","2147483647"})
    void integerBoundariesAccepted(String q)throws Exception {
        mvc.perform(post("/api/store/items/"+ID+"/opening-stock").with(user("sa").roles("SUPER_ADMIN")).contentType("application/json").content(QUANTITY.replace(":2",":"+q))).andExpect(status().isOk());
    }
    @Test void malformedAndAnonymous()throws Exception {
        mvc.perform(get("/api/store/items")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/store/requests/not-uuid").with(user("sa").roles("SUPER_ADMIN"))).andExpect(status().isBadRequest());
        mvc.perform(post("/api/store/items/"+ID+"/opening-stock").with(user("sa").roles("SUPER_ADMIN")).contentType("application/json").content(QUANTITY.replace("\"quantity\"","\"performedBy\":\"spoof\",\"quantity\""))).andExpect(status().isBadRequest());
    }
}
