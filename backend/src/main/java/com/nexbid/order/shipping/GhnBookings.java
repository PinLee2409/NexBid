package com.nexbid.order.shipping;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.stereotype.Service;

import com.nexbid.auction.AuctionService;
import com.nexbid.auction.AuctionSummaryView;
import com.nexbid.common.exception.BusinessException;
import com.nexbid.common.exception.ErrorCode;
import com.nexbid.order.entity.Order;
import com.nexbid.product.Parcel;
import com.nexbid.product.ProductService;
import com.nexbid.product.ProductView;
import com.nexbid.user.AddressBook;
import com.nexbid.user.ShippingAddress;

/**
 * EN: Books a GHN pickup for a paid order on NexBid's GHN account: from the seller's saved address to the address the
 *     buyer paid for, the product's packed size (or the default box), insured like the shipping quote, nothing
 *     collected on delivery. Both addresses must carry GHN's ids.
 * VI: Đặt GHN lấy hàng cho một đơn đã trả tiền bằng tài khoản GHN của NexBid: từ địa chỉ đã lưu của người bán tới địa
 *     chỉ người mua đã dùng khi trả tiền, theo kích thước kiện (hoặc hộp mặc định), khai giá như lúc báo phí ship, không
 *     thu hộ. Cả hai địa chỉ phải có mã GHN.
 */
@Service
public class GhnBookings {

    private final GhnShipping ghn;
    private final AddressBook addresses;
    private final AuctionService auctions;
    private final ProductService products;
    private final ShippingEstimates estimates;

    GhnBookings(GhnShipping ghn, AddressBook addresses, AuctionService auctions, ProductService products,
            ShippingEstimates estimates) {
        this.ghn = ghn;
        this.addresses = addresses;
        this.auctions = auctions;
        this.products = products;
        this.estimates = estimates;
    }

    /** EN: GHN's order code and what GHN charges NexBid. / VI: Mã đơn GHN và số GHN thu của NexBid. */
    public record Booked(String orderCode, BigDecimal fee) {
    }

    public boolean enabled() {
        return ghn.enabled();
    }

    public Booked book(Order order) {
        if (!ghn.enabled()) {
            throw new BusinessException(ErrorCode.SHIPPING_UNAVAILABLE, "GHN is not connected");
        }
        if (order.getShipDistrictId() == null || order.getShipWardCode() == null) {
            throw new BusinessException(ErrorCode.GHN_ADDRESS_REQUIRED,
                    "The buyer's address was typed by hand, so GHN cannot be booked for it");
        }
        ShippingAddress from = addresses.addressOf(order.getSellerId()).filter(ShippingAddress::onGhn)
                .orElseThrow(() -> new BusinessException(ErrorCode.GHN_ADDRESS_REQUIRED,
                        "Pick your own address from GHN's lists before booking GHN"));
        AuctionSummaryView lot = auctions.participantSummariesOf(List.of(order.getAuctionId())).get(order.getAuctionId());
        Parcel parcel = products.findById(lot.product().id()).map(ProductView::parcel).orElse(null);
        if (parcel == null) {
            parcel = Parcel.DEFAULT;
        }

        GhnClient.Booked booked = ghn.book(new GhnClient.Booking(order.getId().toString(),
                from.recipient(), from.phone(), from.line(), from.ward(), from.district(), from.province(),
                order.getShipRecipient(), order.getShipPhone(), order.getShipAddress(), order.getShipDistrictId(),
                order.getShipWardCode(), lot.product().name(), parcel.weightGrams(), parcel.lengthCm(),
                parcel.widthCm(), parcel.heightCm(), estimates.insuranceFor(order.getAmount())));
        return new Booked(booked.orderCode(), booked.fee());
    }

    /** EN: Where the seller prints GHN's label. / VI: Nơi người bán in phiếu GHN. */
    public String labelUrl(String orderCode) {
        return ghn.labelUrl(orderCode);
    }
}
