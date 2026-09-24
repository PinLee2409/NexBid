package com.nexbid.product.storage;

import java.nio.file.Path;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * EN: Where uploaded images live and how big they may be.
 * VI: Ảnh tải lên nằm ở đâu và được phép nặng tới mức nào.
 *
 * <p>EN: The directory is outside the source tree on purpose — uploads are data, not code, and must never
 *     end up in a commit.
 * <p>VI: Thư mục nằm ngoài cây mã nguồn là có chủ ý — ảnh tải lên là dữ liệu chứ không phải mã, tuyệt đối
 *     không được lọt vào commit.
 */
@ConfigurationProperties(prefix = "nexbid.storage")
public record StorageProperties(Path imagesDir, long maxImageBytes, String publicPath) {

    public StorageProperties {
        imagesDir = imagesDir == null ? Path.of("var", "images") : imagesDir;
        maxImageBytes = maxImageBytes <= 0 ? 5L * 1024 * 1024 : maxImageBytes;
        publicPath = publicPath == null || publicPath.isBlank() ? "/media/products" : publicPath;
    }
}
