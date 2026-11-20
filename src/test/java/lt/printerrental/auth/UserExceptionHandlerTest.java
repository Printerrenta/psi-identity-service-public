package lt.printerrental.auth;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.Test;
import org.springframework.http.ProblemDetail;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

class UserExceptionHandlerTest {
  @Test
  void mapsIllegalArgumentToBoundedBadRequestProblemDetail() {
    var handler = new UserExceptionHandler();

    ProblemDetail normal = handler.badRequest(new IllegalArgumentException("Invalid pagination"));
    assertThat(normal.getStatus()).isEqualTo(400);
    assertThat(normal.getDetail()).isEqualTo("Invalid pagination");

    ProblemDetail blank = handler.badRequest(new IllegalArgumentException("   "));
    assertThat(blank.getStatus()).isEqualTo(400);
    assertThat(blank.getDetail()).isEqualTo("Invalid request");

    ProblemDetail empty = handler.badRequest(new IllegalArgumentException((String) null));
    assertThat(empty.getStatus()).isEqualTo(400);
    assertThat(empty.getDetail()).isEqualTo("Invalid request");

    ProblemDetail longMsg = handler.badRequest(new IllegalArgumentException("x".repeat(300)));
    assertThat(longMsg.getStatus()).isEqualTo(400);
    assertThat(longMsg.getDetail()).hasSize(200);

    ProblemDetail multiline = handler.badRequest(new IllegalArgumentException("bad\r\ninput"));
    assertThat(multiline.getStatus()).isEqualTo(400);
    assertThat(multiline.getDetail()).isEqualTo("bad input");
  }

  @Test
  void returnsHttp400ThroughMvcAdvice() throws Exception {
    var mvc =
        MockMvcBuilders.standaloneSetup(new FailingProbe())
            .setControllerAdvice(new UserExceptionHandler())
            .build();
    mvc.perform(get("/probe/illegal"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.detail").value("Invalid pagination"));
  }

  @RestController
  static class FailingProbe {
    @GetMapping("/probe/illegal")
    String fail() {
      throw new IllegalArgumentException("Invalid pagination");
    }
  }
}
