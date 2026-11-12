package lt.printerrental.auth;

public interface CustomerIdentityGateway {
  String create(String email, String name, String temporaryPassword);
}
