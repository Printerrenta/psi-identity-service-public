package lt.printerrental.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Creates only CUSTOMER credentials through the existing managed provider. Never edits Auth SQL. */
@Component
public class ManagedCustomerIdentityGateway implements CustomerIdentityGateway {
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

  @Override public String create(String email, String name, String temporaryPassword) {
    String cookie = "";
    try {
      var created = post("/sign-up/email", Map.of("email", email, "name", name, "password", temporaryPassword), "");
      if (created.statusCode() == 429) throw new CustomerProvisionException(429, "Per daug bandymų. Palaukite ir bandykite dar kartą.");
      if (created.statusCode() >= 400) throw new CustomerProvisionException(409, "Paskyros sukurti nepavyko. Jei el. paštas jau registruotas, naudokite slaptažodžio atkūrimą.");
      // Some providers conceal duplicate signup with a synthetic user. A successful sign-in
      // with our generated password proves that we actually created this identity.
      var signed = post("/sign-in/email", Map.of("email", email, "password", temporaryPassword), "");
      cookie = signed.headers().allValues("set-cookie").stream().map(s -> s.split(";", 2)[0]).collect(Collectors.joining("; "));
      if (signed.statusCode() >= 400) throw new CustomerProvisionException(409, "Šis el. paštas jau registruotas arba reikia patvirtinti paskyrą. Naudokite slaptažodžio atkūrimą.");
      JsonNode user = json.readTree(signed.body()).path("user");
      String id = user.path("id").asText();
      if (id.isBlank() || !email.equalsIgnoreCase(user.path("email").asText())) throw new CustomerProvisionException(502, "Autentifikacijos tiekėjas nepatvirtino kliento tapatybės.");
      return id;
    } catch (CustomerProvisionException e) { throw e; }
    catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new CustomerProvisionException(503, "Autentifikacijos paslauga laikinai nepasiekiama."); }
    catch (Exception e) { throw new CustomerProvisionException(503, "Autentifikacijos paslauga laikinai nepasiekiama. Bandykite dar kartą."); }
    finally { if (!cookie.isEmpty()) try { post("/sign-out", Map.of(), cookie); } catch (Exception ignored) { /* Never log credentials or provider sessions. */ } }
  }
}
