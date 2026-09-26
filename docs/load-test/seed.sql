-- EN: Ten open lots for the k6 load test (guide §41), written straight to the database — creating and
--     approving them through the API would need an admin. Safe to run again: it only tops up what is
--     missing and pushes the close back, so repeated runs keep bidding on the same lots.
-- VI: Mười lô đang mở cho load test k6 (guide §41), ghi thẳng vào database — tạo và duyệt qua API thì cần
--     tài khoản admin. Chạy lại nhiều lần vẫn an toàn: chỉ bổ sung phần còn thiếu và lùi giờ đóng, nên các
--     lần chạy sau tiếp tục trả giá trên đúng các lô đó.
--
--   docker compose exec -T postgres psql -U nexbid -d nexbid -v ON_ERROR_STOP=1 < docs/load-test/seed.sql
--
-- EN: Ids follow a pattern the k6 script rebuilds: lot n is 10ad0000-0000-4000-8000-<n padded to 12>.
-- VI: Id theo một quy luật mà script k6 dựng lại được: lô n là 10ad0000-0000-4000-8000-<n đệm đủ 12 số>.

BEGIN;

INSERT INTO users (id, full_name, email, password, status, created_at, updated_at)
VALUES ('10ad0000-0000-4000-a000-000000000001', 'Load Test Seller', 'loadtest.seller@nexbid.local',
        -- EN: Not a real hash: this account never signs in. / VI: Không phải hash thật: tài khoản này không bao giờ đăng nhập.
        'no-login', 'ACTIVE', now(), now())
ON CONFLICT (id) DO NOTHING;

INSERT INTO user_roles (user_id, role_id)
SELECT '10ad0000-0000-4000-a000-000000000001', id FROM roles WHERE name IN ('BUYER', 'SELLER')
ON CONFLICT DO NOTHING;

INSERT INTO products (id, seller_id, category_id, name, description, condition, status, created_at, updated_at)
SELECT ('10ad0000-0000-4000-9000-' || lpad(n::text, 12, '0'))::uuid,
       '10ad0000-0000-4000-a000-000000000001',
       (SELECT id FROM categories ORDER BY name LIMIT 1),
       'Load test lot ' || n,
       'A lot seeded for the k6 load test in docs/load-test.',
       'LIKE_NEW', 'IN_AUCTION', now(), now()
  FROM generate_series(1, 10) AS n
ON CONFLICT (id) DO NOTHING;

INSERT INTO auctions (id, product_id, seller_id, starting_price, current_price, minimum_increment,
                      start_time, end_time, status, bid_count, version, created_at, updated_at)
SELECT ('10ad0000-0000-4000-8000-' || lpad(n::text, 12, '0'))::uuid,
       ('10ad0000-0000-4000-9000-' || lpad(n::text, 12, '0'))::uuid,
       '10ad0000-0000-4000-a000-000000000001',
       1000000, 1000000, 10000,
       now() - interval '1 hour', now() + interval '1 day', 'ACTIVE', 0, 0, now(), now()
  FROM generate_series(1, 10) AS n
ON CONFLICT (id) DO NOTHING;

-- EN: Keep the lots open for another day. One that already closed stays closed — reopening it would
--     orphan its payment — and the k6 setup says so.
-- VI: Giữ các lô mở thêm một ngày. Lô đã đóng thì để nguyên — mở lại sẽ bỏ rơi khoản thanh toán của nó —
--     và phần setup của k6 sẽ báo điều đó.
UPDATE auctions
   SET end_time = now() + interval '1 day', updated_at = now()
 WHERE id::text LIKE '10ad0000-0000-4000-8000-%' AND status = 'ACTIVE';

COMMIT;
