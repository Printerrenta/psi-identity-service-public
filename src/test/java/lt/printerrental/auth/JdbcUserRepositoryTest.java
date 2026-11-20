package lt.printerrental.auth;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class JdbcUserRepositoryTest {
  @Test
  void rejectsInvalidSchemaName() {
    var db = mock(JdbcTemplate.class);
    assertThatThrownBy(() -> new JdbcUserRepository(db, "identity; DROP TABLE app_users"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new JdbcUserRepository(db, "Identity"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new JdbcUserRepository(db, null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void usesTransactionAdvisoryLockAndParameterizedSchema() {
    var db = mock(JdbcTemplate.class);
    var repo = new JdbcUserRepository(db, "custom_schema");

    repo.lockRoleChanges();
    verify(db).execute("SELECT pg_advisory_xact_lock(7341928374619283741)");

    repo.updateRole("alice", UserRole.ADMIN);
    verify(db)
        .update("UPDATE custom_schema.app_users SET role=? WHERE subject=?", "ADMIN", "alice");
  }
}
