package com.nexbid.user;

import java.util.stream.Collectors;
import java.util.stream.Stream;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * EN: Where a buyer's lots are delivered. Also the request body of PUT /api/users/me/address.
 * VI: Nơi giao hàng cho người mua. Cũng là body của PUT /api/users/me/address.
 */
public record ShippingAddress(

        @NotBlank(message = "Recipient is required")
        @Size(max = 120, message = "Recipient must be at most 120 characters")
        String recipient,

        @NotBlank(message = "Phone is required")
        @Pattern(regexp = "^\\+?[0-9 .-]{8,20}$", message = "Phone must be 8 to 20 digits")
        String phone,

        @NotBlank(message = "Address is required")
        @Size(max = 255, message = "Address must be at most 255 characters")
        String line,

        @Size(max = 120, message = "Ward must be at most 120 characters")
        String ward,

        @Size(max = 120, message = "District must be at most 120 characters")
        String district,

        @NotBlank(message = "Province or city is required")
        @Size(max = 120, message = "Province or city must be at most 120 characters")
        String province,

        /**
         * EN: GHN's ids when the address was picked from GHN's lists; all three or none. Sellers ship from it, buyers
         *     receive at it, and shipping quotes need both.
         * VI: Mã GHN khi địa chỉ được chọn từ danh sách của GHN; đủ cả ba hoặc không có. Người bán gửi từ đây, người
         *     mua nhận ở đây, và báo phí ship cần cả hai.
         */
        @Positive(message = "Province id must be positive")
        Integer provinceId,

        @Positive(message = "District id must be positive")
        Integer districtId,

        @Size(max = 20, message = "Ward code must be at most 20 characters")
        String wardCode) {

    /** EN: Whether GHN can quote to or from here. / VI: GHN có báo phí tới hoặc từ đây được không. */
    public boolean onGhn() {
        return provinceId != null && districtId != null && wardCode != null;
    }

    /** EN: Street, ward, district, province — as a label reads. / VI: Số nhà, phường, quận, tỉnh — như trên nhãn gửi hàng. */
    public String oneLine() {
        return Stream.of(line, ward, district, province)
                .filter(part -> part != null && !part.isBlank())
                .map(String::trim)
                .collect(Collectors.joining(", "));
    }

    public ShippingAddress trimmed() {
        String code = trim(wardCode);
        boolean complete = provinceId != null && districtId != null && code != null;
        return new ShippingAddress(trim(recipient), trim(phone), trim(line), trim(ward), trim(district), trim(province),
                complete ? provinceId : null, complete ? districtId : null, complete ? code : null);
    }

    private static String trim(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
