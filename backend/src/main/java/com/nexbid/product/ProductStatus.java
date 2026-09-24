package com.nexbid.product;

/**
 * EN: Where a product is in its life. Guide §11 suggests DRAFT/ACTIVE/INACTIVE, but those cannot say
 *     "already in an auction" — which is exactly what the schedule screen filters on.
 * VI: Sản phẩm đang ở đâu trong vòng đời. Guide §11 gợi ý DRAFT/ACTIVE/INACTIVE, nhưng bộ đó không nói được
 *     "đang nằm trong một phiên đấu giá" — mà đó chính là thứ màn lên lịch cần để lọc.
 */
public enum ProductStatus {
    /** EN: Being written, not ready to sell. / VI: Đang soạn, chưa sẵn sàng bán. */
    DRAFT,
    /** EN: Ready to be put in an auction. / VI: Sẵn sàng đưa vào phiên đấu giá. */
    AVAILABLE,
    /** EN: Attached to an auction, so it cannot be scheduled again. / VI: Đã gắn vào một phiên, không lên lịch lại được. */
    IN_AUCTION,
    /** EN: The auction completed and it changed hands. / VI: Phiên đã xong và hàng đã đổi chủ. */
    SOLD
}
