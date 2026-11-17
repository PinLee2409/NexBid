-- EN: Backs the accent-free name search (V39) with a trigram index. Measured on 200,000 products: a "%dong ho ...%"
--     search fell from about 180 ms (every row folded and compared) to about 12 ms. Patterns shorter than three
--     letters still read most of the table; trigrams cannot help them.
-- VI: Thêm index trigram cho tìm tên không dấu (V39). Đo trên 200.000 sản phẩm: tìm "%dong ho ...%" giảm từ khoảng
--     180 ms (gập dấu và so từng dòng) xuống khoảng 12 ms. Mẫu ngắn hơn ba chữ vẫn đọc gần hết bảng; trigram không
--     giúp được.
CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE INDEX IF NOT EXISTS products_name_fold_trgm ON products USING gin (fold(name) gin_trgm_ops);
