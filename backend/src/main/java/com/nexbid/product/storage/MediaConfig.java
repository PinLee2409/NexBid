package com.nexbid.product.storage;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * EN: Serves stored images back over HTTP, so an uploaded file has a URL the browser can load.
 * VI: Phục vụ lại ảnh đã lưu qua HTTP, để file tải lên có một URL trình duyệt nạp được.
 */
@Configuration
// EN: Every @WebMvcTest slice loads WebMvcConfigurer beans but not the properties scan, so this class
//     has to bring its own settings with it.
// VI: Mọi test lát @WebMvcTest đều nạp bean WebMvcConfigurer nhưng không chạy properties scan, nên lớp này
//     phải tự mang theo cấu hình của mình.
@EnableConfigurationProperties(StorageProperties.class)
public class MediaConfig implements WebMvcConfigurer {

    private final StorageProperties properties;

    public MediaConfig(StorageProperties properties) {
        this.properties = properties;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler(properties.publicPath() + "/**")
                .addResourceLocations(properties.imagesDir().toUri().toString());
    }
}
