package lt.printerrental.auth;

import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcUserRepository implements UserRepository {
  private static final RowMapper<AppUser> USER =
      (rs, row) ->
          new AppUser(
              rs.getString("subject"),
              rs.getString("email"),
              rs.getString("display_name"),
              UserRole.valueOf(rs.getString("role")));
  private final JdbcTemplate db;

  public JdbcUserRepository(JdbcTemplate db) {
    this.db = db;
  }

  @Override
  public void createCustomerIfAbsent(String subject, String email, String name) {
    db.update(
        """
        INSERT INTO identity.app_users(subject,email,display_name,role) VALUES (?,?,?,'CUSTOMER')
        ON CONFLICT (subject) DO NOTHING
        """,
        subject,
        email,
        name);
  }

  @Override
  public Optional<AppUser> findBySubject(String subject) {
    return db
        .query(
            "SELECT subject,email,display_name,role FROM identity.app_users WHERE subject=?",
            USER,
            subject)
        .stream()
        .findFirst();
  }

  @Override
  public List<AppUser> findPage(int page, int size) {
    return db.query(
        """
        SELECT subject,email,display_name,role FROM identity.app_users
        ORDER BY created_at,subject LIMIT ? OFFSET ?
        """,
        USER,
        size,
        (long) page * size);
  }

  @Override
  public void lockRoleChanges() {
    db.execute("LOCK TABLE identity.app_users IN SHARE ROW EXCLUSIVE MODE");
  }

  @Override
  public long countAdministrators() {
    return db.queryForObject(
        "SELECT count(*) FROM identity.app_users WHERE role='ADMIN'", Long.class);
  }

  @Override
  public void updateRole(String subject, UserRole role) {
    db.update("UPDATE identity.app_users SET role=? WHERE subject=?", role.name(), subject);
  }
}
