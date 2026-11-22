package com.example.file;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

/**
 * Stores each user's files in S3 under the key  "{userId}/{filename}".
 */
@RestController
@RequestMapping("/files")
public class FileController {

    private final S3Client s3;
    private final String bucket;

    public FileController(S3Client s3, @Value("${aws.s3.bucket}") String bucket) {
        this.s3 = s3;
        this.bucket = bucket;
    }

    @PostMapping
    public Map<String, Object> upload(@RequestParam("file") MultipartFile file,
                                      @RequestParam("userId") Long userId) throws IOException {
        String name = cleanName(file.getOriginalFilename());
        String key = userId + "/" + name;

        PutObjectRequest req = PutObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .contentType(file.getContentType())
                .build();
        s3.putObject(req, RequestBody.fromBytes(file.getBytes()));

        return Map.of("bucket", bucket, "key", key, "filename", name, "size", file.getSize());
    }

    @GetMapping
    public List<Map<String, Object>> list(@RequestParam("userId") Long userId) {
        var resp = s3.listObjectsV2(ListObjectsV2Request.builder()
                .bucket(bucket)
                .prefix(userId + "/")
                .build());
        return resp.contents().stream()
                .map(o -> Map.<String, Object>of(
                        "key", o.key(),
                        "filename", o.key().substring(o.key().indexOf('/') + 1),
                        "size", o.size(),
                        "lastModified", o.lastModified().toString()))
                .toList();
    }

    @GetMapping("/{userId}/{filename}")
    public ResponseEntity<byte[]> download(@PathVariable Long userId, @PathVariable String filename) {
        try {
            ResponseBytes<GetObjectResponse> obj = s3.getObjectAsBytes(GetObjectRequest.builder()
                    .bucket(bucket)
                    .key(userId + "/" + cleanName(filename))
                    .build());
            String type = obj.response().contentType();
            return ResponseEntity.ok()
                    .contentType(type != null ? MediaType.parseMediaType(type) : MediaType.APPLICATION_OCTET_STREAM)
                    .body(obj.asByteArray());
        } catch (NoSuchKeyException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "File not found");
        }
    }

    /** Keep only the file name (no folders / path tricks). */
    private static String cleanName(String name) {
        if (name == null || name.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Filename is required");
        }
        String n = name.replace('\\', '/');
        n = n.substring(n.lastIndexOf('/') + 1);
        if (n.isBlank() || n.equals("..")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid filename");
        }
        return n;
    }
}
