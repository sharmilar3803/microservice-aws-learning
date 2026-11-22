package com.example.user;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.multipart.MultipartFile;

/**
 * Synchronous REST client: User Service -> File Service.
 * Every call blocks until the File Service answers.
 */
@Component
public class FileServiceClient {

    private final RestClient restClient;

    public FileServiceClient(@Value("${file-service.url}") String baseUrl) {
        this.restClient = RestClient.builder().baseUrl(baseUrl).build();
    }

    public Map<String, Object> upload(Long userId, MultipartFile file) throws IOException {
        // Re-wrap the file so the original filename is kept when forwarding.
        ByteArrayResource resource = new ByteArrayResource(file.getBytes()) {
            @Override
            public String getFilename() {
                return file.getOriginalFilename();
            }
        };
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", resource);
        body.add("userId", String.valueOf(userId));

        return restClient.post()
                .uri("/files")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(body)
                .retrieve()
                .body(new ParameterizedTypeReference<Map<String, Object>>() {});
    }

    public List<Map<String, Object>> list(Long userId) {
        return restClient.get()
                .uri("/files?userId={userId}", userId)
                .retrieve()
                .body(new ParameterizedTypeReference<List<Map<String, Object>>>() {});
    }

    public ResponseEntity<byte[]> download(Long userId, String filename) {
        return restClient.get()
                .uri("/files/{userId}/{filename}", userId, filename)
                .retrieve()
                .toEntity(byte[].class);
    }
}
