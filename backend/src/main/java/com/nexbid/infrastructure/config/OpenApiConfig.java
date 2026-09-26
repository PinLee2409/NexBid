package com.nexbid.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;

/**
 * EN: The title of the API docs and how to sign in from them: paste the accessToken from
 *     POST /api/auth/login into "Authorize", and every call from Swagger UI carries it.
 * VI: Tiêu đề của tài liệu API và cách đăng nhập từ đó: dán accessToken lấy từ POST /api/auth/login vào
 *     "Authorize", mọi lời gọi từ Swagger UI sẽ mang theo nó.
 */
@Configuration
public class OpenApiConfig {

    private static final String BEARER = "bearer";

    @Bean
    OpenAPI nexbidOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("NexBid API")
                        .version("v1")
                        .description("Real-time auctions: listing, approval, bidding, payment. "
                                + "Errors follow one shape: {success, code, message, timestamp, details}."))
                .components(new Components().addSecuritySchemes(BEARER, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER));
    }
}
