package com.nexbid.order.shipping;

import java.math.BigDecimal;
import java.text.Collator;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nexbid.common.exception.BusinessException;
import com.nexbid.common.exception.ErrorCode;
import com.nexbid.order.entity.Order;
import com.nexbid.order.repository.OrderRepository;

/**
 * EN: The GHN integration. Off until NEXBID_GHN_TOKEN and NEXBID_GHN_SHOP_ID are set; then sellers who ship with GHN
 *     ("GHN" as the carrier, GHN's order code as the tracking number) have their parcels followed — by GHN's webhook,
 *     and by a poll in case a call was missed. When GHN says "delivered", the order completes a few days later unless the
 *     buyer disputes (nexbid.order.complete-after-delivery). Fees and address lists are there for checkout to use.
 * VI: Tích hợp GHN. Tắt cho tới khi đặt NEXBID_GHN_TOKEN và NEXBID_GHN_SHOP_ID; khi đó người bán gửi bằng GHN ("GHN" là đơn
 *     vị vận chuyển, mã đơn GHN là mã vận đơn) được theo dõi kiện hàng — qua webhook của GHN, và qua một lượt hỏi định kỳ
 *     phòng khi lỡ webhook. Khi GHN báo "delivered", đơn tự hoàn tất sau vài ngày nếu người mua không khiếu nại
 *     (nexbid.order.complete-after-delivery). Phí ship và danh sách địa chỉ có sẵn để trang thanh toán dùng.
 */
@Service
public class GhnShipping {

    public static final String CARRIER = "GHN";
    private static final Set<String> DELIVERED = Set.of("delivered");
    // EN: GHN's lists barely change; fees for one lot and one district change with the price only.
    // VI: Danh sách của GHN gần như không đổi; phí cho một lô và một quận chỉ đổi theo giá.
    private static final Duration AREAS_FOR = Duration.ofHours(24);
    private static final Duration FEES_FOR = Duration.ofMinutes(30);
    // EN: The sandbox lists test places ("Hà Nội 02", "Test - Alert - Tỉnh - 001"); nobody lives there.
    // VI: Sandbox có các địa danh test ("Hà Nội 02", "Test - Alert - Tỉnh - 001"); không ai ở đó.
    private static final Pattern TEST_PLACE = Pattern.compile("(?i)^test\\b|\\s0\\d+$");

    private record Kept<T>(T value, Instant until) {
    }

    private final Map<String, Kept<List<GhnClient.Area>>> areas = new ConcurrentHashMap<>();
    private final Map<GhnClient.FeeQuery, Kept<BigDecimal>> fees = new ConcurrentHashMap<>();
    private static final Logger log = LoggerFactory.getLogger(GhnShipping.class);

    private final OrderRepository orders;
    private final GhnClient client;
    private final boolean enabled;
    private final String webhookSecret;

    @org.springframework.beans.factory.annotation.Autowired
    GhnShipping(OrderRepository orders,
            @Value("${nexbid.shipping.ghn.base-url}") String baseUrl,
            @Value("${nexbid.shipping.ghn.token}") String token,
            @Value("${nexbid.shipping.ghn.shop-id}") String shopId,
            @Value("${nexbid.shipping.ghn.webhook-secret}") String webhookSecret) {
        this(orders, new GhnClient(baseUrl, token, shopId), !token.isBlank() && !shopId.isBlank(), webhookSecret);
    }

    GhnShipping(OrderRepository orders, GhnClient client, boolean enabled, String webhookSecret) {
        this.orders = orders;
        this.client = client;
        this.enabled = enabled;
        this.webhookSecret = webhookSecret == null ? "" : webhookSecret;
    }

    public boolean enabled() {
        return enabled;
    }

    /** EN: Whether a typed carrier means GHN. / VI: Đơn vị vận chuyển người bán nhập có phải GHN không. */
    public static boolean isGhn(String carrier) {
        if (carrier == null) {
            return false;
        }
        String name = carrier.trim().toLowerCase(Locale.ROOT);
        return name.equals("ghn") || name.equals("giao hàng nhanh") || name.equals("giao hang nhanh");
    }

    public static boolean isDelivered(String status) {
        return status != null && DELIVERED.contains(status.trim().toLowerCase(Locale.ROOT));
    }

    /**
     * EN: Checks a GHN order code a seller typed. Returns its status when GHN knows it, empty when GHN is off or could not
     *     be asked, and refuses a code GHN has never heard of.
     * VI: Kiểm mã đơn GHN người bán nhập. Trả trạng thái khi GHN biết mã, rỗng khi GHN tắt hoặc không hỏi được, và từ chối
     *     mã GHN chưa từng nghe tới.
     */
    public Optional<String> verifyParcel(String orderCode) {
        if (!enabled || orderCode == null || orderCode.isBlank()) {
            return Optional.empty();
        }
        GhnClient.Lookup found = client.lookup(orderCode.trim());
        if (found.missing()) {
            throw new BusinessException(ErrorCode.TRACKING_NOT_FOUND, "GHN has no order " + orderCode.trim());
        }
        return Optional.ofNullable(found.status()).map(status -> status.trim().toLowerCase(Locale.ROOT));
    }

    /** EN: Books a pickup with GHN on the shop's account. / VI: Đặt GHN lấy hàng bằng tài khoản của shop. */
    GhnClient.Booked book(GhnClient.Booking booking) {
        requireEnabled();
        return client.book(booking);
    }

    /** EN: Where the seller prints GHN's label. / VI: Nơi người bán in phiếu GHN. */
    public String labelUrl(String orderCode) {
        requireEnabled();
        return client.labelUrl(orderCode);
    }

    /** EN: The fee for one parcel, between GHN districts; kept a while. / VI: Phí cho một kiện, giữa các quận của GHN; giữ một lúc. */
    public BigDecimal fee(GhnClient.FeeQuery query) {
        requireEnabled();
        return remembered(fees, query, FEES_FOR, () -> client.fee(query));
    }

    public List<GhnClient.Area> provinces() {
        requireEnabled();
        return remembered(areas, "provinces", AREAS_FOR, () -> sorted(client.provinces()));
    }

    public List<GhnClient.Area> districts(int provinceId) {
        requireEnabled();
        return remembered(areas, "districts:" + provinceId, AREAS_FOR, () -> sorted(client.districts(provinceId)));
    }

    public List<GhnClient.Area> wards(int districtId) {
        requireEnabled();
        return remembered(areas, "wards:" + districtId, AREAS_FOR, () -> sorted(client.wards(districtId)));
    }

    private static List<GhnClient.Area> sorted(List<GhnClient.Area> found) {
        Collator vietnamese = Collator.getInstance(Locale.forLanguageTag("vi"));
        return found.stream()
                .filter(area -> !TEST_PLACE.matcher(area.name()).find())
                .sorted(Comparator.comparing(GhnClient.Area::name, vietnamese))
                .toList();
    }

    private static <K, V> V remembered(Map<K, Kept<V>> store, K key, Duration keep, Supplier<V> load) {
        Instant now = Instant.now();
        Kept<V> kept = store.get(key);
        if (kept != null && kept.until().isAfter(now)) {
            return kept.value();
        }
        V value = load.get();
        // EN: Bounded: an hour of distinct fee queries cannot grow without end. / VI: Có giới hạn: phí không phình mãi.
        if (store.size() > 5_000) {
            store.entrySet().removeIf(entry -> !entry.getValue().until().isAfter(now));
        }
        store.put(key, new Kept<>(value, now.plus(keep)));
        return value;
    }

    /** EN: Whether a webhook call carries the configured secret. / VI: Lời gọi webhook có mang đúng secret đã cấu hình không. */
    boolean acceptsWebhook(String secret) {
        return enabled && !webhookSecret.isBlank() && webhookSecret.equals(secret);
    }

    /**
     * EN: Records what GHN says about one of its parcels. Unknown codes are ignored: the parcel may belong to a shipment
     *     outside NexBid. Returns whether an order was updated.
     * VI: Ghi lại điều GHN báo về một kiện của họ. Mã không biết thì bỏ qua: kiện có thể là hàng gửi ngoài NexBid. Trả về
     *     có đơn nào được cập nhật không.
     */
    @Transactional
    public boolean record(String orderCode, String status, Instant at) {
        if (orderCode == null || orderCode.isBlank() || status == null || status.isBlank()) {
            return false;
        }
        String word = status.trim().toLowerCase(Locale.ROOT);
        return orders.lockByParcel(CARRIER, orderCode.trim()).map(order -> {
            order.recordCarrierStatus(word, DELIVERED.contains(word), at);
            orders.save(order);
            return true;
        }).orElse(false);
    }

    /** EN: Asks GHN about every parcel still in transit; returns how many changed. / VI: Hỏi GHN về mọi kiện còn đang giao; trả về số kiện thay đổi. */
    public int refresh(int limit) {
        if (!enabled) {
            return 0;
        }
        int changed = 0;
        for (Order order : orders.inTransitWith(CARRIER, limit)) {
            try {
                String status = client.status(order.getTrackingNumber());
                if (!status.equalsIgnoreCase(String.valueOf(order.getShippingStatus()))
                        && record(order.getTrackingNumber(), status, Instant.now())) {
                    changed++;
                }
            } catch (BusinessException ex) {
                log.warn("GHN status for {} failed: {}", order.getTrackingNumber(), ex.getMessage());
            }
        }
        return changed;
    }

    private void requireEnabled() {
        if (!enabled) {
            throw new BusinessException(ErrorCode.SHIPPING_UNAVAILABLE, "GHN is not connected yet");
        }
    }
}
