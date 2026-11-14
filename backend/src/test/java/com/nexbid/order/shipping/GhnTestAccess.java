package com.nexbid.order.shipping;

import java.math.BigDecimal;

import com.nexbid.order.repository.OrderRepository;

/**
 * EN: Lets tests in other packages build the GHN integration against a fake gateway.
 * VI: Cho test ở package khác dựng tích hợp GHN với một gateway giả.
 */
public final class GhnTestAccess {

    private GhnTestAccess() {
    }

    public static Object client(String baseUrl, String token, String shopId) {
        return new GhnClient(baseUrl, token, shopId);
    }

    public static GhnShipping shipping(OrderRepository orders, Object client, String secret) {
        return new GhnShipping(orders, (GhnClient) client, true, secret);
    }

    public static BigDecimal fee(GhnShipping ghn) {
        return ghn.fee(new GhnClient.FeeQuery(1454, 1452, "21012", 1000, 30, 20, 10, 2_000_000));
    }
}
