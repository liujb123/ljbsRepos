package com.webox.file;

import com.webox.common.BusinessException;
import com.webox.common.ErrorCode;
import com.webox.config.WeBoxProperties;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * Stores dish images on the local filesystem.
 *
 * <p>Hardening applied to every upload:
 * <ul>
 *   <li>size cap and extension allow-list,</li>
 *   <li><b>magic-byte</b> sniffing so a renamed executable cannot masquerade as an image,</li>
 *   <li>the original filename is discarded entirely and replaced by a UUID,</li>
 *   <li>reads are served through a strict allow-list pattern, defeating {@code ../} traversal.</li>
 * </ul>
 */
@Service
public class FileStorageService {

    private static final Logger log = LoggerFactory.getLogger(FileStorageService.class);
    private static final long MAX_BYTES = 2L * 1024 * 1024;

    /** Only files this service generated can be read back. */
    public static final Pattern STORED_NAME =
            Pattern.compile("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.(jpg|jpeg|png|webp)$");

    private final Path root;

    public FileStorageService(WeBoxProperties properties) {
        this.root = Path.of(properties.storage().uploadDir()).toAbsolutePath().normalize();
    }

    @PostConstruct
    void prepare() throws IOException {
        Files.createDirectories(root);
        log.info("Dish image storage directory: {}", root);
    }

    /** @return the generated file name, suitable for {@code GET /api/files/images/{name}}. */
    public String store(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.UPLOAD_INVALID_TYPE);
        }
        if (file.getSize() > MAX_BYTES) {
            throw new BusinessException(ErrorCode.UPLOAD_TOO_LARGE);
        }

        byte[] header = readHeader(file);
        String extension = detectExtension(header);
        if (extension == null) {
            throw new BusinessException(ErrorCode.UPLOAD_INVALID_TYPE);
        }
        String declared = file.getOriginalFilename();
        if (declared != null && !declared.isBlank() && !declaredExtensionAllowed(declared)) {
            throw new BusinessException(ErrorCode.UPLOAD_INVALID_TYPE);
        }

        String storedName = UUID.randomUUID() + "." + extension;
        Path target = root.resolve(storedName).normalize();
        if (!target.startsWith(root)) {
            // Defensive: resolve() plus a UUID name cannot escape, but never trust a path blindly.
            throw new BusinessException(ErrorCode.UPLOAD_INVALID_TYPE);
        }
        try (InputStream in = file.getInputStream()) {
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ex) {
            log.error("Failed to store uploaded image", ex);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "The image could not be saved.");
        }
        log.info("Stored dish image {} ({} bytes)", storedName, file.getSize());
        return storedName;
    }

    /** @return the absolute path of a stored image, or empty when the name is not acceptable. */
    public java.util.Optional<Path> resolveStored(String name) {
        if (name == null || !STORED_NAME.matcher(name).matches()) {
            return java.util.Optional.empty();
        }
        Path candidate = root.resolve(name).normalize();
        if (!candidate.startsWith(root) || !Files.isRegularFile(candidate)) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(candidate);
    }

    private static byte[] readHeader(MultipartFile file) {
        try (InputStream in = file.getInputStream()) {
            return in.readNBytes(12);
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.UPLOAD_INVALID_TYPE);
        }
    }

    /** Identifies the real format from the leading bytes, ignoring whatever the client claimed. */
    private static String detectExtension(byte[] header) {
        if (header.length >= 3
                && (header[0] & 0xFF) == 0xFF && (header[1] & 0xFF) == 0xD8 && (header[2] & 0xFF) == 0xFF) {
            return "jpg";
        }
        if (header.length >= 8
                && (header[0] & 0xFF) == 0x89 && header[1] == 'P' && header[2] == 'N' && header[3] == 'G'
                && (header[4] & 0xFF) == 0x0D && (header[5] & 0xFF) == 0x0A
                && (header[6] & 0xFF) == 0x1A && (header[7] & 0xFF) == 0x0A) {
            return "png";
        }
        if (header.length >= 12
                && header[0] == 'R' && header[1] == 'I' && header[2] == 'F' && header[3] == 'F'
                && header[8] == 'W' && header[9] == 'E' && header[10] == 'B' && header[11] == 'P') {
            return "webp";
        }
        return null;
    }

    private static boolean declaredExtensionAllowed(String filename) {
        String lower = filename.toLowerCase(Locale.ROOT);
        return lower.endsWith(".jpg") || lower.endsWith(".jpeg")
                || lower.endsWith(".png") || lower.endsWith(".webp");
    }

    public static String contentTypeFor(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".png")) {
            return "image/png";
        }
        if (lower.endsWith(".webp")) {
            return "image/webp";
        }
        return "image/jpeg";
    }
}
