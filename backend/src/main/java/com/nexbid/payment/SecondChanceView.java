package com.nexbid.payment;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * EN: For the seller, one unpaid lot: the runner-up's bid (null when nobody else bid), whether an offer can be made
 *     now, and the offer if one was. The runner-up is not named — bidders stay anonymous to sellers.
 * VI: Cho người bán, một lô bị bỏ không trả: giá của người thứ hai (null nếu không ai khác trả giá), có đề nghị được
 *     ngay không, và đề nghị nếu đã có. Người thứ hai không được nêu tên — người trả giá luôn ẩn danh với người bán.
 */
public record SecondChanceView(UUID auctionId, BigDecimal runnerUpBid, boolean canOffer, OfferView.Details offer) {
}
