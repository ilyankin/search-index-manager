package com.izenkyt.searchindexmanager.indexbuild;

import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

final class IndexBuildTestSupport {

    private IndexBuildTestSupport() {
    }

    static String url(int port, String path) {
        return "http://localhost:" + port + "/api/v1" + path;
    }

    @SuppressWarnings("unchecked")
    static UUID createIndex(TestRestTemplate restTemplate, int port, String name) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        String body = "{\"name\":\"" + name + "\",\"description\":\"integration\","
                + "\"fields\":[{\"name\":\"title\",\"type\":\"text\"},"
                + "{\"name\":\"tag\",\"type\":\"keyword\"},"
                + "{\"name\":\"count\",\"type\":\"long\"}]}";
        ResponseEntity<Map> resp = restTemplate.postForEntity(
                url(port, "/indexes"), new HttpEntity<>(body, headers), Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(resp.getBody()).isNotNull();
        return UUID.fromString((String) resp.getBody().get("id"));
    }

    @SuppressWarnings("unchecked")
    static ResponseEntity<Map> postBuild(TestRestTemplate restTemplate, int port, UUID indexId, String ndjson) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("application/x-ndjson"));
        return restTemplate.postForEntity(
                url(port, "/indexes/" + indexId + "/build"),
                new HttpEntity<>(ndjson, headers), Map.class);
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> getVersion(TestRestTemplate restTemplate, int port, UUID indexId, int version) {
        ResponseEntity<Map> resp = restTemplate.getForEntity(
                url(port, "/indexes/" + indexId + "/versions/" + version), Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).isNotNull();
        return resp.getBody();
    }
}
