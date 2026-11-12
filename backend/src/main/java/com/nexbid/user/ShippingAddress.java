package com.nexbid.user;

import java.util.stream.Collectors;
import java.util.stream.Stream;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
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
        String province) {

    /** EN: Street, ward, district, province — as a label reads. / VI: Số nhà, phường, quận, tỉnh — như trên nhãn gửi hàng. */
    public String oneLine() {
        return Stream.of(line, ward, district, province)
                .filter(part -> part != null && !part.isBlank())
                .map(String::trim)
                .collect(Collectors.joining(", "));
    }

    public ShippingAddress trimmed() {
        return new ShippingAddress(trim(recipient), trim(phone), trim(line), trim(ward), trim(district), trim(province));
    }

    private static String trim(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
