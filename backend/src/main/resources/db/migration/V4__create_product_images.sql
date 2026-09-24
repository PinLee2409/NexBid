-- EN: Product images (guide §12, spec §23). One product has many; the lowest sort_order is the cover.
-- VI: Ảnh sản phẩm (guide §12, spec §23). Một sản phẩm có nhiều ảnh; sort_order nhỏ nhất là ảnh bìa.

CREATE TABLE product_images (
    id         UUID         PRIMARY KEY,
    -- EN: CASCADE here, unlike products: an image has no meaning without its product.
    -- VI: Ở đây dùng CASCADE khác với products: một tấm ảnh không còn ý nghĩa gì khi mất sản phẩm.
    product_id UUID         NOT NULL REFERENCES products (id) ON DELETE CASCADE,
    image_url  VARCHAR(500) NOT NULL,
    alt_text   VARCHAR(200),
    sort_order INTEGER      NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL
);

-- EN: Images are always read as "this product's, in order".
-- VI: Ảnh luôn được đọc theo kiểu "của sản phẩm này, theo thứ tự".
CREATE INDEX ix_product_images_product ON product_images (product_id, sort_order);

-- EN: Two images cannot claim the same position — the cover must be unambiguous.
-- VI: Hai ảnh không thể cùng giữ một vị trí — ảnh bìa phải rõ ràng là cái nào.
CREATE UNIQUE INDEX ux_product_images_order ON product_images (product_id, sort_order);
