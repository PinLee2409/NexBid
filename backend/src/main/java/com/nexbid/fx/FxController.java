package com.nexbid.fx;

import java.time.Duration;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.nexbid.common.response.ApiResponse;

/**
 * EN: The reference rates, public: a visitor may want prices in dollars before signing in.
 * VI: Tỷ giá tham khảo, công khai: khách có thể muốn xem giá bằng đô la trước khi đăng nhập.
 */
@RestController
public class FxController {

    private final ExchangeRates rates;

    FxController(ExchangeRates rates) {
        this.rates = rates;
    }

    @GetMapping("/api/fx")
    public ResponseEntity<ApiResponse<ExchangeRates.Rates>> current() {
        return ResponseEntity.ok().cacheControl(CacheControl.maxAge(Duration.ofHours(1)).cachePublic())
                .body(ApiResponse.of(rates.current()));
    }
}
