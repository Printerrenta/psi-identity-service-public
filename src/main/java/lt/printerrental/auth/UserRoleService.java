package lt.printerrental.auth;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserRoleService {
  private final UserRepository users;
  private final CurrentUser currentUser;

  public UserRoleService(UserRepository users, CurrentUser currentUser) {
    this.users = users;
    this.currentUser = currentUser;
  }

  @Transactional
  public AppUser changeRole(String subject, UserRole role) {
    if (!currentUser.hasRole(UserRole.ADMIN)) {
      throw new org.springframework.security.access.AccessDeniedException(
          "Administrator access required");
    }
    // Hold the lock through commit so two demotions cannot both remove the last administrator.
    users.lockRoleChanges();
    var user = users.findBySubject(subject).orElseThrow(UserNotFoundException::new);
    if (user.role() == UserRole.ADMIN
        && role != UserRole.ADMIN
        && users.countAdministrators() <= 1) {
      throw new UserRoleConflictException();
    }
    users.updateRole(subject, role);
    return new AppUser(user.subject(), user.email(), user.name(), role);
  }
}
