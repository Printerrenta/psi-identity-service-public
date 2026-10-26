package lt.printerrental.auth;

public class UserRoleConflictException extends RuntimeException {
  public UserRoleConflictException() {
    super("Cannot remove the last administrator");
  }
}
