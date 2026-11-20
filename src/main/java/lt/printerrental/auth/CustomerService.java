package lt.printerrental.auth;

import java.security.SecureRandom;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

@Service
public class CustomerService {
  private static final Logger log = LoggerFactory.getLogger(CustomerService.class);
  private final CurrentUser current;
  private final UserRepository users;
  private final UserService registration;
  private final CustomerIdentityGateway provider;
  private final SecureRandom random = new SecureRandom();
  public CustomerService(CurrentUser current, UserRepository users, UserService registration, CustomerIdentityGateway provider) {
    this.current = current; this.users = users; this.registration = registration; this.provider = provider;
  }
  public record Created(AppUser customer, String temporaryPassword, boolean existing) {}
  public AppUser get(String subject) {
    staff();
    return users.findBySubject(subject).filter(u -> u.role() == UserRole.CUSTOMER)
        .orElseThrow(() -> new CustomerProvisionException(404, "Kliento paskyra nerasta."));
  }
  private void staff() { if (!current.isStaff()) throw new AccessDeniedException("Staff access required"); }
  public List<AppUser> list(int page, int size) {
    staff(); if (page < 0 || page > 10000 || size < 1 || size > 100) throw new IllegalArgumentException("Invalid pagination");
    return users.findCustomers(page, size);
  }
  public Created create(String name, String email) {
    staff(); name = name.strip(); email = email.strip().toLowerCase(Locale.ROOT);
    if (name.isBlank() || name.length() > 255 || email.isBlank() || email.length() > 254) throw new IllegalArgumentException("Invalid customer fields");
    var existing = users.findByEmail(email);
    if (existing.isPresent()) {
      if (existing.get().role() != UserRole.CUSTOMER) throw new CustomerProvisionException(409, "Šis el. paštas priklauso darbuotojo arba administratoriaus paskyrai.");
      log.info("Customer lookup matched existing customer staff={} customer={}", current.subject(), existing.get().subject());
      return new Created(existing.get(), null, true);
    }
    byte[] bytes = new byte[18]; random.nextBytes(bytes);
    String temporary = "Pr!" + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    String subject = provider.create(email, name, temporary);
    var customer = registration.registerIdentity(subject, email, name);
    if (customer.role() != UserRole.CUSTOMER) throw new CustomerProvisionException(409, "Esamos paskyros teisės nekeičiamos.");
    log.info("Customer provisioned staff={} customer={}", current.subject(), customer.subject());
    return new Created(customer, temporary, false);
  }
}
