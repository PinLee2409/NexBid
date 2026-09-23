package com.nexbid;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.modulith.Modulithic;

/**
 * EN: Modular monolith (spec §44) — each sub-package of com.nexbid is a module, boundaries checked at build time.
 * VI: Monolith theo module (spec §44) — mỗi package con của com.nexbid là một module, ranh giới được kiểm lúc build.
 */
@SpringBootApplication
// EN: Shared modules everything may use: the response contract and technical config.
// VI: Module dùng chung cho tất cả: hợp đồng response và cấu hình kỹ thuật.
@Modulithic(sharedModules = { "common", "infrastructure" })
public class NexbidApplication {

    public static void main(String[] args) {
        SpringApplication.run(NexbidApplication.class, args);
    }

}
