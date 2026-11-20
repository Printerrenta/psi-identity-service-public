package lt.printerrental.auth;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth/customers")
public class CustomerController {
  private final CustomerService service;
  public CustomerController(CustomerService service) { this.service = service; }
  public record Create(@NotBlank @Size(max=255) String name, @NotBlank @Email @Size(max=254) String email) {}
  @GetMapping("/{subject}") public AppUser get(@PathVariable String subject) { return service.get(subject); }
  @GetMapping
  public List<AppUser> list(
      @RequestParam(defaultValue = "0") @Min(0) @Max(10000) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
    return service.list(page, size);
  }
  @PostMapping public ResponseEntity<CustomerService.Created> create(@Valid @RequestBody Create input) {
    return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.create(input.name(), input.email()));
  }
}
