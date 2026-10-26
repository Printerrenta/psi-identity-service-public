package lt.printerrental.auth;

import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

@Component
public class LocalUserIdentityResolver implements UserIdentityResolver {
  private final UserService users;

  public LocalUserIdentityResolver(UserService users) {
    this.users = users;
  }

  public AppUser resolve(Jwt jwt) {
    return users.registerIdentity(
        jwt.getSubject(), jwt.getClaimAsString("email"), jwt.getClaimAsString("name"));
  }
}
