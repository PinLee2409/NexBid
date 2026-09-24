package com.nexbid.product.storage;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import com.nexbid.common.exception.BusinessException;
import com.nexbid.common.exception.ErrorCode;

import jakarta.annotation.PostConstruct;

/**
 * EN: Writes uploaded images to disk (guide §12 — local first, object storage later).
 * VI: Ghi ảnh tải lên xuống đĩa (guide §12 — local trước, object storage sau).
 *
 * <p>EN: The file name is always generated here. A name from the client can contain "../" and land the
 *     upload anywhere on the filesystem, which is the whole reason this class exists rather than a one-liner.
 * <p>VI: Tên file luôn do chỗ này sinh ra. Tên do client gửi có thể chứa "../" và đẩy file đi bất kỳ đâu
 *     trên ổ đĩa — đó chính là lý do có cả lớp này thay vì một dòng lệnh.
 */
@Component
public class ImageStorage {

    private static final Logger log = LoggerFactory.getLogger(ImageStorage.class);

    /**
     * EN: Extension is chosen from the detected type, never from the uploaded name.
     * VI: Phần mở rộng chọn theo kiểu nhận dạng được, không lấy từ tên file tải lên.
     */
    private static final Map<String, String> ALLOWED_TYPES = Map.of(
            "image/jpeg", ".jpg",
            "image/png", ".png",
            "image/webp", ".webp",
            "image/avif", ".avif",
            "image/gif", ".gif");

    private final StorageProperties properties;

    public ImageStorage(StorageProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    void prepareDirectory() throws IOException {
        Files.createDirectories(properties.imagesDir());
        log.info("Product images are stored in {}", properties.imagesDir().toAbsolutePath());
    }

    /**
     * EN: Saves one file and returns the public path it is served at.
     * VI: Lưu một file và trả về đường dẫn công khai để truy cập nó.
     */
    public String store(MultipartFile file) {
        if (file.isEmpty()) {
            throw new BusinessException(ErrorCode.IMAGE_INVALID, "The uploaded file is empty");
        }

        if (file.getSize() > properties.maxImageBytes()) {
            throw new BusinessException(
                    ErrorCode.IMAGE_TOO_LARGE,
                    "Images must be at most %d MB".formatted(properties.maxImageBytes() / 1_048_576));
        }

        String extension = ALLOWED_TYPES.get(detectType(file));
        if (extension == null) {
            throw new BusinessException(
                    ErrorCode.IMAGE_INVALID, "Only JPEG, PNG, WebP, AVIF and GIF images are accepted");
        }

        String storedName = UUID.randomUUID() + extension;
        Path target = properties.imagesDir().resolve(storedName);

        try (InputStream in = file.getInputStream()) {
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ex) {
            throw new IllegalStateException("Could not store image " + storedName, ex);
        }

        return properties.publicPath() + "/" + storedName;
    }

    /**
     * EN: Best effort. A row removed from the database while its file lingers is untidy; a file removed
     *     while the row survives would show a broken image, which is worse.
     * VI: Cố gắng hết sức. Xoá dòng trong DB mà file còn sót lại thì chỉ là rác; ngược lại, xoá file mà
     *     dòng vẫn còn sẽ hiện ảnh vỡ, tệ hơn nhiều.
     */
    public void deleteQuietly(String publicUrl) {
        String name = publicUrl.substring(publicUrl.lastIndexOf('/') + 1);
        Path target = properties.imagesDir().resolve(name).normalize();

        // EN: Refuse to touch anything that escaped the images directory.
        // VI: Từ chối động vào bất cứ thứ gì nằm ngoài thư mục ảnh.
        if (!target.startsWith(properties.imagesDir().normalize())) {
            log.warn("Refusing to delete a path outside the images directory: {}", publicUrl);
            return;
        }

        try {
            Files.deleteIfExists(target);
        } catch (IOException ex) {
            log.warn("Could not delete image file {}: {}", name, ex.getMessage());
        }
    }

    /**
     * EN: Content-Type is sent by the client, so it is a claim, not a fact. The first bytes are checked
     *     against it — an executable renamed to .png announces itself here.
     * VI: Content-Type do client gửi nên đó là lời khai, không phải sự thật. Vài byte đầu được kiểm lại —
     *     một file thực thi đổi tên thành .png sẽ lộ ra ở đây.
     */
    private String detectType(MultipartFile file) {
        String claimed = file.getContentType();

        try (InputStream in = file.getInputStream()) {
            byte[] header = in.readNBytes(12);
            String actual = MagicBytes.imageTypeOf(header);

            if (actual == null) {
                throw new BusinessException(ErrorCode.IMAGE_INVALID, "This file is not an image");
            }

            if (claimed != null && !actual.equals(claimed)) {
                log.debug("Upload claimed {} but looks like {}", claimed, actual);
            }

            return actual;

        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.IMAGE_INVALID, "The uploaded file could not be read");
        }
    }
}
