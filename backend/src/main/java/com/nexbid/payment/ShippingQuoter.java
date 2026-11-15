package com.nexbid.payment;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

/**
 * EN: Prices shipping for a won lot. Implemented by the shipping side (GHN); the payment module only asks, so it does
 *     not depend on who answers. Empty means no carrier quote, and the flat fee is charged instead.
 * VI: Tính phí ship cho một lô đã thắng. Phía vận chuyển (GHN) hiện thực; module thanh toán chỉ hỏi, nên không phụ
 *     thuộc vào bên trả lời. Rỗng nghĩa là không có giá của hãng, và phí cố định được thu thay vào.
 */
public interface ShippingQuoter {

    Optional<BigDecimal> quote(UUID auctionId, UUID buyerId, BigDecimal price);
}
