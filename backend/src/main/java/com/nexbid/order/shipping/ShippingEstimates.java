package com.nexbid.order.shipping;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.nexbid.auction.AuctionDetailPublicView;
import com.nexbid.auction.AuctionService;
import com.nexbid.common.exception.BusinessException;
import com.nexbid.payment.ShippingQuoter;
import com.nexbid.product.Parcel;
import com.nexbid.product.ProductService;
import com.nexbid.product.ProductView;
import com.nexbid.user.AddressBook;
import com.nexbid.user.ShippingAddress;

/**
 * EN: "About 96,900₫ to ship to Ba Đình, Hà Nội" on a lot page. A guide only: nothing is charged, and the seller may
 *     ship with another carrier. GHN quotes from the seller's saved address to the viewer's, for the product's packed
 *     size (or a default box) insured at the lot's price rounded up to the next million, so one quote serves every bid
 *     within that million.
 * VI: "Phí ship về Ba Đình, Hà Nội khoảng 96.900₫" trên trang lô. Chỉ để tham khảo: không thu tiền này, và người bán có
 *     thể gửi bằng hãng khác. GHN báo phí từ địa chỉ đã lưu của người bán tới địa chỉ của người xem, theo kích thước kiện
 *     của sản phẩm (hoặc hộp mặc định), khai giá bằng giá lô làm tròn lên triệu tiếp theo, để một lần báo phí dùng được
 *     cho mọi lượt trả giá trong cùng triệu đó.
 */
@Service
public class ShippingEstimates implements ShippingQuoter {

    /**
     * EN: OFF — GHN not connected, show nothing. NEEDS_ADDRESS — the viewer is signed out or has no GHN address.
     *     NO_ORIGIN — the seller has no GHN address. UNAVAILABLE — GHN did not answer. READY — fee is set.
     * VI: OFF — chưa kết nối GHN, không hiện gì. NEEDS_ADDRESS — người xem chưa đăng nhập hoặc chưa có địa chỉ GHN.
     *     NO_ORIGIN — người bán chưa có địa chỉ GHN. UNAVAILABLE — GHN không trả lời. READY — đã có phí.
     */
    public enum State {
        OFF, NEEDS_ADDRESS, NO_ORIGIN, UNAVAILABLE, READY
    }

    public record Estimate(State state, BigDecimal fee, String district, String province, boolean defaultParcel) {

        static Estimate of(State state) {
            return new Estimate(state, null, null, null, false);
        }
    }

    private static final BigDecimal MILLION = BigDecimal.valueOf(1_000_000);
    private static final Logger log = LoggerFactory.getLogger(ShippingEstimates.class);

    private final GhnShipping ghn;
    private final AuctionService auctions;
    private final ProductService products;
    private final AddressBook addresses;
    private final long insuranceCap;

    ShippingEstimates(GhnShipping ghn, AuctionService auctions, ProductService products, AddressBook addresses,
            @Value("${nexbid.shipping.ghn.insurance-cap:5000000}") long insuranceCap) {
        this.ghn = ghn;
        this.auctions = auctions;
        this.products = products;
        this.addresses = addresses;
        this.insuranceCap = insuranceCap;
    }

    public Estimate forLot(UUID auctionId, UUID viewerId) {
        // EN: Unknown or hidden lots answer 404 whether or not GHN is on. / VI: Lô không có hoặc bị ẩn trả 404 dù GHN bật hay tắt.
        AuctionDetailPublicView lot = auctions.getPublic(auctionId);
        if (!ghn.enabled()) {
            return Estimate.of(State.OFF);
        }
        Optional<ShippingAddress> to = viewerId == null ? Optional.empty() : addresses.addressOf(viewerId);
        if (to.isEmpty() || !to.get().onGhn()) {
            return Estimate.of(State.NEEDS_ADDRESS);
        }
        Optional<ShippingAddress> from = addresses.addressOf(lot.seller().id());
        if (from.isEmpty() || !from.get().onGhn()) {
            return Estimate.of(State.NO_ORIGIN);
        }

        Parcel declared = products.findById(lot.product().id()).map(ProductView::parcel).orElse(null);
        BigDecimal price = lot.auction().currentPrice() != null
                ? lot.auction().currentPrice()
                : lot.auction().startingPrice();

        ShippingAddress buyer = to.get();
        return fee(from.get(), buyer, declared, price, auctionId)
                .map(fee -> new Estimate(State.READY, fee, buyer.district(), buyer.province(), declared == null))
                .orElseGet(() -> Estimate.of(State.UNAVAILABLE));
    }

    /**
     * EN: The shipping charged with a won lot: the same GHN quote as the lot page, for the price that won. Empty when
     *     GHN is off or failing, or either address was typed by hand; the payment then charges its flat fee.
     * VI: Phí ship thu cùng một lô đã thắng: cùng cách báo phí GHN như trang lô, theo giá thắng. Rỗng khi GHN tắt hoặc
     *     lỗi, hoặc một trong hai địa chỉ gõ tay; khi đó khoản thanh toán thu phí cố định.
     */
    @Override
    public Optional<BigDecimal> quote(UUID auctionId, UUID buyerId, BigDecimal price) {
        if (!ghn.enabled()) {
            return Optional.empty();
        }
        Optional<ShippingAddress> to = addresses.addressOf(buyerId).filter(ShippingAddress::onGhn);
        Optional<ShippingAddress> from = addresses.addressOf(auctions.sellerOf(auctionId)).filter(ShippingAddress::onGhn);
        if (to.isEmpty() || from.isEmpty()) {
            return Optional.empty();
        }
        UUID productId = auctions.participantSummariesOf(List.of(auctionId)).get(auctionId).product().id();
        Parcel declared = products.findById(productId).map(ProductView::parcel).orElse(null);
        return fee(from.get(), to.get(), declared, price, auctionId);
    }

    /**
     * EN: Insured at the price rounded up to the next million, so one quote serves every bid within that million, and
     *     never above the cap: about 0.5% of the insured value is added to the fee.
     * VI: Khai giá bằng giá làm tròn lên triệu tiếp theo, để một lần báo phí dùng cho mọi lượt trả giá trong cùng triệu
     *     đó, và không quá mức trần: khoảng 0,5% giá khai được cộng vào phí.
     */
    private Optional<BigDecimal> fee(ShippingAddress from, ShippingAddress to, Parcel declared, BigDecimal price,
            UUID auctionId) {
        Parcel parcel = declared == null ? Parcel.DEFAULT : declared;
        long insured = Math.min(insuranceCap,
                price.divide(MILLION, 0, RoundingMode.CEILING).multiply(MILLION).longValueExact());
        try {
            return Optional.of(ghn.fee(new GhnClient.FeeQuery(from.districtId(), to.districtId(), to.wardCode(),
                    parcel.weightGrams(), parcel.lengthCm(), parcel.widthCm(), parcel.heightCm(), insured)));
        } catch (BusinessException ex) {
            log.warn("GHN fee for lot {} failed: {}", auctionId, ex.getMessage());
            return Optional.empty();
        }
    }
}
