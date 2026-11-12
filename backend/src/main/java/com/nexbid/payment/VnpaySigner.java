package com.nexbid.payment;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * EN: VNPay's signature (API 2.1.0): the vnp_ fields sorted by name, each name and value URL-encoded, joined as a
 *     query string, then HMAC-SHA512 with the merchant's hash secret, in hex. The redirect URL carries exactly that
 *     query string plus vnp_SecureHash; IPN and return calls are checked the same way with the hash fields left out.
 * VI: Chữ ký của VNPay (API 2.1.0): các trường vnp_ sắp theo tên, tên và giá trị đều URL-encode, nối thành query
 *     string, rồi HMAC-SHA512 với hash secret của merchant, dạng hex. URL chuyển hướng mang đúng query string đó kèm
 *     vnp_SecureHash; IPN và lời gọi trả về được kiểm theo cùng cách, bỏ các trường hash ra.
 */
final class VnpaySigner {

    static final String HASH = "vnp_SecureHash";
    static final String HASH_TYPE = "vnp_SecureHashType";

    private final byte[] secret;

    VnpaySigner(String hashSecret) {
        this.secret = hashSecret.getBytes(StandardCharsets.UTF_8);
    }

    /** EN: The query string to send, signature included. / VI: Query string cần gửi, đã kèm chữ ký. */
    String signedQuery(Map<String, String> fields) {
        String query = canonical(fields);
        return query + "&" + HASH + "=" + sign(query);
    }

    /** EN: Whether the fields VNPay sent carry a valid signature. / VI: Các trường VNPay gửi có chữ ký hợp lệ không. */
    boolean verify(Map<String, String> fields) {
        String given = fields.get(HASH);
        if (given == null || given.isBlank()) {
            return false;
        }
        Map<String, String> unsigned = new TreeMap<>(fields);
        unsigned.remove(HASH);
        unsigned.remove(HASH_TYPE);
        byte[] expected = sign(canonical(unsigned)).getBytes(StandardCharsets.US_ASCII);
        byte[] actual = given.toLowerCase().getBytes(StandardCharsets.US_ASCII);
        return MessageDigest.isEqual(expected, actual);
    }

    String sign(String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA512");
            mac.init(new SecretKeySpec(secret, "HmacSHA512"));
            return HexFormat.of().formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException | InvalidKeyException ex) {
            throw new IllegalStateException("HMAC-SHA512 is not available", ex);
        }
    }

    /** EN: Sorted, empty values left out, as VNPay's own samples do. / VI: Đã sắp xếp, bỏ giá trị rỗng, như mẫu của VNPay. */
    static String canonical(Map<String, String> fields) {
        return new TreeMap<>(fields).entrySet().stream()
                .filter(field -> field.getValue() != null && !field.getValue().isEmpty())
                .map(field -> encode(field.getKey()) + "=" + encode(field.getValue()))
                .collect(Collectors.joining("&"));
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.US_ASCII);
    }
}
