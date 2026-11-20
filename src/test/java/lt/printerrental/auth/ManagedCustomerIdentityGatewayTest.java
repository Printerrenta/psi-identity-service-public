package lt.printerrental.auth;

import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;

class ManagedCustomerIdentityGatewayTest {
  HttpServer server;
  List<String> signedOutCookies;
  AtomicInteger signInStatus;

  @BeforeEach
  void start() throws Exception {
    signedOutCookies = new CopyOnWriteArrayList<>();
    signInStatus = new AtomicInteger(200);
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/sign-up/email",
        e -> {
          e.getResponseHeaders().add("Set-Cookie", "signup_session=abc; Path=/; HttpOnly");
          byte[] body = "{\"user\":{\"id\":\"cust-1\"}}".getBytes(StandardCharsets.UTF_8);
          e.sendResponseHeaders(200, body.length);
          e.getResponseBody().write(body);
          e.close();
        });
    server.createContext(
        "/sign-in/email",
        e -> {
          int status = signInStatus.get();
          if (status == 200) {
            e.getResponseHeaders().add("Set-Cookie", "signin_session=def; Path=/; HttpOnly");
          }
          byte[] body =
              status == 200
                  ? "{\"user\":{\"id\":\"cust-1\",\"email\":\"client@example.test\"}}"
                      .getBytes(StandardCharsets.UTF_8)
                  : "{\"error\":\"conflict\"}".getBytes(StandardCharsets.UTF_8);
          e.sendResponseHeaders(status, body.length);
          e.getResponseBody().write(body);
          e.close();
        });
    server.createContext(
        "/sign-out",
        e -> {
          String cookie = e.getRequestHeaders().getFirst("Cookie");
          if (cookie != null) {
            signedOutCookies.add(cookie);
          }
          byte[] body = "{}".getBytes(StandardCharsets.UTF_8);
          e.sendResponseHeaders(200, body.length);
          e.getResponseBody().write(body);
          e.close();
        });
    server.start();
  }

  @AfterEach
  void stop() {
    server.stop(0);
  }

  @Test
  void signsOutBothSignUpAndSignInSessions() {
    var gateway =
        new ManagedCustomerIdentityGateway(
            new ObjectMapper(),
            "http://127.0.0.1:" + server.getAddress().getPort(),
            "http://localhost:5173");

    String id = gateway.create("client@example.test", "Client", "TempPass123!");
    assertThat(id).isEqualTo("cust-1");
    assertThat(signedOutCookies)
        .containsExactlyInAnyOrder("signup_session=abc", "signin_session=def");
  }

  @Test
  void signsOutSignUpSessionEvenWhenSignInFails() {
    signInStatus.set(401);
    var gateway =
        new ManagedCustomerIdentityGateway(
            new ObjectMapper(),
            "http://127.0.0.1:" + server.getAddress().getPort(),
            "http://localhost:5173");

    assertThatThrownBy(() -> gateway.create("client@example.test", "Client", "TempPass123!"))
        .isInstanceOf(CustomerProvisionException.class);
    assertThat(signedOutCookies).containsExactly("signup_session=abc");
  }
}
