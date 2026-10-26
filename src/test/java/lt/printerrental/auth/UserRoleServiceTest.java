package lt.printerrental.auth;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

@ExtendWith(MockitoExtension.class)
class UserRoleServiceTest {
  @Mock UserRepository users;
  @Mock CurrentUser currentUser;
  UserRoleService service;

  @BeforeEach
  void setup() {
    service = new UserRoleService(users, currentUser);
  }

  @Test
  void nonAdministratorCannotChangeRolesEvenWithoutAController() {
    assertThatThrownBy(() -> service.changeRole("someone", UserRole.ADMIN))
        .isInstanceOf(AccessDeniedException.class);
    verifyNoInteractions(users);
  }

  @Test
  void lastAdministratorCannotBeDemoted() {
    when(currentUser.hasRole(UserRole.ADMIN)).thenReturn(true);
    when(users.findBySubject("root"))
        .thenReturn(Optional.of(new AppUser("root", "root@example.test", "Root", UserRole.ADMIN)));
    when(users.countAdministrators()).thenReturn(1L);
    assertThatThrownBy(() -> service.changeRole("root", UserRole.EMPLOYEE))
        .isInstanceOf(UserRoleConflictException.class);
    verify(users, never()).updateRole(any(), any());
  }

  @Test
  void roleChangeLocksBeforeReadingTheAdministratorCount() {
    when(currentUser.hasRole(UserRole.ADMIN)).thenReturn(true);
    when(users.findBySubject("root"))
        .thenReturn(Optional.of(new AppUser("root", "root@example.test", "Root", UserRole.ADMIN)));
    when(users.countAdministrators()).thenReturn(2L);
    assertThat(service.changeRole("root", UserRole.CUSTOMER).role()).isEqualTo(UserRole.CUSTOMER);
    var order = inOrder(users);
    order.verify(users).lockRoleChanges();
    order.verify(users).findBySubject("root");
    order.verify(users).countAdministrators();
    order.verify(users).updateRole("root", UserRole.CUSTOMER);
  }
}
