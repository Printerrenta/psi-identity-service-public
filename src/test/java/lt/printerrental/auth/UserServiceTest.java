package lt.printerrental.auth;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Optional;
import org.junit.jupiter.api.Test;

class UserServiceTest {
  @Test
  void registerIdentityReadsBeforeWriteAndSkipsInsertForExistingUser() {
    var users = mock(UserRepository.class);
    var existing = new AppUser("sub-1", "alice@example.test", "Alice", UserRole.EMPLOYEE);
    when(users.findBySubject("sub-1")).thenReturn(Optional.of(existing));

    var service = new UserService(users);
    var resolved = service.registerIdentity("sub-1", "alice@example.test", "Alice");

    assertThat(resolved).isEqualTo(existing);
    verify(users).findBySubject("sub-1");
    verify(users, never()).createCustomerIfAbsent(any(), any(), any());
  }

  @Test
  void registerIdentityInsertsWhenAbsentAndTruncatesOverlongFields() {
    var users = mock(UserRepository.class);
    String longEmail = "a".repeat(260);
    String longName = "N".repeat(270);
    var created =
        new AppUser("sub-2", longEmail.substring(0, 254), longName.substring(0, 255), UserRole.CUSTOMER);
    when(users.findBySubject("sub-2")).thenReturn(Optional.empty(), Optional.of(created));

    var service = new UserService(users);
    var resolved = service.registerIdentity("sub-2", longEmail, longName);

    assertThat(resolved).isEqualTo(created);
    var order = inOrder(users);
    order.verify(users).findBySubject("sub-2");
    order
        .verify(users)
        .createCustomerIfAbsent("sub-2", longEmail.substring(0, 254), longName.substring(0, 255));
    order.verify(users).findBySubject("sub-2");
  }

  @Test
  void registerIdentityDefaultsNullEmailAndNameToEmptyStrings() {
    var users = mock(UserRepository.class);
    var created = new AppUser("sub-3", "", "", UserRole.CUSTOMER);
    when(users.findBySubject("sub-3")).thenReturn(Optional.empty(), Optional.of(created));

    var service = new UserService(users);
    assertThat(service.registerIdentity("sub-3", null, null)).isEqualTo(created);
    verify(users).createCustomerIfAbsent("sub-3", "", "");
  }

  @Test
  void listRejectsOutOfBoundsPagination() {
    var users = mock(UserRepository.class);
    var service = new UserService(users);

    assertThatThrownBy(() -> service.list(-1, 20)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> service.list(10001, 20)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> service.list(0, 0)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> service.list(0, 101)).isInstanceOf(IllegalArgumentException.class);
    verifyNoInteractions(users);
  }
}
