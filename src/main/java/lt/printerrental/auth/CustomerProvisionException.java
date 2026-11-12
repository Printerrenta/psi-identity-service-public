package lt.printerrental.auth;

public class CustomerProvisionException extends RuntimeException {
  private final int status;
  public CustomerProvisionException(int status, String message) { super(message); this.status = status; }
  public int status() { return status; }
}
