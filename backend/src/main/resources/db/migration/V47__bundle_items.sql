-- EN: A lot can be a set: the product lists its pieces (name and quantity) as JSON. Empty means a single item.
-- VI: Một lô có thể là một bộ: sản phẩm liệt kê các món (tên và số lượng) dạng JSON. Rỗng nghĩa là một món đơn lẻ.
ALTER TABLE products ADD COLUMN bundle_items TEXT;
