package com.nexbid.auction;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.RedisStringCommands.SetOption;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.types.Expiration;
import org.springframework.stereotype.Component;

import com.nexbid.auction.entity.Auction;
import com.nexbid.product.ProductImageService;
import com.nexbid.product.ProductService;
import com.nexbid.product.ProductView;
import com.nexbid.user.UserService;

import tools.jackson.databind.ObjectMapper;

/**
 * EN: Lot cards in Redis (guide §34, spec §20.1). Redis is never the source of truth: a miss, a broken
 *     entry or Redis being down all fall back to the database, and the page is the same either way.
 * VI: Thẻ lô trong Redis (guide §34, spec §20.1). Redis không bao giờ là nguồn sự thật: thiếu dữ liệu, dữ
 *     liệu hỏng hay Redis sập đều quay về database, và trang hiển thị vẫn y hệt.
 */
@Component
class LotCache {

    private static final Logger log = LoggerFactory.getLogger(LotCache.class);

    // EN: The version in the key lets a new card shape ignore old entries instead of misreading them.
    // VI: Phiên bản trong khoá giúp hình dạng thẻ mới bỏ qua dữ liệu cũ thay vì đọc sai.
    private static final String PREFIX = "nexbid:v1:lot:";

    private final StringRedisTemplate redis;
    private final ObjectMapper json;
    private final ProductService products;
    private final ProductImageService images;
    private final UserService users;
    private final Duration ttl;
    private final Duration retryAfter;

    /** EN: While in the future, Redis is not asked at all. / VI: Khi mốc này còn ở tương lai thì không hỏi Redis nữa. */
    private volatile Instant quietUntil = Instant.EPOCH;

    LotCache(
            StringRedisTemplate redis,
            ObjectMapper json,
            ProductService products,
            ProductImageService images,
            UserService users,
            @Value("${nexbid.cache.lot-ttl}") Duration ttl,
            @Value("${nexbid.cache.retry-after}") Duration retryAfter) {

        this.redis = redis;
        this.json = json;
        this.products = products;
        this.images = images;
        this.users = users;
        this.ttl = ttl;
        this.retryAfter = retryAfter;
    }

    static String keyOf(UUID auctionId) {
        return PREFIX + auctionId;
    }

    /**
     * EN: Cards for the given lots, one Redis round trip for the lot, one batch of queries for the misses.
     *     A lot whose product is gone gets no card.
     * VI: Thẻ cho các lô được đưa vào: một lượt hỏi Redis cho cả nhóm, một đợt truy vấn cho phần còn thiếu.
     *     Lô mà sản phẩm đã mất thì không có thẻ.
     */
    Map<UUID, LotCard> cardsFor(Collection<Auction> lots) {
        Map<UUID, LotCard> cards = new HashMap<>(read(lots));

        List<Auction> missing = lots.stream().filter(lot -> !cards.containsKey(lot.getId())).toList();
        if (!missing.isEmpty()) {
            Map<UUID, LotCard> built = build(missing);
            write(built);
            cards.putAll(built);
        }
        return cards;
    }

    /** EN: Drops a lot's card so the next read rebuilds it. / VI: Bỏ thẻ của một lô để lần đọc sau dựng lại. */
    void evict(Collection<UUID> auctionIds) {
        if (auctionIds.isEmpty() || resting()) {
            return;
        }
        try {
            redis.delete(auctionIds.stream().map(LotCache::keyOf).toList());
        } catch (RuntimeException ex) {
            rest(ex);
        }
    }

    private Map<UUID, LotCard> read(Collection<Auction> lots) {
        if (lots.isEmpty() || resting()) {
            return Map.of();
        }

        List<Auction> ordered = List.copyOf(lots);
        List<String> values;
        try {
            values = redis.opsForValue().multiGet(ordered.stream().map(lot -> keyOf(lot.getId())).toList());
        } catch (RuntimeException ex) {
            rest(ex);
            return Map.of();
        }

        Map<UUID, LotCard> found = new HashMap<>();
        for (int i = 0; values != null && i < ordered.size(); i++) {
            String value = values.get(i);
            if (value == null) {
                continue;
            }
            try {
                found.put(ordered.get(i).getId(), json.readValue(value, LotCard.class));
            } catch (RuntimeException ex) {
                // EN: A card that no longer reads is a miss, not an error. / VI: Thẻ không đọc được là thiếu dữ liệu, không phải lỗi.
                log.debug("Ignoring an unreadable lot card for {}", ordered.get(i).getId());
            }
        }
        return found;
    }

    private void write(Map<UUID, LotCard> cards) {
        if (cards.isEmpty() || resting()) {
            return;
        }
        try {
            Expiration expiry = Expiration.from(ttl);
            redis.executePipelined((RedisCallback<Object>) connection -> {
                cards.forEach((auctionId, card) -> connection.stringCommands().set(
                        keyOf(auctionId).getBytes(StandardCharsets.UTF_8),
                        json.writeValueAsBytes(card),
                        expiry,
                        SetOption.upsert()));
                return null;
            });
        } catch (RuntimeException ex) {
            rest(ex);
        }
    }

    private Map<UUID, LotCard> build(List<Auction> lots) {
        List<UUID> productIds = lots.stream().map(Auction::getProductId).distinct().toList();
        Map<UUID, ProductView> productsById = products.findAllById(productIds);
        var imagesByProduct = images.listPublic(productIds);
        Map<UUID, String> sellerNames = users.namesOf(lots.stream().map(Auction::getSellerId).distinct().toList());

        Map<UUID, LotCard> built = new HashMap<>();
        for (Auction lot : lots) {
            ProductView product = productsById.get(lot.getProductId());
            if (product == null) {
                continue;
            }
            built.put(lot.getId(), new LotCard(
                    product.id(),
                    product.name(),
                    product.description(),
                    product.condition().name(),
                    product.category(),
                    new ArrayList<>(imagesByProduct.getOrDefault(product.id(), List.of())),
                    lot.getSellerId(),
                    sellerNames.getOrDefault(lot.getSellerId(), "Unknown seller")));
        }
        return built;
    }

    private boolean resting() {
        return Instant.now().isBefore(quietUntil);
    }

    /**
     * EN: Redis failed: carry on from the database and stop asking for a while, so an outage costs one
     *     timeout rather than one per request.
     * VI: Redis lỗi: tiếp tục bằng database và thôi hỏi một lúc, để sự cố chỉ tốn một lần timeout chứ không
     *     phải mỗi request một lần.
     */
    private void rest(RuntimeException ex) {
        quietUntil = Instant.now().plus(retryAfter);
        log.warn("Redis unavailable, serving lots from the database for {}: {}", retryAfter, ex.getMessage());
    }
}
