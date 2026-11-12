#!/usr/bin/env node
// EN: Adds to an existing NexBid database instead of starting one: more sellers and bidders, about thirty
//     more lots across every category (live for days, opening soon, waiting for approval), and a few lots
//     that close, get paid, ship, arrive and are reviewed — so seller pages have ratings. Also creates the
//     admin account admin@gmail.com. Safe to run again: accounts that exist are signed in, and lots whose
//     product already exists are skipped. Like seed.mjs, everything goes through the API; only roles are
//     granted in SQL.
// VI: Bổ sung vào cơ sở dữ liệu NexBid đang có thay vì tạo mới: thêm người bán và người mua, khoảng ba mươi
//     lô ở mọi danh mục (đang đấu giá nhiều ngày, sắp mở, chờ duyệt), và vài lô đóng phiên, được thanh toán,
//     gửi hàng, nhận hàng và đánh giá — để trang người bán có điểm đánh giá. Đồng thời tạo tài khoản quản trị
//     admin@gmail.com. Chạy lại được: tài khoản đã có thì chỉ đăng nhập, lô có sản phẩm trùng tên thì bỏ qua.
//     Giống seed.mjs, mọi thứ đi qua API; chỉ vai trò được cấp bằng SQL.
//
//   node docs/demo/enrich.mjs                 # dev services + backend on :8080
//   STACK=docker node docs/demo/enrich.mjs    # the stack from `docker compose up`
//
// EN: Run seed.mjs first; this reuses its accounts (pin, alex, … @nexbid.test). Needs internet for photos.
// VI: Chạy seed.mjs trước; script này dùng lại tài khoản của nó (pin, alex, … @nexbid.test). Cần internet để tải ảnh.

import { execFileSync } from "node:child_process";
import path from "node:path";
import { fileURLToPath } from "node:url";

import { payThroughVnpay } from "./vnpay.mjs";

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "../..");
const API = (process.env.NEXBID_API_URL ?? "http://localhost:8080").replace(/\/$/, "");
const COMPOSE_FILE = process.env.STACK === "docker" ? "docker-compose.yml" : "docker/compose.yaml";
const PASSWORD = process.env.DEMO_PASSWORD ?? "nexbid-demo";

// EN: Key → account. `email`/`password` override the demo defaults. / VI: Khoá → tài khoản. `email`/`password` thay mặc định demo.
const PEOPLE = {
  // EN: The admin account asked for by name. / VI: Tài khoản quản trị được yêu cầu.
  gmailAdmin: { name: "NexBid Admin", role: "ADMIN", email: "admin@gmail.com", password: "admin123" },
  admin: { name: "Nora Adams", role: "ADMIN" },
  // EN: From seed.mjs. / VI: Từ seed.mjs.
  pin: { name: "Pin Le", role: "SELLER" },
  atelier: { name: "Atelier Nord", role: "SELLER" },
  vault: { name: "The Vault Collective", role: "SELLER" },
  lumen: { name: "Lumen Optics", role: "SELLER" },
  alex: { name: "Alex Turner" },
  john: { name: "John Mercer" },
  mika: { name: "Mika Tanaka" },
  sara: { name: "Sara Novak" },
  dmitri: { name: "Dmitri Volkov" },
  // EN: New sellers. / VI: Người bán mới.
  kicks: { name: "Kicks Lab Saigon", role: "SELLER" },
  saigon: { name: "Saigon Vintage House", role: "SELLER" },
  hanoiart: { name: "Hanoi Art House", role: "SELLER" },
  tempo: { name: "Tempo Watch Atelier", role: "SELLER" },
  // EN: New bidders. / VI: Người mua mới.
  linh: { name: "Linh Nguyen" },
  minh: { name: "Minh Tran" },
  khoa: { name: "Khoa Pham" },
  chloe: { name: "Chloe Martin" },
  raj: { name: "Raj Patel" },
  emma: { name: "Emma Wilson" },
};

const emailOf = (key) => PEOPLE[key].email ?? `${key}@nexbid.test`;
const passwordOf = (key) => PEOPLE[key].password ?? PASSWORD;

// EN: Unsplash ids, matched to each product by its description on unsplash.com (Unsplash License).
// VI: Id ảnh Unsplash, khớp với từng sản phẩm theo mô tả trên unsplash.com (Unsplash License).
const photo = (id) => `https://images.unsplash.com/${id}?fm=jpg&fit=crop&w=1400&q=80`;

const H = 60;
const D = 24 * H;

// EN: Times are minutes from now. `closing` lots end during the run; `after` says how far their order goes.
// VI: Thời gian tính bằng phút kể từ bây giờ. Lô `closing` kết thúc ngay khi chạy; `after` cho biết đơn hàng đi tới đâu.
const LOTS = [
  // ---------------------------------------------------------------- technology
  {
    seller: "atelier", category: "technology", condition: "LIKE_NEW",
    name: "Sony WH-1000XM4 Noise-Cancelling Headphones",
    description: "Black, bought last year and used mostly on flights. Ear pads replaced with genuine Sony spares last month, battery still lasts a full long-haul trip. Includes the hard case, aeroplane adapter and both cables.",
    photos: ["photo-1548378329-437e1ef34263", "photo-1583305727488-61f82c7eae4b"],
    price: 3_500_000, step: 100_000, buyNow: 6_200_000,
    plan: { state: "live", start: -6 * H, end: 2 * D, bids: ["linh", "raj", "linh"] },
  },
  {
    seller: "atelier", category: "technology", condition: "LIKE_NEW",
    name: "AirPods Max — Silver",
    description: "Silver with the white headband. Cushions are clean and springy, no creaking at the hinges. Smart Case and the original USB-C cable included; AppleCare runs until next spring.",
    photos: ["photo-1609081219090-a6d81d3085bf", "photo-1612116454817-2b0841e30eaf"],
    price: 7_000_000, step: 200_000,
    plan: { state: "live", start: -2 * H, end: 3 * D, bids: ["emma", "chloe", "emma", "khoa"] },
  },
  {
    seller: "pin", category: "technology", condition: "LIKE_NEW",
    name: 'iPad Pro 12.9" with Magic Keyboard',
    description: "512GB Wi-Fi model with the Magic Keyboard and Apple Pencil. Screen has worn a protector since day one; battery health 94%. Reset and ready, ships in the original boxes.",
    photos: ["photo-1760708369071-e8a50a8979cb", "photo-1759820941220-fed6a1010146"],
    price: 18_000_000, step: 250_000, reserve: 22_000_000,
    plan: { state: "live", start: -10 * H, end: 4 * D, bids: ["minh", "raj", "minh"] },
  },
  {
    seller: "saigon", category: "technology", condition: "GOOD",
    name: "Nintendo Switch OLED + 6 Games",
    description: "White OLED model with both Joy-Cons, drift-free. Bundle includes Zelda: Tears of the Kingdom, Mario Kart 8, Smash Bros. Ultimate and three more cartridges, plus a carry case and a 256GB card.",
    photos: ["photo-1578303512597-81e6cc155b3e", "photo-1612036781124-847f8939b154", "photo-1749264292585-858660898055"],
    price: 6_000_000, step: 100_000, buyNow: 9_500_000,
    plan: { state: "live", start: -20 * H, end: 18 * H, antiSniping: true, bids: ["khoa", "linh", "khoa", "alex", "khoa"] },
  },
  {
    seller: "lumen", category: "technology", condition: "LIKE_NEW",
    name: "DJI Mini 3 Pro Fly More Kit",
    description: "Under 249g so no registration needed in most countries. Three batteries with the charging hub, the RC with built-in screen, spare propellers and the shoulder bag. Never crashed; 11 hours of flight logged.",
    photos: ["photo-1599403868569-30dae419c0a7", "photo-1665649925534-bb0e308a8fa3"],
    price: 12_000_000, step: 200_000,
    plan: { state: "live", start: -H, end: 5 * D, bids: ["raj"] },
  },
  {
    seller: "pin", category: "technology", condition: "LIKE_NEW",
    name: "iPhone 15 Pro Max 256GB",
    description: "Natural Titanium, unlocked. Kept in a case with a glass protector since purchase; battery health 98%. Box, unused USB-C cable and paperwork included.",
    photos: ["photo-1760708551271-05d0bc471590", "photo-1710023038502-ba80a70a9f53"],
    price: 20_000_000, step: 250_000,
    plan: { state: "upcoming", start: 18 * H, end: 3 * D },
  },
  {
    seller: "saigon", category: "technology", condition: "GOOD",
    name: "Vintage Analog Synthesizer",
    description: "Four-voice analog polysynth from the early eighties, fully recapped with new key contacts. All voices tune and hold; a couple of slider caps are replacements. A favourite in a Saigon studio for a decade.",
    photos: ["photo-1634041551278-a843c116ff28", "photo-1733900606117-2522d8ed3a7b"],
    price: 30_000_000, step: 500_000,
    plan: { state: "pending", start: 2 * D, end: 6 * D },
  },
  // ---------------------------------------------------------------- watches
  {
    seller: "tempo", category: "watches", condition: "GOOD",
    name: "Rolex Datejust 36 — Ref. 16234",
    description: "1990s Datejust with white gold fluted bezel and silver stick dial on a Jubilee bracelet. Serviced in 2024 with paperwork; bracelet stretch is minimal. Comes with a Tempo service warranty card.",
    photos: ["photo-1620625515032-6ed0c1790c75", "photo-1619976491498-f2dadb25fb3b", "photo-1619976442556-210d9a914669"],
    price: 120_000_000, step: 1_000_000, reserve: 140_000_000,
    plan: { state: "live", start: -30 * H, end: 2 * D, autoBids: [["dmitri", 135_000_000]], bids: ["chloe", "sara", "chloe"] },
  },
  {
    seller: "tempo", category: "watches", condition: "LIKE_NEW",
    name: "Cartier Tank Must — Large",
    description: "Steel case with black lacquer dial, quartz movement and black alligator-effect strap. Purchased 2023, worn to a handful of events. Full set with box and international warranty card.",
    photos: ["photo-1747995525906-b141498b8e7e", "photo-1773414686135-cffd9fbe3590"],
    price: 55_000_000, step: 500_000,
    plan: { state: "live", start: -4 * H, end: 6 * D, bids: ["emma", "mika"] },
  },
  {
    seller: "tempo", category: "watches", condition: "LIKE_NEW",
    name: "Seiko Prospex Diver 200m",
    description: "Automatic diver with unidirectional bezel and LumiBrite hands. Runs +6 s/day. No scratches on the crystal; bracelet links all included.",
    photos: ["photo-1708647585535-c3f3317e9088", "photo-1708647585543-bee62dc16136"],
    price: 8_000_000, step: 100_000, buyNow: 12_000_000,
    plan: { state: "live", start: -12 * H, end: 30 * H, bids: ["minh", "khoa", "minh"] },
  },
  {
    seller: "kicks", category: "watches", condition: "GOOD",
    name: "Casio G-Shock Pair — Black & Orange",
    description: "Two G-Shocks sold together: an all-black resin model and a black-and-orange analog-digital. Both keep time and light up; light wear on the bezels.",
    photos: ["photo-1543956872-37cfc5474a71", "photo-1576420469891-b303889e81bf"],
    price: 2_000_000, step: 50_000,
    plan: { state: "upcoming", start: 6 * H, end: 2 * D },
  },
  // ---------------------------------------------------------------- cameras
  {
    seller: "lumen", category: "cameras", condition: "LIKE_NEW",
    name: "Fujifilm X100V — Silver",
    description: "The fixed 23mm f/2 compact everyone wants. 4,100 shutter actuations, firmware up to date. Includes the lens hood, adapter ring, two batteries and a leather half case.",
    photos: ["photo-1601854525059-f807eea1d9e1", "photo-1709200732045-69942455f9df"],
    price: 30_000_000, step: 500_000,
    plan: { state: "live", start: -5 * H, end: 26 * H, bids: ["chloe", "raj", "chloe", "pin"] },
  },
  {
    seller: "lumen", category: "cameras", condition: "GOOD",
    name: "Nikon D850 Body",
    description: "45.7MP full-frame body with 38,000 actuations. Rubber grips are tight, sensor recently cleaned. Two EN-EL15b batteries and the original strap.",
    photos: ["photo-1517282079187-899edd773b0d", "photo-1612109110174-9973fa777d0d", "photo-1541690663428-b99fc611c419"],
    price: 32_000_000, step: 500_000,
    plan: { state: "live", start: -8 * H, end: 4 * D, bids: ["john"] },
  },
  {
    seller: "saigon", category: "cameras", condition: "GOOD",
    name: "Polaroid 600 Instant Camera",
    description: "Classic box-type instant camera, tested with a fresh pack of film — every frame came out. Includes the original strap and one unopened colour film pack.",
    photos: ["photo-1612547036242-77002603e5aa", "photo-1593024566312-9415946b20b7"],
    price: 1_500_000, step: 50_000, buyNow: 2_800_000,
    plan: { state: "live", start: -2 * H, end: 3 * D, bids: ["linh", "emma"] },
  },
  // ---------------------------------------------------------------- sneakers
  {
    seller: "kicks", category: "sneakers", condition: "NEW",
    name: 'Nike Dunk Low "Panda" — US 9',
    description: "Deadstock, never tried on. Original box with lid intact and the receipt from a Saigon retailer.",
    photos: ["photo-1634624943296-54ce51cf732c", "photo-1623684225794-a8f1f5037f5c", "photo-1615290643080-e379930bf0cb"],
    price: 2_500_000, step: 50_000,
    plan: { state: "live", start: -3 * H, end: 20 * H, antiSniping: true, bids: ["khoa", "minh", "khoa", "linh", "minh"] },
  },
  {
    seller: "kicks", category: "sneakers", condition: "LIKE_NEW",
    name: "Adidas Samba OG — White / Black",
    description: "US 8.5, worn twice. Gum soles still clean, no creasing on the leather. Comes with the box and spare laces.",
    photos: ["photo-1718220130188-428c7dc27fd2", "photo-1718220095476-7916e897fc55"],
    price: 1_800_000, step: 50_000,
    plan: { state: "live", start: -H, end: 2 * D, bids: ["emma", "chloe"] },
  },
  {
    seller: "kicks", category: "sneakers", condition: "GOOD",
    name: 'Yeezy Boost 350 V2 "Grey"',
    description: "US 10. Primeknit upper is clean, Boost midsole lightly toned from wear. Box included; authenticated by Kicks Lab.",
    photos: ["photo-1572573385779-4db2f3af622e"],
    price: 4_000_000, step: 100_000,
    plan: { state: "live", start: -16 * H, end: 5 * D, bids: ["raj", "alex"] },
  },
  {
    seller: "kicks", category: "sneakers", condition: "NEW",
    name: "Converse Chuck 70 Hi — Black",
    description: "US 9, brand new with tags. The 1970 re-issue with the heavier canvas, cushioned insole and vintage license plate.",
    photos: ["photo-1602762201481-9abf682fa38d", "photo-1605973828881-d32d26aaf2ae"],
    price: 1_200_000, step: 50_000,
    plan: { state: "upcoming", start: 10 * H, end: 3 * D },
  },
  {
    seller: "kicks", category: "sneakers", condition: "LIKE_NEW",
    name: "New Balance 574 — Pink / Grey",
    description: "US 7, worn a few times indoors. Suede is clean and the midsole is bright. Original box.",
    photos: ["photo-1551107696-a4b0c5a0d9a2"],
    price: 1_500_000, step: 50_000,
    plan: { state: "closing", bids: ["linh", "emma", "linh"], after: "reviewed",
      review: { rating: 5, comment: "Exactly as described and shipped the next morning. Box was double-wrapped." } },
  },
  // ---------------------------------------------------------------- fashion
  {
    seller: "atelier", category: "fashion", condition: "LIKE_NEW",
    name: "Supreme x Louis Vuitton Keepall 45",
    description: "Red Epi leather Keepall from the 2017 collaboration. Handles have light patina, interior is spotless. Comes with the dust bag, name tag and receipt.",
    photos: ["photo-1567755997194-f92b5dc9106f", "photo-1564982759617-29646f69025c"],
    price: 150_000_000, step: 2_000_000, reserve: 170_000_000,
    plan: { state: "live", start: -20 * H, end: 4 * D, bids: ["mika", "dmitri"] },
  },
  {
    seller: "saigon", category: "fashion", condition: "GOOD",
    name: "Vintage Leather Crossbody Bag",
    description: "Brown and black full-grain leather with brass hardware, made in Florence in the 1980s. Supple leather, all stitching intact, light wear to the corners.",
    photos: ["photo-1586413595198-1840407316e9"],
    price: 3_000_000, step: 100_000,
    plan: { state: "live", start: -4 * H, end: 3 * D, bids: ["chloe"] },
  },
  {
    seller: "atelier", category: "fashion", condition: "LIKE_NEW",
    name: "Burberry Kensington Trench Coat",
    description: "Honey gabardine, UK 8. Dry-cleaned and stored in a garment bag. All buttons and the belt present; the check lining is pristine.",
    photos: ["photo-1676716105765-e19fe6a01851", "photo-1633821879282-0c4e91f96232"],
    price: 25_000_000, step: 500_000,
    plan: { state: "live", start: -24 * H, end: 36 * H, bids: ["emma", "sara", "emma"] },
  },
  {
    seller: "saigon", category: "fashion", condition: "GOOD",
    name: "Ray-Ban Wayfarer — Vintage B&L",
    description: "Original Bausch & Lomb era Wayfarers with etched lenses. Frame is straight and the hinges are tight. Comes in the brown leather case.",
    photos: ["photo-1572635196237-14b3f281503f", "photo-1556306535-38febf6782e7"],
    price: 4_000_000, step: 100_000,
    plan: { state: "closing", bids: ["raj", "alex", "raj"], after: "reviewed",
      review: { rating: 4, comment: "Lovely pair, genuinely B&L. Case had a bit more wear than the photos showed." } },
  },
  {
    seller: "saigon", category: "fashion", condition: "GOOD",
    name: "Levi's Type III Denim Trucker Jacket",
    description: "Mid-wash, size M, made in USA. Faded evenly at the seams and cuffs; no tears or stains.",
    photos: ["photo-1611312449408-fcece27cdbb7", "photo-1772985451338-6bbc32aeedf1"],
    price: 1_800_000, step: 50_000,
    plan: { state: "pending", start: D, end: 4 * D },
  },
  // ---------------------------------------------------------------- art
  {
    seller: "hanoiart", category: "art", condition: "NEW",
    name: '"Mountain Dawn" — Oil on Canvas',
    description: "Original oil painting, 80 × 60cm, signed lower right. Painted en plein air in Sa Pa and finished in the studio. Ships stretched and ready to hang.",
    photos: ["photo-1700405495207-3f87da07c1c7"],
    price: 15_000_000, step: 250_000,
    plan: { state: "live", start: -10 * H, end: 5 * D, bids: ["chloe", "sara"] },
  },
  {
    seller: "hanoiart", category: "art", condition: "NEW",
    name: '"River Valley" — Oil on Canvas',
    description: "Original oil, 100 × 70cm, part of the same series as Mountain Dawn. Thick impasto in the foreground, soft glazes in the sky. Certificate signed by the artist.",
    photos: ["photo-1694637110424-abfb52423afa"],
    price: 18_000_000, step: 250_000,
    plan: { state: "live", start: -H, end: 6 * D, bids: [] },
  },
  {
    seller: "hanoiart", category: "art", condition: "NEW",
    name: "Abstract Composition No. 3",
    description: "Acrylic and ink on canvas, 120 × 90cm. Layered colour fields scraped back to reveal the ground. Signed and dated on the reverse.",
    photos: ["photo-1541961017774-22349e4a1262"],
    price: 22_000_000, step: 500_000,
    plan: { state: "upcoming", start: 30 * H, end: 5 * D },
  },
  {
    seller: "hanoiart", category: "art", condition: "LIKE_NEW",
    name: "Bronze Horse Sculpture",
    description: "Lost-wax bronze on a marble base, 45cm tall. Cast in a small foundry in Bat Trang, edition 3 of 12. Rich brown patina with no wear.",
    photos: ["photo-1651085410796-e663860b2b08"],
    price: 35_000_000, step: 500_000,
    plan: { state: "live", start: -20 * H, end: 3 * D, bids: ["dmitri", "john"] },
  },
  {
    seller: "hanoiart", category: "art", condition: "NEW",
    name: "Pair of Studio Ceramic Vases",
    description: "Two tall wheel-thrown stoneware vases with ash glaze, 42cm and 38cm. Made by a Bat Trang potter; each signed on the base.",
    photos: ["photo-1760402327535-85a771fb034c"],
    price: 4_500_000, step: 100_000,
    plan: { state: "closing", bids: ["sara", "chloe"], after: "reviewed",
      review: { rating: 5, comment: "Beautiful glaze, packed in so much foam they could have survived anything." } },
  },
  {
    seller: "hanoiart", category: "art", condition: "NEW",
    name: '"Antelope Canyon" — Silver Gelatin Print',
    description: "Hand-printed black-and-white photograph, 50 × 70cm on fibre paper, edition 5 of 20. Signed and numbered; archival mat included.",
    photos: ["photo-1506574723610-7fe0f0c7f9ea"],
    price: 6_000_000, step: 100_000,
    plan: { state: "live", start: -3 * H, end: 4 * D, bids: ["minh"] },
  },
  // ---------------------------------------------------------------- collectibles
  {
    seller: "vault", category: "collectibles", condition: "GOOD",
    name: "Gibson Les Paul Custom — Ebony",
    description: "Ebony finish with gold hardware and the original 490R/498T pickups. Plays beautifully with a low action; frets at about 80%. Ships in the brown Gibson hard case.",
    photos: ["photo-1625159510807-03f1d1b52bff", "photo-1588885108221-776f24071e0b"],
    price: 85_000_000, step: 1_000_000,
    plan: { state: "live", start: -40 * H, end: 2 * D, autoBids: [["john", 95_000_000]], bids: ["dmitri", "alex"] },
  },
  {
    seller: "vault", category: "collectibles", condition: "GOOD",
    name: "Marshall Valve Guitar Amplifier",
    description: "All-valve head with the classic crunch. Re-tubed last year, quiet at idle. Includes the footswitch and a padded cover.",
    photos: ["photo-1600691222598-51c23d20bb05", "photo-1702438712148-cf23f3c38765"],
    price: 25_000_000, step: 500_000,
    plan: { state: "live", start: -12 * H, end: 4 * D, bids: ["john"] },
  },
  {
    seller: "saigon", category: "collectibles", condition: "GOOD",
    name: "Olivetti Valentine Typewriter — Red",
    description: "The 1969 Ettore Sottsass design in red ABS with its matching case. Types cleanly with a fresh ribbon; a few scuffs on the case.",
    photos: ["photo-1680813999409-b6109c96e554"],
    price: 7_000_000, step: 100_000,
    plan: { state: "live", start: -6 * H, end: 3 * D, bids: ["sara", "chloe", "sara"] },
  },
  {
    seller: "vault", category: "collectibles", condition: "LIKE_NEW",
    name: "Fountain Pen Collection — 4 Pens",
    description: "Four piston-fill fountain pens with gold nibs (F, M, M, B), all flushed and writing smoothly. Includes a leather roll and two bottles of ink.",
    photos: ["photo-1781835071877-7f2ed6e591be", "photo-1781835073061-8d26b2645a08"],
    price: 9_000_000, step: 200_000,
    plan: { state: "closing", bids: ["mika", "john"], after: "shipped" },
  },
  {
    seller: "kicks", category: "collectibles", condition: "GOOD",
    name: "Pokémon TCG Bulk Lot — 500+ Cards",
    description: "Over five hundred cards from 2016–2023 sets, including about forty holos and reverse holos. Sorted by set in a binder and a storage box.",
    photos: ["photo-1647892591690-25cf830cda51", "photo-1647893976852-71b2f6ba10e4"],
    price: 2_000_000, step: 50_000,
    plan: { state: "live", start: -2 * H, end: 2 * D, bids: ["khoa", "linh"] },
  },
];

const CLOSING_SECONDS = 25;
const WATCHES = {
  pin: ["Rolex Datejust 36 — Ref. 16234", "Fujifilm X100V — Silver", "Gibson Les Paul Custom — Ebony"],
  linh: ["Nike Dunk Low \"Panda\" — US 9", "AirPods Max — Silver", "Polaroid 600 Instant Camera"],
  chloe: ["Cartier Tank Must — Large", "Burberry Kensington Trench Coat", '"Mountain Dawn" — Oil on Canvas'],
  raj: ["DJI Mini 3 Pro Fly More Kit", "iPhone 15 Pro Max 256GB"],
  minh: ["Seiko Prospex Diver 200m", 'iPad Pro 12.9" with Magic Keyboard'],
};

const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));
const minutesFromNow = (minutes) => new Date(Date.now() + minutes * 60_000).toISOString();
const quote = (text) => `'${text.replaceAll("'", "''")}'`;

async function call(method, route, { token, body, form } = {}) {
  for (let attempt = 1; ; attempt++) {
    const response = await fetch(API + route, {
      method,
      headers: {
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
        ...(body !== undefined ? { "Content-Type": "application/json" } : {}),
      },
      body: form ?? (body !== undefined ? JSON.stringify(body) : undefined),
    });
    const payload = await response.json().catch(() => null);
    if (response.status === 429 && attempt <= 5) {
      await sleep((Number(response.headers.get("Retry-After")) || 2) * 1000);
      continue;
    }
    if (!response.ok || payload?.success === false) {
      const error = new Error(`${method} ${route} → ${response.status} ${payload?.code ?? ""} ${payload?.message ?? ""}`.trim());
      error.code = payload?.code;
      throw error;
    }
    return payload?.data;
  }
}

function sql(statement) {
  return execFileSync(
    "docker",
    ["compose", "-f", path.join(ROOT, COMPOSE_FILE), "exec", "-T", "postgres",
      "psql", "-U", "nexbid", "-d", "nexbid", "-v", "ON_ERROR_STOP=1", "-tAq", "-c", statement],
    { encoding: "utf8" },
  ).trim();
}

async function downloadPhotos(ids) {
  const photos = new Map();
  const queue = [...new Set(ids)];
  async function worker() {
    for (let id = queue.shift(); id; id = queue.shift()) {
      try {
        const response = await fetch(photo(id));
        if (!response.ok) throw new Error(`HTTP ${response.status}`);
        photos.set(id, new Blob([await response.arrayBuffer()], { type: "image/jpeg" }));
      } catch (error) {
        console.warn(`  ! photo ${id} skipped: ${error.message}`);
      }
    }
  }
  await Promise.all(Array.from({ length: 6 }, worker));
  return photos;
}

async function placeBid(lotId, who, tokens) {
  for (let attempt = 1; ; attempt++) {
    const { minimumNextBid } = await call("GET", `/api/auctions/${lotId}`);
    try {
      return await call("POST", `/api/auctions/${lotId}/bids`, { token: tokens[who], body: { amount: minimumNextBid } });
    } catch (error) {
      if (error.code !== "BID_TOO_LOW" || attempt >= 3) throw error;
    }
  }
}

async function waitFor(what, read, timeoutMs = 90_000) {
  const deadline = Date.now() + timeoutMs;
  for (;;) {
    const value = await read();
    if (value) return value;
    if (Date.now() > deadline) throw new Error(`Timed out waiting for ${what}`);
    await sleep(1000);
  }
}

async function main() {
  console.log(`Enriching ${API} (SQL via ${COMPOSE_FILE})`);
  try {
    await call("GET", "/api/server-time");
  } catch {
    throw new Error(`No backend at ${API}. Start it first (see README → Run).`);
  }

  console.log("• accounts");
  for (const [key, person] of Object.entries(PEOPLE)) {
    try {
      await call("POST", "/api/auth/register", { body: { fullName: person.name, email: emailOf(key), password: passwordOf(key) } });
      console.log(`  new      ${emailOf(key)}`);
    } catch (error) {
      if (error.code !== "EMAIL_ALREADY_EXISTS") throw error;
    }
  }
  const grants = Object.entries(PEOPLE)
    .filter(([, person]) => person.role)
    .map(([key, person]) => `(${quote(emailOf(key))}, ${quote(person.role)})`)
    .join(", ");
  sql(`INSERT INTO user_roles (user_id, role_id)
       SELECT u.id, r.id FROM users u JOIN roles r ON (u.email, r.name) IN (${grants})
       ON CONFLICT DO NOTHING`);
  const tokens = {};
  for (const key of Object.keys(PEOPLE)) {
    try {
      tokens[key] = (await call("POST", "/api/auth/login", { body: { email: emailOf(key), password: passwordOf(key) } })).accessToken;
    } catch (error) {
      throw new Error(`Cannot sign in as ${emailOf(key)} (${error.message}). Was it created with another password?`);
    }
  }

  const categories = Object.fromEntries((await call("GET", "/api/categories")).map((category) => [category.slug, category.id]));
  const existing = new Set(sql("SELECT name FROM products").split("\n").filter(Boolean));
  const todo = LOTS.filter((lot) => !existing.has(lot.name));
  if (todo.length < LOTS.length) console.log(`  (${LOTS.length - todo.length} lots already there, skipped)`);

  console.log("• photos");
  const photos = await downloadPhotos(todo.flatMap((lot) => lot.photos));

  console.log("• lots");
  const closing = [];
  const ordered = [...todo.filter((lot) => lot.plan.state !== "closing"), ...todo.filter((lot) => lot.plan.state === "closing")];
  for (const lot of ordered) {
    const seller = tokens[lot.seller];
    const product = await call("POST", "/api/seller/products", {
      token: seller,
      body: { name: lot.name, description: lot.description, categoryId: categories[lot.category], condition: lot.condition, publishNow: true },
    });
    const files = lot.photos.filter((id) => photos.has(id));
    if (files.length > 0) {
      const form = new FormData();
      files.forEach((id, index) => form.append("files", photos.get(id), `photo-${index + 1}.jpg`));
      await call("POST", `/api/seller/products/${product.id}/images`, { token: seller, form });
    }

    const { plan } = lot;
    const closingNow = plan.state === "closing";
    const auction = await call("POST", "/api/seller/auctions", {
      token: seller,
      body: {
        productId: product.id,
        startingPrice: lot.price,
        minimumIncrement: lot.step,
        startTime: minutesFromNow(closingNow ? -120 : plan.start),
        endTime: closingNow ? new Date(Date.now() + CLOSING_SECONDS * 1000).toISOString() : minutesFromNow(plan.end),
        antiSnipingEnabled: Boolean(plan.antiSniping),
        antiSnipingWindowSeconds: 30,
        extensionSeconds: 120,
        ...(lot.reserve ? { reservePrice: lot.reserve } : {}),
        ...(lot.buyNow ? { buyNowPrice: lot.buyNow } : {}),
      },
    });
    lot.id = auction.id;

    await call("POST", `/api/seller/auctions/${auction.id}/submit`, { token: seller });
    if (["live", "upcoming", "closing"].includes(plan.state)) {
      await call("POST", `/api/admin/auctions/${auction.id}/approve`, { token: tokens.admin });
    }
    for (const [who, max] of plan.autoBids ?? []) {
      await call("POST", `/api/auctions/${auction.id}/auto-bid`, { token: tokens[who], body: { maxAmount: max } });
    }
    for (const who of plan.bids ?? []) {
      await placeBid(auction.id, who, tokens);
      await sleep(150);
    }
    if (closingNow) closing.push(lot);
    console.log(`  ${plan.state.padEnd(8)} ${lot.name}  (${files.length} photo${files.length === 1 ? "" : "s"})`);
  }

  for (const [who, names] of Object.entries(WATCHES)) {
    for (const name of names) {
      const lot = LOTS.find((each) => each.name === name);
      if (!lot?.id) continue;
      await call("POST", `/api/auctions/${lot.id}/watch`, { token: tokens[who] }).catch(() => {});
    }
  }

  if (closing.length > 0) console.log(`• waiting for ${closing.length} lots to close`);
  for (const lot of closing) {
    await waitFor(`${lot.name} to close`, async () => {
      const { auction } = await call("GET", `/api/auctions/${lot.id}`);
      return ["ENDED", "COMPLETED"].includes(auction.status);
    }, 3 * 60_000);
  }

  for (const lot of closing) {
    const winner = lot.plan.bids.at(-1);
    const entry = await waitFor(`a payment for ${lot.name}`, async () =>
      (await call("GET", "/api/users/me/payments", { token: tokens[winner] })).find((each) => each.payment.auctionId === lot.id));
    await payThroughVnpay(API, call, entry.payment.id, tokens[winner]);

    const order = await waitFor(`a paid order for ${lot.name}`, async () =>
      (await call("GET", "/api/seller/orders", { token: tokens[lot.seller] }))
        .find((each) => each.order.auctionId === lot.id && each.order.status === "PAID"));
    await call("POST", `/api/seller/orders/${order.order.id}/ship`, { token: tokens[lot.seller] });

    if (lot.plan.after === "reviewed") {
      await call("POST", `/api/users/me/orders/${order.order.id}/received`, { token: tokens[winner] });
      await call("POST", `/api/users/me/orders/${order.order.id}/review`, { token: tokens[winner], body: lot.plan.review });
    }
    console.log(`  ${lot.plan.after.padEnd(8)} ${lot.name}  (won by ${emailOf(winner)})`);
  }

  console.log(`
Done.
  admin@gmail.com / admin123                     admin console (also: admin@nexbid.test / ${PASSWORD})
  kicks@ saigon@ hanoiart@ tempo@nexbid.test     new sellers   (password ${PASSWORD})
  linh@ minh@ khoa@ chloe@ raj@ emma@nexbid.test new bidders   (password ${PASSWORD})`);
}

main().catch((error) => {
  console.error(`\nEnriching failed: ${error.message}`);
  process.exit(1);
});
