package com.nexbid.order.shipping;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
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

    private final RestClient http;
    private final String token;
    private final String shopId;

    GhnClient(String baseUrl, String token, String shopId) {
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

    /** EN: The parcel's status word, e.g. "delivering" or "delivered". / VI: Trạng thái của kiện, ví dụ "delivering" hay "delivered". */
    String status(String orderCode) {
        return post("/v2/shipping-order/detail", Map.of("order_code", orderCode), false).get("status").asString();
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
