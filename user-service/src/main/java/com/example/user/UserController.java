package com.example.user;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClientException;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/users")
public class UserController {

    private final UserRepository repo;
    private final FileServiceClient fileClient;

    public UserController(UserRepository repo, FileServiceClient fileClient) {
        this.repo = repo;
        this.fileClient = fileClient;
    }

    // ---- user CRUD ----

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public User create(@Valid @RequestBody User user) {
        user.setId(null);
        return repo.save(user);
    }

    @GetMapping
    public List<User> all() {
        return repo.findAll();
    }

    @GetMapping("/{id}")
    public User get(@PathVariable Long id) {
        return findOr404(id);
    }

    // ---- file operations: validate the user, then call File Service synchronously ----

    @PostMapping("/{id}/files")
    public Map<String, Object> upload(@PathVariable Long id, @RequestParam("file") MultipartFile file)
            throws IOException {
        findOr404(id);
        try {
            return fileClient.upload(id, file);
        } catch (RestClientException e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "File Service call failed: " + e.getMessage());
        }
    }

    @GetMapping("/{id}/files")
    public List<Map<String, Object>> files(@PathVariable Long id) {
        findOr404(id);
        try {
            return fileClient.list(id);
        } catch (RestClientException e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "File Service call failed: " + e.getMessage());
        }
    }

    @GetMapping("/{id}/files/{filename}")
    public ResponseEntity<byte[]> download(@PathVariable Long id, @PathVariable String filename) {
        findOr404(id);
        try {
            ResponseEntity<byte[]> resp = fileClient.download(id, filename);
            MediaType type = resp.getHeaders().getContentType();
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                    .contentType(type != null ? type : MediaType.APPLICATION_OCTET_STREAM)
                    .body(resp.getBody());
        } catch (RestClientException e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "File Service call failed: " + e.getMessage());
        }
    }

    private User findOr404(Long id) {
        return repo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User " + id + " not found"));
    }
}
