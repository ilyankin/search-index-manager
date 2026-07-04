package com.izenkyt.searchindexmanager.index.api;

import com.izenkyt.searchindexmanager.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ContextConfiguration;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.InstanceOfAssertFactories.LIST;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@ContextConfiguration(classes = TestcontainersConfiguration.class)
class IndexApiIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    private String url(String path) {
        return "http://localhost:" + port + "/api/v1" + path;
    }

    private ResponseEntity<Map> createIndex(String name) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        String body = "{\"name\":\"" + name + "\",\"description\":\"integration\",\"fields\":[{\"name\":\"title\",\"type\":\"text\"}]}";
        return restTemplate.postForEntity(url("/indexes"), new HttpEntity<>(body, headers), Map.class);
    }

    @Test
    void createAndGetByIdAndList_thenDuplicateNameReturns409() {
        // create
        ResponseEntity<Map> created = createIndex("idx-int-" + UUID.randomUUID());
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(created.getHeaders().getLocation()).isNotNull();
        assertThat(created.getBody()).isNotNull();
        assertThat(created.getBody().get("name")).asString().startsWith("idx-int-");
        assertThat(created.getBody().get("fields")).asInstanceOf(LIST).isNotEmpty();

        // location -> id
        String location = created.getHeaders().getLocation().toString();
        UUID id = UUID.fromString(location.substring(location.lastIndexOf('/') + 1));
        assertThat(created.getBody().get("id")).isEqualTo(id.toString());
        assertThat(created.getBody().get("name")).isNotNull();

        // get by id
        ResponseEntity<Map> fetched = restTemplate.getForEntity(url("/indexes/" + id), Map.class);
        assertThat(fetched.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(fetched.getBody()).isNotNull();
        assertThat(fetched.getBody().get("id")).isEqualTo(id.toString());

        // list
        ResponseEntity<Map> list = restTemplate.getForEntity(url("/indexes?page=0&size=10"), Map.class);
        assertThat(list.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(list.getBody()).isNotNull();
        List<?> content = (List<?>) list.getBody().get("content");
        assertThat(content).isNotEmpty();

        // duplicate name -> 409
        String dupName = (String) created.getBody().get("name");
        ResponseEntity<Map> dup = createIndex(dupName);
        assertThat(dup.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(dup.getBody()).isNotNull();
        assertThat(dup.getBody().get("status")).isEqualTo(409);

        // missing index -> 404
        ResponseEntity<Map> missing = restTemplate.getForEntity(url("/indexes/" + UUID.randomUUID()), Map.class);
        assertThat(missing.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void versionsEndpointReturns404_forMissingIndex() {
        ResponseEntity<Map> resp = restTemplate.exchange(
                url("/indexes/" + UUID.randomUUID() + "/versions"),
                HttpMethod.GET, null, Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void invalidCreateReturns400_withFieldErrors() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        String body = "{\"name\":\"\",\"description\":\"x\",\"fields\":[]}";
        ResponseEntity<Map> resp = restTemplate.postForEntity(url("/indexes"), new HttpEntity<>(body, headers), Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().get("status")).isEqualTo(400);
        assertThat(resp.getBody().get("errors")).isNotNull();
    }
}