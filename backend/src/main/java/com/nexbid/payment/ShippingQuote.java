package com.nexbid.payment;

import java.math.BigDecimal;

/**
 * EN: The shipping fixed on a payment and what the buyer will be charged. {@code source} is GHN or FLAT.
 * VI: Phí ship đã chốt trên một khoản thanh toán và tổng người mua sẽ trả. {@code source} là GHN hoặc FLAT.
 */
public record ShippingQuote(BigDecimal amount, BigDecimal shippingFee, String source, BigDecimal total) {

    public static final String GHN = "GHN";
    public static final String FLAT = "FLAT";
}
