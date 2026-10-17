package com.webox.file;

import com.webox.common.ApiResponse;
import com.webox.common.BusinessException;
import com.webox.common.ErrorCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@Tag(name = "Dish images")
public class FileController {

    private final FileStorageService storageService;

    public FileController(FileStorageService storageService) {
        this.storageService = storageService;
    }

    /** Admin-only by virtue of the {@code /api/admin/**} rule in SecurityConfig. */
    @PostMapping("/api/admin/files/images")
    @Operation(summary = "Upload a dish image (JPG, PNG or WebP, up to 2 MB)")
    public ApiResponse<ImageUploadResponse> upload(@RequestParam("file") MultipartFile file) {
        String storedName = storageService.store(file);
        return ApiResponse.ok(new ImageUploadResponse("/api/files/images/" + storedName));
    }

    /**
     * Serves a stored image. The name must match the generated-UUID allow-list, so a crafted
     * path such as {@code ../../application.yml} is rejected before touching the filesystem.
     */
    @GetMapping("/api/files/images/{name}")
    @Operation(summary = "Read a stored dish image")
    public ResponseEntity<Resource> read(@PathVariable String name) throws IOException {
        Path path = storageService.resolveStored(name)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Image not found."));
        byte[] bytes = Files.readAllBytes(path);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(FileStorageService.contentTypeFor(name)))
                .cacheControl(CacheControl.maxAge(java.time.Duration.ofDays(30)).cachePublic())
                .body(new ByteArrayResource(bytes));
    }

    public record ImageUploadResponse(String url) {
    }
}
