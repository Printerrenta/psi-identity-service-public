package lt.printerrental.auth;

import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class UserService {
  private final UserRepository users;

  public UserService(UserRepository users) {
    this.users = users;
  }

  @Transactional
  public AppUser registerIdentity(String subject, String email, String name) {
    // The repository assigns CUSTOMER only; identity-provider roles are never application
    // permissions.
    return users
        .findBySubject(subject)
        .orElseGet(
            () -> {
              users.createCustomerIfAbsent(subject, bounded(email, 254), bounded(name, 255));
              return get(subject);
            });
  }

  public AppUser get(String subject) {
    return users.findBySubject(subject).orElseThrow(UserNotFoundException::new);
  }

  public List<AppUser> list(int page, int size) {
    if (page < 0 || page > 10000 || size < 1 || size > 100) {
      throw new IllegalArgumentException("Invalid pagination");
    }
    return users.findPage(page, size);
  }

  private String bounded(String value, int length) {
    if (value == null) {
      return "";
    }
    return value.length() > length ? value.substring(0, length) : value;
  }
}
