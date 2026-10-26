package lt.printerrental.auth;

import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
  private final UserService users;
  private final CurrentUser currentUser;
  private final String baseUrl;

  public AuthController(
      UserService users, CurrentUser currentUser, @Value("${NEON_AUTH_BASE_URL}") String baseUrl) {
    this.users = users;
    this.currentUser = currentUser;
    this.baseUrl = baseUrl;
  }

  @GetMapping("/config")
  public Map<String, String> config() {
    return Map.of("authUrl", baseUrl, "scheme", "Bearer");
  }

  @GetMapping("/me")
  public AppUser me() {
    return users.get(currentUser.subject());
  }
}
