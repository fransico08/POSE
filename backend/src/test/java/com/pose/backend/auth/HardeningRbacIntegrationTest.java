package com.pose.backend.auth;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class HardeningRbacIntegrationTest extends FoundationTestSupport {
    @Test void selfAndOrganizationRemainAvailableWithoutUserReadPermission() throws Exception {
        jdbc.update("DELETE FROM role_permissions WHERE role_id=(SELECT id FROM roles WHERE organization_id=? AND code='MANAGER') AND permission_id=(SELECT id FROM permissions WHERE code='identity.user.read')",ORG_ONE);
        JsonNode actor=loginOne();String token=bearer(actor);
        mvc.perform(get("/api/v1/users/"+USER_ONE).header("Authorization",token)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/organization").header("Authorization",token)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/roles").header("Authorization",token)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/users/"+USER_THREE).header("Authorization",token)).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN_SCOPE"));
        mvc.perform(get("/api/v1/users/"+USER_TWO).header("Authorization",token)).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN_SCOPE"));
        mvc.perform(get("/api/v1/users").header("Authorization",token)).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN_PERMISSION"));
    }
    @Test void managerRoleWithoutModulePermissionsCannotUseModuleEndpoints() throws Exception {
        jdbc.update("DELETE FROM role_permissions WHERE permission_id=(SELECT id FROM permissions WHERE code='customer.read') AND role_id IN (SELECT id FROM roles WHERE organization_id=?)",ORG_ONE);
        JsonNode actor=loginOne();String token=bearer(actor);
        for(String path:new String[]{"/api/v1/customers?ownerUserId="+USER_ONE,"/api/v1/leads?ownerUserId="+USER_ONE,
                "/api/v1/opportunities/owners","/api/v1/audit","/api/v1/offerings/categories"}) {
            mvc.perform(get(path).header("Authorization",token)).andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("FORBIDDEN_PERMISSION"));
        }
    }
}
