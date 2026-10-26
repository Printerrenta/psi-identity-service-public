package lt.printerrental.auth;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/users")
public class AdminUserController {
  private final UserService users;
  private final UserRoleService roles;

  public AdminUserController(UserService users, UserRoleService roles) {
    this.users = users;
    this.roles = roles;
  }

  @GetMapping
  public List<AppUser> list(
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
    return users.list(page, size);
  }

  public record ChangeRole(@NotNull UserRole role) {}

  @PatchMapping("/{subject}/role")
  public AppUser changeRole(@PathVariable String subject, @Valid @RequestBody ChangeRole input) {
    return roles.changeRole(subject, input.role());
  }
}
