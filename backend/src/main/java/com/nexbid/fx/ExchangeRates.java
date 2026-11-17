package com.nexbid.fx;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import tools.jackson.databind.JsonNode;

/**
 * EN: Reference exchange rates for showing a price in another currency beside the đồng. Fetched at most once a day
 *     from a free public feed (no key, nothing about anyone is sent) and kept in {@code exchange_rates}; if the feed
 *     is down the last rates stay, and before the first fetch built-in rates stand in. Nothing is ever charged in
 *     these currencies — payment stays in VND.
 * VI: Tỷ giá tham khảo để hiện giá bằng tiền khác bên cạnh tiền đồng. Lấy tối đa mỗi ngày một lần từ một nguồn công khai
 *     miễn phí (không cần key, không gửi gì về ai) và lưu trong {@code exchange_rates}; nguồn hỏng thì tỷ giá cũ vẫn
 *     dùng, còn trước lần lấy đầu tiên thì dùng tỷ giá có sẵn. Không bao giờ thu tiền theo các loại tiền này — thanh
 *     toán vẫn bằng VND.
 */
@Service
public class ExchangeRates {

    private static final Logger log = LoggerFactory.getLogger(ExchangeRates.class);

    /** EN: The currencies offered, in menu order. / VI: Các loại tiền được hỗ trợ, theo thứ tự trong menu. */
    public static final List<String> CURRENCIES = List.of("USD", "EUR");

    /** EN: Stand-ins until the first fetch; roughly late-2026 levels. / VI: Dùng tạm tới lần lấy đầu; mức khoảng cuối 2026. */
    private static final Map<String, BigDecimal> FALLBACK = Map.of(
            "USD", new BigDecimal("26300"),
            "EUR", new BigDecimal("30500"));

    public record Rate(String currency, BigDecimal vndPer) {
    }

    public record Rates(String base, List<Rate> rates, String source, Instant updatedAt) {
    }

    private final JdbcTemplate jdbc;
    private final RestClient http;
    private final String feedUrl;
    private final boolean enabled;
    private final Duration maxAge;

    ExchangeRates(JdbcTemplate jdbc,
            @Value("${nexbid.fx.feed-url:https://open.er-api.com/v6/latest/USD}") String feedUrl,
            @Value("${nexbid.fx.enabled:true}") boolean enabled,
            @Value("${nexbid.fx.max-age:PT20H}") Duration maxAge) {
        this.jdbc = jdbc;
        this.feedUrl = feedUrl;
        this.enabled = enabled;
        this.maxAge = maxAge;
        var timeouts = new SimpleClientHttpRequestFactory();
        timeouts.setConnectTimeout(Duration.ofSeconds(5));
        timeouts.setReadTimeout(Duration.ofSeconds(10));
        this.http = RestClient.builder().requestFactory(timeouts).build();
    }

    /** EN: What the site shows now. / VI: Những gì site đang hiển thị. */
    public Rates current() {
        List<Rate> stored = jdbc.query("SELECT currency, vnd_per FROM exchange_rates",
                (rs, n) -> new Rate(rs.getString("currency"), rs.getBigDecimal("vnd_per")));
        Instant updatedAt = lastFetch();
        String source = stored.isEmpty() ? "fallback"
                : jdbc.queryForObject("SELECT max(source) FROM exchange_rates", String.class);
        List<Rate> rates = new ArrayList<>();
        for (String currency : CURRENCIES) {
            BigDecimal value = stored.stream().filter(rate -> rate.currency().equals(currency))
                    .map(Rate::vndPer).findFirst().orElse(FALLBACK.get(currency));
            rates.add(new Rate(currency, value.setScale(2, RoundingMode.HALF_UP)));
        }
        return new Rates("VND", rates, source, updatedAt);
    }

    /**
     * EN: Checks every hour, fetches only when the stored rates are older than {@code maxAge}: once a day in practice.
     * VI: Kiểm tra mỗi giờ, chỉ lấy khi tỷ giá đã lưu cũ hơn {@code maxAge}: thực tế là mỗi ngày một lần.
     */
    @Scheduled(initialDelayString = "${nexbid.fx.initial-delay:PT15S}", fixedDelayString = "${nexbid.fx.check-every:PT1H}")
    void refreshIfStale() {
        if (!enabled) {
            return;
        }
        Instant last = lastFetch();
        if (last != null && last.isAfter(Instant.now().minus(maxAge))) {
            return;
        }
        try {
            JsonNode body = http.get().uri(feedUrl).retrieve().body(JsonNode.class);
            store(body, Instant.now());
        } catch (RestClientException | IllegalStateException ex) {
            log.warn("Exchange rates not refreshed: {}", ex.getMessage());
        }
    }

    /**
     * EN: Reads a feed answer in the open.er-api shape: {@code {"result":"success","rates":{"VND":…,"EUR":…}}} with USD
     *     as the base. Each currency's đồng value is VND/that currency.
     * VI: Đọc câu trả lời theo dạng của open.er-api: {@code {"result":"success","rates":{"VND":…,"EUR":…}}} với gốc USD.
     *     Giá trị theo đồng của mỗi loại tiền là VND/loại tiền đó.
     */
    public void store(JsonNode body, Instant at) {
        if (body == null || !"success".equals(body.path("result").asString(""))) {
            throw new IllegalStateException("feed did not answer success");
        }
        JsonNode rates = body.path("rates");
        double vnd = rates.path("VND").asDouble(0);
        if (vnd <= 0) {
            throw new IllegalStateException("feed has no VND rate");
        }
        for (String currency : CURRENCIES) {
            double perUsd = rates.path(currency).asDouble(0);
            if (perUsd <= 0) {
                continue;
            }
            BigDecimal vndPer = BigDecimal.valueOf(vnd / perUsd).setScale(4, RoundingMode.HALF_UP);
            jdbc.update("""
                    INSERT INTO exchange_rates (currency, vnd_per, source, fetched_at) VALUES (?, ?, ?, ?)
                    ON CONFLICT (currency) DO UPDATE SET vnd_per = EXCLUDED.vnd_per, source = EXCLUDED.source,
                                                         fetched_at = EXCLUDED.fetched_at
                    """, currency, vndPer, "open.er-api.com", Timestamp.from(at));
        }
        log.info("Exchange rates refreshed: 1 USD = {} VND", Math.round(vnd));
    }

    private Instant lastFetch() {
        Timestamp at = jdbc.queryForObject("SELECT max(fetched_at) FROM exchange_rates", Timestamp.class);
        return at == null ? null : at.toInstant();
    }
}
