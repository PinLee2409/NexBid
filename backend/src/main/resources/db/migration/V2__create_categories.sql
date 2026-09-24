-- EN: Categories (guide §10). Products reference these, so the table comes first.
-- VI: Danh mục (guide §10). Sản phẩm tham chiếu tới đây, nên bảng này phải có trước.

CREATE TABLE categories (
    id          UUID         PRIMARY KEY,
    name        VARCHAR(80)  NOT NULL,
    -- EN: What the browse URL carries, so it has to be unique and stable.
    -- VI: Thứ nằm trên URL duyệt hàng, nên phải duy nhất và ổn định.
    slug        VARCHAR(80)  NOT NULL UNIQUE,
    description VARCHAR(500),
    image_url   VARCHAR(500),
    status      VARCHAR(16)  NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL,
    updated_at  TIMESTAMPTZ  NOT NULL
);

-- EN: Name is shown, so two categories reading the same is a data error even when slugs differ.
-- VI: Tên là thứ hiển thị, nên hai danh mục trùng tên là lỗi dữ liệu dù slug có khác nhau.
CREATE UNIQUE INDEX ux_categories_name_lower ON categories (LOWER(name));

-- EN: The seven the frontend already browses by. Seeded so the two ends agree from the first request.
-- VI: Đúng bảy danh mục frontend đang duyệt theo. Gieo sẵn để hai đầu khớp nhau ngay từ request đầu tiên.
INSERT INTO categories (id, name, slug, description, status, created_at, updated_at) VALUES
  (gen_random_uuid(), 'Technology',   'technology',   'Flagship machines, rare silicon and hard-to-find hardware.', 'ACTIVE', now(), now()),
  (gen_random_uuid(), 'Watches',      'watches',      'Swiss references, vintage dials and collector pieces.',      'ACTIVE', now(), now()),
  (gen_random_uuid(), 'Collectibles', 'collectibles', 'Graded cards, sealed sets and cultural artefacts.',          'ACTIVE', now(), now()),
  (gen_random_uuid(), 'Sneakers',     'sneakers',     'Deadstock grails and limited collaborations.',               'ACTIVE', now(), now()),
  (gen_random_uuid(), 'Fashion',      'fashion',      'Archive pieces, leather goods and runway rarities.',         'ACTIVE', now(), now()),
  (gen_random_uuid(), 'Art',          'art',          'Editions, originals and signed prints from working artists.','ACTIVE', now(), now()),
  (gen_random_uuid(), 'Cameras',      'cameras',      'Digital bodies, cult optics and film classics.',             'ACTIVE', now(), now());
