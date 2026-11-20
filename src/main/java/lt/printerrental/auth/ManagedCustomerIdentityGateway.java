package lt.printerrental.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Creates only CUSTOMER credentials through the existing managed provider. Never edits Auth SQL. */
@Component
public class ManagedCustomerIdentityGateway implements CustomerIdentityGateway {
  private static final Logger log = LoggerFactory.getLogger(ManagedCustomerIdentityGateway.class);
  private final ObjectMapper json;
  private final String base;
  private final String origin;
  private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

  public ManagedCustomerIdentityGateway(ObjectMapper json, @Value("${NEON_AUTH_BASE_URL}") String base,
      @Value("${AUTH_PUBLIC_ORIGIN:http://localhost:5173}") String origin) {
    this.json = json; this.base = base.replaceAll("/$", ""); this.origin = origin;
    var uri = URI.create(base);
    if (!uri.getScheme().equals("https") && !java.util.Set.of("localhost", "127.0.0.1").contains(uri.getHost())) throw new IllegalArgumentException("Auth must use HTTPS");
  }

  private HttpResponse<String> post(String path, Map<String, String> body, String cookie) throws Exception {
    var request = HttpRequest.newBuilder(URI.create(base + path)).timeout(Duration.ofSeconds(20))
        .header("Content-Type", "application/json").header("Origin", origin);
    if (!cookie.isEmpty()) request.header("Cookie", cookie);
    return http.send(request.POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build(), HttpResponse.BodyHandlers.ofString());
  }

  private String extractCookie(HttpResponse<String> response) {
    return response.headers().allValues("set-cookie").stream()
        .map(s -> s.split(";", 2)[0])
        .filter(s -> !s.isBlank())
        .collect(Collectors.joining("; "));
  }

  private void signOutQuietly(String cookie) {
    if (cookie == null || cookie.isBlank()) {
      return;
    }
    try {
      var res = post("/sign-out", Map.of(), cookie);
      if (res.statusCode() >= 400) {
        log.warn("Identity provider /sign-out returned status {}", res.statusCode());
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      log.warn("Interrupted while signing out temporary provider session");
    } catch (Exception e) {
      // Never log credentials, cookies, or provider sessions.
      log.warn("Failed to sign out temporary provider session ({})", e.getClass().getSimpleName());
    }
  }

  @Override
  public String create(String email, String name, String temporaryPassword) {
    String signUpCookie = "";
    String signInCookie = "";
    try {
      var created = post("/sign-up/email", Map.of("email", email, "name", name, "password", temporaryPassword), "");
      signUpCookie = extractCookie(created);
      if (created.statusCode() == 429) {
        log.warn("Identity provider /sign-up/email rate-limited request (status 429)");
        throw new CustomerProvisionException(429, "Per daug bandymų. Palaukite ir bandykite dar kartą.");
      }
      if (created.statusCode() >= 400) {
        log.warn("Identity provider /sign-up/email failed with status {}", created.statusCode());
        throw new CustomerProvisionException(409, "Paskyros sukurti nepavyko. Jei el. paštas jau registruotas, naudokite slaptažodžio atkūrimą.");
      }
      // Some providers conceal duplicate signup with a synthetic user. A successful sign-in
      // with our generated password proves that we actually created this identity.
      var signed = post("/sign-in/email", Map.of("email", email, "password", temporaryPassword), "");
      signInCookie = extractCookie(signed);
      if (signed.statusCode() >= 400) {
        log.warn("Identity provider /sign-in/email verification failed with status {}", signed.statusCode());
        throw new CustomerProvisionException(409, "Šis el. paštas jau registruotas arba reikia patvirtinti paskyrą. Naudokite slaptažodžio atkūrimą.");
      }
      JsonNode user = json.readTree(signed.body()).path("user");
      String id = user.path("id").asText();
      if (id.isBlank() || !email.equalsIgnoreCase(user.path("email").asText())) {
        log.warn("Identity provider /sign-in/email returned unexpected user identity payload");
        throw new CustomerProvisionException(502, "Autentifikacijos tiekėjas nepatvirtino kliento tapatybės.");
      }
      return id;
    } catch (CustomerProvisionException e) {
      throw e;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      log.warn("Interrupted while provisioning customer with identity provider");
      throw new CustomerProvisionException(503, "Autentifikacijos paslauga laikinai nepasiekiama.");
    } catch (Exception e) {
      // Never log passwords, cookies, or provider tokens.
      log.error("Failed to provision customer with identity provider ({})", e.getClass().getSimpleName());
      throw new CustomerProvisionException(503, "Autentifikacijos paslauga laikinai nepasiekiama. Bandykite dar kartą.");
    } finally {
      signOutQuietly(signInCookie);
      if (!signUpCookie.equals(signInCookie)) {
        signOutQuietly(signUpCookie);
      }
    }
  }
}
