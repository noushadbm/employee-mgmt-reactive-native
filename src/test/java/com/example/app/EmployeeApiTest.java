package com.example.app;

import com.jayway.jsonpath.JsonPath;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest
@AutoConfigureWebTestClient
class EmployeeApiTest {

    @Autowired WebTestClient client;

    private Map<String, Object> body(String email) {
        return Map.of("firstName", "Ada", "lastName", "Lovelace", "email", email, "department", "Eng");
    }

    @Test
    void fullCrudLifecycle() {
        byte[] created = client.post().uri("/api/employees").bodyValue(body("crud@example.com"))
                .exchange().expectStatus().isCreated()
                .expectHeader().exists("Location")
                .expectBody().returnResult().getResponseBody();
        Integer id = JsonPath.read(new String(created), "$.id");

        client.get().uri("/api/employees/{id}", id).exchange().expectStatus().isOk()
                .expectBody().jsonPath("$.email").isEqualTo("crud@example.com");

        client.get().uri("/api/employees?department=Eng").exchange().expectStatus().isOk()
                .expectBody().jsonPath("$[?(@.id==" + id + ")]").exists();

        client.put().uri("/api/employees/{id}", id)
                .bodyValue(Map.of("firstName", "Ada", "lastName", "Byron", "email", "crud@example.com"))
                .exchange().expectStatus().isOk()
                .expectBody().jsonPath("$.lastName").isEqualTo("Byron");

        client.delete().uri("/api/employees/{id}", id).exchange().expectStatus().isNoContent();
        client.get().uri("/api/employees/{id}", id).exchange().expectStatus().isNotFound();
        client.delete().uri("/api/employees/{id}", id).exchange().expectStatus().isNotFound();
    }

    @Test
    void duplicateEmailIsConflict() {
        client.post().uri("/api/employees").bodyValue(body("dup@example.com")).exchange().expectStatus().isCreated();
        client.post().uri("/api/employees").bodyValue(body("dup@example.com")).exchange()
                .expectStatus().isEqualTo(409);
    }

    @Test
    void invalidInputIsBadRequest() {
        client.post().uri("/api/employees")
                .bodyValue(Map.of("firstName", "", "lastName", "X", "email", "not-an-email"))
                .exchange().expectStatus().isBadRequest()
                .expectBody().jsonPath("$.errors").isArray();
    }

    @Test
    void updateUnknownIsNotFound() {
        client.put().uri("/api/employees/99999").bodyValue(body("nobody@example.com"))
                .exchange().expectStatus().isNotFound();
    }
}
