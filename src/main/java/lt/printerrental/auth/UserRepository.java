package lt.printerrental.auth;

import java.util.List;
import java.util.Optional;

/** Persistence operations used by user registration and role management. */
public interface UserRepository {
  void createCustomerIfAbsent(String subject, String email, String name);

  Optional<AppUser> findBySubject(String subject);

  List<AppUser> findPage(int page, int size);

  void lockRoleChanges();

  long countAdministrators();

  void updateRole(String subject, UserRole role);
}
