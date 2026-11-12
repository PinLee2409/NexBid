package com.nexbid.support;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import tools.jackson.databind.ObjectMapper;

/**
 * EN: Plays VNPay in tests: starts a checkout the way the browser does, then sends back what VNPay would — a reply
 *     signed with the local hash secret. Its own HMAC code, written from VNPay's documentation, so a mistake in the
 *     app's signer cannot hide behind the same mistake here.
 * VI: Đóng vai VNPay trong test: bắt đầu checkout như trình duyệt, rồi gửi lại thứ VNPay sẽ gửi — một phản hồi ký bằng
 *     hash secret local. Có code HMAC riêng, viết theo tài liệu VNPay, để lỗi trong bộ ký của app không thể núp sau
 *     cùng lỗi đó ở đây.
 */
public final class VnpayTestSupport {

    /** EN: application.yml's local placeholder. / VI: Giá trị giữ chỗ local trong application.yml. */
    public static final String SECRET = "change-me-local-vnpay-secret";

    /** EN: VNPay's codes: paid, insufficient funds, cancelled by the buyer. / VI: Mã của VNPay: đã trả, không đủ tiền, người mua huỷ. */
    public static final String PAID = "00";
    public static final String DECLINED = "51";
    public static final String CANCELLED = "24";

    private static final ObjectMapper JSON = new ObjectMapper();

    private VnpayTestSupport() {
    }

    /** EN: Checkout needs a delivery address; tests that are not about it get a standard one. / VI: Checkout cần địa chỉ; test không liên quan thì dùng địa chỉ chuẩn. */
    public static void saveAddress(MockMvc mockMvc, String token) throws Exception {
        mockMvc.perform(put("/api/users/me/address")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"recipient":"Test Buyer","phone":"0901234567","line":"12 Nguyen Hue",
                         "ward":"Ben Nghe","district":"District 1","province":"Ho Chi Minh City"}
                        """));
    }

    public static ResultActions checkout(MockMvc mockMvc, String token, String paymentId) throws Exception {
        saveAddress(mockMvc, token);
        return mockMvc.perform(post("/api/payments/" + paymentId + "/vnpay")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"locale\":\"en\"}"));
    }

    /**
     * EN: Checkout, then VNPay's answer through the return URL. "SUCCESS" pays; anything else is a declined card. When
     *     the checkout itself is refused (not the owner, already paid, expired), that refusal is what comes back.
     * VI: Checkout, rồi câu trả lời của VNPay qua return URL. "SUCCESS" là trả được; còn lại là thẻ bị từ chối. Khi
     *     chính checkout bị từ chối (không phải chủ, đã trả, hết hạn), lời từ chối đó là thứ được trả về.
     */
    public static ResultActions pay(MockMvc mockMvc, String token, String paymentId, String outcome) throws Exception {
        ResultActions started = checkout(mockMvc, token, paymentId);
        if (started.andReturn().getResponse().getStatus() != 200) {
            return started;
        }
        Map<String, String> reply = reply(paymentUrlOf(started), "SUCCESS".equals(outcome) ? PAID : DECLINED);
        return mockMvc.perform(withParams(get("/api/payments/vnpay/return"), reply));
    }

    public static String paymentUrlOf(ResultActions checkout) throws Exception {
        return JSON.readTree(checkout.andReturn().getResponse().getContentAsString())
                .get("data").get("paymentUrl").asString();
    }

    /** EN: The fields the app put in its redirect URL. / VI: Các trường mà app đặt trong URL chuyển hướng. */
    public static Map<String, String> fieldsOf(String paymentUrl) {
        Map<String, String> fields = new LinkedHashMap<>();
        for (String pair : URI.create(paymentUrl).getRawQuery().split("&")) {
            int eq = pair.indexOf('=');
            fields.put(URLDecoder.decode(pair.substring(0, eq), StandardCharsets.UTF_8),
                    URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8));
        }
        return fields;
    }

    /** EN: VNPay's signed reply to that checkout. / VI: Phản hồi có chữ ký của VNPay cho checkout đó. */
    public static Map<String, String> reply(String paymentUrl, String responseCode) {
        Map<String, String> sent = fieldsOf(paymentUrl);
        Map<String, String> reply = new TreeMap<>();
        reply.put("vnp_TmnCode", sent.get("vnp_TmnCode"));
        reply.put("vnp_Amount", sent.get("vnp_Amount"));
        reply.put("vnp_TxnRef", sent.get("vnp_TxnRef"));
        reply.put("vnp_OrderInfo", sent.get("vnp_OrderInfo"));
        reply.put("vnp_BankCode", "NCB");
        reply.put("vnp_PayDate", sent.get("vnp_CreateDate"));
        reply.put("vnp_ResponseCode", responseCode);
        reply.put("vnp_TransactionStatus", responseCode);
        reply.put("vnp_TransactionNo", "1" + sent.get("vnp_TxnRef").hashCode());
        return signed(reply);
    }

    /** EN: Re-signs fields after a test changed them, or signs new ones. / VI: Ký lại các trường sau khi test sửa chúng, hoặc ký trường mới. */
    public static Map<String, String> signed(Map<String, String> fields) {
        Map<String, String> unsigned = new TreeMap<>(fields);
        unsigned.remove("vnp_SecureHash");
        unsigned.remove("vnp_SecureHashType");
        String data = unsigned.entrySet().stream()
                .map(field -> URLEncoder.encode(field.getKey(), StandardCharsets.US_ASCII) + "="
                        + URLEncoder.encode(field.getValue(), StandardCharsets.US_ASCII))
                .collect(Collectors.joining("&"));
        Map<String, String> result = new TreeMap<>(unsigned);
        result.put("vnp_SecureHash", hmac(data));
        return result;
    }

    public static MockHttpServletRequestBuilder withParams(MockHttpServletRequestBuilder request, Map<String, String> fields) {
        fields.forEach(request::param);
        return request;
    }

    private static String hmac(String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA512");
            mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA512"));
            return HexFormat.of().formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }
}
