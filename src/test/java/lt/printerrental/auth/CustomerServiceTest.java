package lt.printerrental.auth;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

class CustomerServiceTest {
  @Test void selectedCustomerLookupRequiresStaffAndRejectsNonCustomers() {
    var current=mock(CurrentUser.class); var users=mock(UserRepository.class);
    var service=new CustomerService(current,users,mock(UserService.class),mock(CustomerIdentityGateway.class));
    assertThatThrownBy(() -> service.get("alice")).isInstanceOf(AccessDeniedException.class);
    verifyNoInteractions(users);
    when(current.isStaff()).thenReturn(true);
    var alice=new AppUser("alice","alice@example.com","Alice",UserRole.CUSTOMER);
    when(users.findBySubject("alice")).thenReturn(Optional.of(alice));
    assertThat(service.get("alice")).isEqualTo(alice);
    when(users.findBySubject("employee")).thenReturn(Optional.of(new AppUser("employee","staff@example.com","Staff",UserRole.EMPLOYEE)));
    assertThatThrownBy(() -> service.get("employee")).isInstanceOf(CustomerProvisionException.class);
    when(users.findBySubject("missing")).thenReturn(Optional.empty());
    assertThatThrownBy(() -> service.get("missing")).isInstanceOf(CustomerProvisionException.class);
  }
  @Test void customerCannotProvisionUsersOrReadDirectory() {
    var current = mock(CurrentUser.class); var users = mock(UserRepository.class); var gateway = mock(CustomerIdentityGateway.class);
    var service = new CustomerService(current, users, mock(UserService.class), gateway);
    assertThatThrownBy(() -> service.create("Name", "client@example.com")).isInstanceOf(AccessDeniedException.class);
    assertThatThrownBy(() -> service.list(0, 20)).isInstanceOf(AccessDeniedException.class);
    verifyNoInteractions(users, gateway);
  }
  @Test void staffCreatesOnlyCustomerAndGetsOneTimeGeneratedPassword() {
    var current = mock(CurrentUser.class); when(current.isStaff()).thenReturn(true);
    var users = mock(UserRepository.class); when(users.findByEmail("client@example.com")).thenReturn(Optional.empty());
    var gateway = mock(CustomerIdentityGateway.class); when(gateway.create(eq("client@example.com"), eq("Client"), anyString())).thenReturn("new-subject");
    var registration = mock(UserService.class); when(registration.registerIdentity("new-subject", "client@example.com", "Client")).thenReturn(new AppUser("new-subject", "client@example.com", "Client", UserRole.CUSTOMER));
    var result = new CustomerService(current, users, registration, gateway).create(" Client ", "CLIENT@example.com");
    assertThat(result.customer().role()).isEqualTo(UserRole.CUSTOMER);
    assertThat(result.temporaryPassword()).hasSizeGreaterThanOrEqualTo(24);
    assertThat(result.existing()).isFalse();
  }
  @Test void retryDoesNotResetAnExistingCustomerPassword() {
    var current = mock(CurrentUser.class); when(current.isStaff()).thenReturn(true);
    var users = mock(UserRepository.class); var customer = new AppUser("existing", "client@example.com", "Client", UserRole.CUSTOMER);
    when(users.findByEmail("client@example.com")).thenReturn(Optional.of(customer));
    var gateway = mock(CustomerIdentityGateway.class);
    var result = new CustomerService(current, users, mock(UserService.class), gateway).create("Client", "client@example.com");
    assertThat(result.existing()).isTrue(); assertThat(result.temporaryPassword()).isNull(); verifyNoInteractions(gateway);
  }
  @Test void existingAdminCannotBeChangedIntoCustomer() {
    var current = mock(CurrentUser.class); when(current.isStaff()).thenReturn(true);
    var users = mock(UserRepository.class); when(users.findByEmail("admin@example.com")).thenReturn(Optional.of(new AppUser("admin", "admin@example.com", "Admin", UserRole.ADMIN)));
    var gateway = mock(CustomerIdentityGateway.class);
    assertThatThrownBy(() -> new CustomerService(current, users, mock(UserService.class), gateway).create("Admin", "admin@example.com")).isInstanceOf(CustomerProvisionException.class);
    verifyNoInteractions(gateway);
  }
}
