-- EN: Search ignores Vietnamese accents and case: "dong ho" finds "Đồng hồ". unaccent maps đ/Đ to d/D as well.
--     fold() is IMMUTABLE (the dictionary is named) so it can back an index later.
-- VI: Tìm kiếm bỏ qua dấu tiếng Việt và chữ hoa: "dong ho" tìm ra "Đồng hồ". unaccent cũng đổi đ/Đ thành d/D.
--     fold() là IMMUTABLE (đã chỉ rõ từ điển) nên sau này dùng được cho index.
CREATE EXTENSION IF NOT EXISTS unaccent;

CREATE OR REPLACE FUNCTION fold(value TEXT) RETURNS TEXT
    LANGUAGE sql IMMUTABLE PARALLEL SAFE STRICT
AS $$ SELECT lower(public.unaccent('public.unaccent'::regdictionary, value)) $$;
