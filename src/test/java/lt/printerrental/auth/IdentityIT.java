package lt.printerrental.auth;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;

@SpringBootTest(
    properties = {
      "NEON_AUTH_BASE_URL=https://auth.test/auth",
      "NEON_AUTH_JWKS_URL=https://auth.test/jwks"
    })
@AutoConfigureMockMvc
@Testcontainers
class IdentityIT {
  @Container
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

  @DynamicPropertySource
  static void db(DynamicPropertyRegistry p) {
    p.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    p.add("spring.datasource.username", POSTGRES::getUsername);
    p.add("spring.datasource.password", POSTGRES::getPassword);
  }

  @MockitoBean org.springframework.security.oauth2.jwt.JwtDecoder decoder;
  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;

  @Test
  void onlyAdminChangesRolesAndCannotDemoteLastAdmin() throws Exception {
    jdbc.update(
        "INSERT INTO identity.app_users(subject,email,display_name,role) VALUES"
            + " ('admin','admin@test','Admin','ADMIN'),('employee','employee@test','Employee','EMPLOYEE')");
    mvc.perform(
            patch("/api/admin/users/employee/role")
                .with(user("employee").roles("EMPLOYEE"))
                .contentType("application/json")
                .content("{\"role\":\"ADMIN\"}"))
        .andExpect(status().isForbidden());
    mvc.perform(
            patch("/api/admin/users/admin/role")
                .with(user("admin").roles("ADMIN"))
                .contentType("application/json")
                .content("{\"role\":\"CUSTOMER\"}"))
        .andExpect(status().isConflict());
    mvc.perform(
            patch("/api/admin/users/employee/role")
                .with(user("admin").roles("ADMIN"))
                .contentType("application/json")
                .content("{\"role\":\"ADMIN\"}"))
        .andExpect(status().isOk());
  }
}
