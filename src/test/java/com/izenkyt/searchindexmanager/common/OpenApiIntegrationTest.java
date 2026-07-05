package com.izenkyt.searchindexmanager.common;

import com.izenkyt.searchindexmanager.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ContextConfiguration;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@ContextConfiguration(classes = TestcontainersConfiguration.class)
class OpenApiIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void apiDocsAreServedAtDefaultPath() {
        ResponseEntity<String> response = restTemplate.getForEntity("/v3/api-docs", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody())
                .contains("\"openapi\"")
                .contains("Search Index Manager")
                .contains("/indexes");
    }

    @Test
    void swaggerUiIsServedAtDefaultPath() {
        ResponseEntity<String> response = restTemplate.getForEntity("/swagger-ui/index.html", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("Swagger UI");
    }

    @Test
    void versionedApiRoutesStillWorkAlongsideSwagger() {
        ResponseEntity<String> response = restTemplate.getForEntity("/api/v1/indexes", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }
}
