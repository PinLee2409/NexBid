package com.nexbid.order.shipping;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Set;

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

    static final String CARRIER = "GHN";
    private static final Set<String> DELIVERED = Set.of("delivered");
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

    /** EN: The fee for one parcel, between GHN districts. / VI: Phí cho một kiện, giữa các quận của GHN. */
    public BigDecimal fee(GhnClient.FeeQuery query) {
        requireEnabled();
        return client.fee(query);
    }

    public List<GhnClient.Area> provinces() {
        requireEnabled();
        return client.provinces();
    }

    public List<GhnClient.Area> districts(int provinceId) {
        requireEnabled();
        return client.districts(provinceId);
    }

    public List<GhnClient.Area> wards(int districtId) {
        requireEnabled();
        return client.wards(districtId);
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
