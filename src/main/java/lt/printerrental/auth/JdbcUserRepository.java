package lt.printerrental.auth;

import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcUserRepository implements UserRepository {
  private static final long ROLE_CHANGE_LOCK_KEY = 7341928374619283741L;
  private static final RowMapper<AppUser> USER =
      (rs, row) ->
          new AppUser(
              rs.getString("subject"),
              rs.getString("email"),
              rs.getString("display_name"),
              UserRole.valueOf(rs.getString("role")));
  private final JdbcTemplate db;
  private final String table;

  public JdbcUserRepository(JdbcTemplate db) {
    this(db, "identity");
  }

  @Autowired
  public JdbcUserRepository(JdbcTemplate db, @Value("${DB_SCHEMA:identity}") String schema) {
    if (schema == null || !schema.matches("[a-z][a-z0-9_]*")) {
      throw new IllegalArgumentException("Invalid schema");
    }
    this.db = db;
    this.table = schema + ".app_users";
  }

  @Override
  public void createCustomerIfAbsent(String subject, String email, String name) {
    db.update(
        "INSERT INTO "
            + table
            + "(subject,email,display_name,role) VALUES (?,?,?,'CUSTOMER')"
            + " ON CONFLICT (subject) DO NOTHING",
        subject,
        email,
        name);
  }

  @Override
  public Optional<AppUser> findBySubject(String subject) {
    return db
        .query(
            "SELECT subject,email,display_name,role FROM " + table + " WHERE subject=?",
            USER,
            subject)
        .stream()
        .findFirst();
  }

  @Override
  public List<AppUser> findPage(int page, int size) {
    return db.query(
        "SELECT subject,email,display_name,role FROM "
            + table
            + " ORDER BY created_at,subject LIMIT ? OFFSET ?",
        USER,
        size,
        (long) page * size);
  }

  @Override
  public Optional<AppUser> findByEmail(String email) {
    return db
        .query(
            "SELECT subject,email,display_name,role FROM "
                + table
                + " WHERE lower(email)=lower(?) ORDER BY created_at LIMIT 1",
            USER,
            email)
        .stream()
        .findFirst();
  }

  @Override
  public List<AppUser> findCustomers(int page, int size) {
    return db.query(
        "SELECT subject,email,display_name,role FROM "
            + table
            + " WHERE role='CUSTOMER' ORDER BY created_at,subject LIMIT ? OFFSET ?",
        USER,
        size,
        (long) page * size);
  }

  @Override
  public void lockRoleChanges() {
    db.execute("SELECT pg_advisory_xact_lock(" + ROLE_CHANGE_LOCK_KEY + ")");
  }

  @Override
  public long countAdministrators() {
    Long count =
        db.queryForObject("SELECT count(*) FROM " + table + " WHERE role='ADMIN'", Long.class);
    return count == null ? 0L : count;
  }

  @Override
  public void updateRole(String subject, UserRole role) {
    db.update("UPDATE " + table + " SET role=? WHERE subject=?", role.name(), subject);
  }
}
