package com.nexbid.payment;

import java.math.BigDecimal;

/**
 * EN: The shipping fixed on a payment and what the buyer will be charged. {@code source} is GHN, FLAT or SELLER.
 * VI: Phí ship đã chốt trên một khoản thanh toán và tổng người mua sẽ trả. {@code source} là GHN hoặc FLAT.
 */
public record ShippingQuote(BigDecimal amount, BigDecimal shippingFee, String source, BigDecimal total,
        BigDecimal shippingDiscount, String promoCode) {

    public static final String GHN = "GHN";
    public static final String FLAT = "FLAT";
    /** EN: Free shipping: the seller pays the parcel. / VI: Miễn phí vận chuyển: người bán trả tiền gửi kiện. */
    public static final String SELLER = "SELLER";
}
