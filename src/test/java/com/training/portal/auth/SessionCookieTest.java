package com.training.portal.auth;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.web.server.servlet.context.ServletWebServerApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SessionCookieTest {
    @LocalServerPort int port;
    @Autowired ServletWebServerApplicationContext context;

    @Test
    void csrfEndpointIssuesHttpOnlyCookieWithLocalSettings() throws Exception {
        try (var client = HttpClient.newHttpClient()) {
            var response = client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/auth/csrf"))
                    .GET().build(), HttpResponse.BodyHandlers.ofString());
            assertThat(response.statusCode()).isEqualTo(200);
            var cookies = response.headers().allValues("set-cookie");
            assertThat(cookies).anySatisfy(cookie -> {
                assertThat(cookie).startsWith("JSESSIONID=").contains("HttpOnly", "SameSite=Lax").doesNotContain("Secure");
            });
            assertThat(context.getServletContext().getSessionTimeout()).isEqualTo(60);
            assertThat(context.getServletContext().getEffectiveSessionTrackingModes())
                    .containsExactly(jakarta.servlet.SessionTrackingMode.COOKIE);
        }
    }
}
