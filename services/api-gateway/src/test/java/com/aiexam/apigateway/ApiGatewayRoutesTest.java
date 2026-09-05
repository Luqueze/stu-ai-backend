package com.aiexam.apigateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@TestPropertySource(
        properties = {
            "management.endpoints.web.exposure.include=health,gateway",
            "management.endpoint.gateway.enabled=true"
        })
class ApiGatewayRoutesTest {

    @LocalServerPort private int port;

    private final TestRestTemplate restTemplate = new TestRestTemplate();

    @Test
    @SuppressWarnings("unchecked")
    void gatewayRoutesAreConfiguredForAuthAndExamServices() {
        List<Map<String, Object>> routes =
                restTemplate.getForObject(
                        "http://localhost:" + port + "/actuator/gateway/routes", List.class);

        assertThat(routes).isNotNull();

        Map<String, Object> authRoute =
                routes.stream()
                        .filter(route -> "auth-service".equals(route.get("route_id")))
                        .findFirst()
                        .orElseThrow(() -> new AssertionError("auth-service route not found"));
        assertThat(authRoute.get("uri").toString()).contains("8081");
        assertThat(authRoute.get("predicate").toString()).contains("/api/v1/auth/**").contains("/api/v1/users/**");

        Map<String, Object> examRoute =
                routes.stream()
                        .filter(route -> "exam-service".equals(route.get("route_id")))
                        .findFirst()
                        .orElseThrow(() -> new AssertionError("exam-service route not found"));
        assertThat(examRoute.get("uri").toString()).contains("8082");
        assertThat(examRoute.get("predicate").toString()).contains("/api/v1/exams/**");
    }
}
