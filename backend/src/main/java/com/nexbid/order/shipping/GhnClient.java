package com.nexbid.order.shipping;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.nexbid.common.exception.BusinessException;
import com.nexbid.common.exception.ErrorCode;

import tools.jackson.databind.JsonNode;

/**
 * EN: The few GHN (Giao Hàng Nhanh) calls NexBid makes: the fee for a parcel, a parcel's status, and the address lists
 *     GHN's ids come from. Every call carries the shop's Token (and ShopId where GHN wants it), and gives up after 10
 *     seconds. GHN answers {code, message, data}; anything but code 200 is an error.
 * VI: Vài lời gọi GHN (Giao Hàng Nhanh) mà NexBid dùng: phí của một kiện, trạng thái một kiện, và danh sách địa chỉ để lấy
 *     id của GHN. Mọi lời gọi mang Token của shop (và ShopId khi GHN cần), và dừng sau 10 giây. GHN trả {code, message,
 *     data}; khác code 200 là lỗi.
 */
class GhnClient {

    /** EN: A province, district or ward with GHN's id or code. / VI: Tỉnh, quận hoặc phường kèm id hay mã của GHN. */
    record Area(String id, String name) {
    }

    /** EN: What a parcel costs to send, and GHN's service. / VI: Chi phí gửi một kiện, và dịch vụ của GHN. */
    record FeeQuery(int fromDistrictId, int toDistrictId, String toWardCode, int weightGrams, int lengthCm, int widthCm,
            int heightCm, long insuranceValue) {
    }

    /**
     * EN: A parcel for GHN to pick up from the seller and deliver to the buyer. The shop (NexBid) pays GHN; nothing is
     *     collected on delivery. {@code clientOrderCode} is NexBid's order id, which GHN keeps unique per shop.
     * VI: Một kiện để GHN lấy từ người bán và giao cho người mua. Shop (NexBid) trả tiền cho GHN; không thu hộ khi giao.
     *     {@code clientOrderCode} là id đơn của NexBid, GHN giữ duy nhất trong mỗi shop.
     */
    record Booking(String clientOrderCode, String fromName, String fromPhone, String fromAddress, String fromWard,
            String fromDistrict, String fromProvince, String toName, String toPhone, String toAddress, int toDistrictId,
            String toWardCode, String item, int weightGrams, int lengthCm, int widthCm, int heightCm,
            long insuranceValue) {
    }

    /** EN: GHN's order code and what GHN charges the shop. / VI: Mã đơn GHN và số GHN thu của shop. */
    record Booked(String orderCode, BigDecimal fee) {
    }

    private final RestClient http;
    private final String baseUrl;
    private final String token;
    private final String shopId;

    GhnClient(String baseUrl, String token, String shopId) {
        this.baseUrl = baseUrl;
        var timeouts = new SimpleClientHttpRequestFactory();
        timeouts.setConnectTimeout(Duration.ofSeconds(5));
        timeouts.setReadTimeout(Duration.ofSeconds(10));
        this.http = RestClient.builder().baseUrl(baseUrl).requestFactory(timeouts).build();
        this.token = token;
        this.shopId = shopId;
    }

    /** EN: GHN's standard service (service_type_id 2), in VND. / VI: Dịch vụ chuẩn của GHN (service_type_id 2), tính bằng VND. */
    BigDecimal fee(FeeQuery query) {
        JsonNode data = post("/v2/shipping-order/fee", Map.of(
                "service_type_id", 2,
                "from_district_id", query.fromDistrictId(),
                "to_district_id", query.toDistrictId(),
                "to_ward_code", query.toWardCode(),
                "weight", query.weightGrams(),
                "length", query.lengthCm(),
                "width", query.widthCm(),
                "height", query.heightCm(),
                "insurance_value", query.insuranceValue()), true);
        return data.get("total").decimalValue();
    }

    /**
     * EN: Books the pickup. GHN's own refusals (a phone it does not accept, an address it cannot reach) come back with
     *     GHN's message as GHN_REFUSED; GHN being down is SHIPPING_UNAVAILABLE.
     * VI: Đặt lấy hàng. GHN từ chối (số điện thoại không nhận, địa chỉ không tới được) trả về kèm lời của GHN dưới mã
     *     GHN_REFUSED; GHN sập là SHIPPING_UNAVAILABLE.
     */
    Booked book(Booking booking) {
        Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("payment_type_id", 1);
        body.put("required_note", "CHOXEMHANGKHONGTHU");
        body.put("client_order_code", booking.clientOrderCode());
        body.put("from_name", booking.fromName());
        body.put("from_phone", booking.fromPhone());
        body.put("from_address", booking.fromAddress());
        body.put("from_ward_name", booking.fromWard());
        body.put("from_district_name", booking.fromDistrict());
        body.put("from_province_name", booking.fromProvince());
        body.put("to_name", booking.toName());
        body.put("to_phone", booking.toPhone());
        body.put("to_address", booking.toAddress());
        body.put("to_ward_code", booking.toWardCode());
        body.put("to_district_id", booking.toDistrictId());
        body.put("cod_amount", 0);
        body.put("weight", booking.weightGrams());
        body.put("length", booking.lengthCm());
        body.put("width", booking.widthCm());
        body.put("height", booking.heightCm());
        body.put("insurance_value", booking.insuranceValue());
        body.put("service_type_id", 2);
        body.put("items", List.of(Map.of("name", booking.item(), "quantity", 1, "weight", booking.weightGrams())));

        JsonNode reply;
        try {
            reply = http.post().uri("/v2/shipping-order/create").header("Token", token).header("ShopId", shopId)
                    .contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON).body(body)
                    .exchange((request, response) -> response.getStatusCode().is5xxServerError()
                            ? null
                            : response.bodyTo(JsonNode.class));
        } catch (RestClientException ex) {
            throw new BusinessException(ErrorCode.SHIPPING_UNAVAILABLE, "GHN could not be reached: " + ex.getMessage());
        }
        if (reply == null) {
            throw new BusinessException(ErrorCode.SHIPPING_UNAVAILABLE, "GHN did not answer");
        }
        JsonNode data = reply.path("data");
        if (reply.path("code").asInt() != 200 || !data.path("order_code").isString()) {
            throw new BusinessException(ErrorCode.GHN_REFUSED, "GHN refused the order: " + reply.path("message").asString(""));
        }
        return new Booked(data.path("order_code").asString(), data.path("total_fee").decimalValue());
    }

    /**
     * EN: A link to GHN's printable A5 label for an order, valid for a while.
     * VI: Link tới phiếu A5 in được của GHN cho một đơn, còn hiệu lực một lúc.
     */
    String labelUrl(String orderCode) {
        String printToken = post("/v2/a5/gen-token", Map.of("order_codes", List.of(orderCode)), false)
                .path("token").asString();
        return baseUrl.replaceFirst("/shiip/public-api/?$", "") + "/a5/public-api/printA5?token=" + printToken;
    }

    /**
     * EN: Cancels a GHN order not yet picked up. GHN answers per order code; false means it refused (already picked up,
     *     say), with its reason.
     * VI: Huỷ một đơn GHN chưa được lấy hàng. GHN trả lời theo từng mã đơn; false nghĩa là từ chối (đã lấy hàng chẳng
     *     hạn), kèm lý do.
     */
    record Cancelled(boolean done, String message) {
    }

    Cancelled cancel(String orderCode) {
        JsonNode data = post("/v2/switch-status/cancel", Map.of("order_codes", List.of(orderCode)), true);
        for (JsonNode one : data) {
            if (orderCode.equals(one.path("order_code").asString())) {
                return new Cancelled(one.path("result").asBoolean(false), one.path("message").asString(""));
            }
        }
        return new Cancelled(false, "GHN did not answer for " + orderCode);
    }

    /** EN: The parcel's status word, e.g. "delivering" or "delivered". / VI: Trạng thái của kiện, ví dụ "delivering" hay "delivered". */
    String status(String orderCode) {
        return post("/v2/shipping-order/detail", Map.of("order_code", orderCode), false).get("status").asString();
    }

    /**
     * EN: What GHN knows of an order code: its status, MISSING when GHN says there is no such order, or UNKNOWN when GHN
     *     could not be asked (down, slow, a bad token) — not the seller's fault, so callers let it through.
     * VI: GHN biết gì về một mã đơn: trạng thái của nó, MISSING khi GHN báo không có đơn này, hoặc UNKNOWN khi không hỏi
     *     được GHN (sập, chậm, token sai) — không phải lỗi người bán, nên bên gọi cho qua.
     */
    record Lookup(String status, boolean missing) {
        static final Lookup MISSING = new Lookup(null, true);
        static final Lookup UNKNOWN = new Lookup(null, false);

        boolean found() {
            return status != null;
        }
    }

    Lookup lookup(String orderCode) {
        JsonNode reply;
        try {
            reply = http.post().uri("/v2/shipping-order/detail").header("Token", token)
                    .contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON)
                    .body(Map.of("order_code", orderCode))
                    .exchange((request, response) -> response.getStatusCode().is5xxServerError()
                            ? null
                            : response.bodyTo(JsonNode.class));
        } catch (RestClientException ex) {
            return Lookup.UNKNOWN;
        }
        if (reply == null) {
            return Lookup.UNKNOWN;
        }
        if (reply.path("code").asInt() == 200 && reply.path("data").path("status").isString()) {
            return new Lookup(reply.path("data").path("status").asString(), false);
        }
        String message = reply.path("message").asString("").toLowerCase(Locale.ROOT);
        boolean missing = reply.path("code").asInt() == 400
                && (message.contains("không tồn tại") || message.contains("not exist") || message.contains("not found"));
        return missing ? Lookup.MISSING : Lookup.UNKNOWN;
    }

    List<Area> provinces() {
        return areas(get("/master-data/province"), "ProvinceID", "ProvinceName");
    }

    List<Area> districts(int provinceId) {
        return areas(post("/master-data/district", Map.of("province_id", provinceId), false), "DistrictID",
                "DistrictName");
    }

    List<Area> wards(int districtId) {
        return areas(post("/master-data/ward", Map.of("district_id", districtId), false), "WardCode", "WardName");
    }

    private JsonNode get(String path) {
        return unwrap(call(() -> http.get().uri(path).header("Token", token)
                .accept(MediaType.APPLICATION_JSON).retrieve().body(JsonNode.class)));
    }

    private JsonNode post(String path, Map<String, Object> body, boolean withShop) {
        return unwrap(call(() -> {
            var request = http.post().uri(path).header("Token", token).contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON);
            if (withShop) {
                request = request.header("ShopId", shopId);
            }
            return request.body(body).retrieve().body(JsonNode.class);
        }));
    }

    private static JsonNode call(java.util.function.Supplier<JsonNode> request) {
        try {
            return request.get();
        } catch (RestClientException ex) {
            throw new BusinessException(ErrorCode.SHIPPING_UNAVAILABLE, "GHN could not be reached: " + ex.getMessage());
        }
    }

    private static JsonNode unwrap(JsonNode reply) {
        if (reply == null || reply.path("code").asInt() != 200 || reply.get("data") == null) {
            String message = reply == null ? "no answer" : reply.path("message").asString("");
            throw new BusinessException(ErrorCode.SHIPPING_UNAVAILABLE, "GHN refused the request: " + message);
        }
        return reply.get("data");
    }

    private static List<Area> areas(JsonNode data, String idField, String nameField) {
        List<Area> found = new ArrayList<>();
        for (JsonNode node : data) {
            found.add(new Area(node.path(idField).asString(), node.path(nameField).asString()));
        }
        return found;
    }
}
