#!/usr/bin/env node
// EN: Fills NexBid with a demo catalogue: real photos, live lots mid-bidding, lots opening later, an
//     approval queue, and a buyer who has won, paid and been outbid. Everything goes through the public
//     API, so bids, auto bids, closes, payments, notifications and the audit log are the real thing. Only
//     the SELLER and ADMIN roles are granted in SQL — the API deliberately has no way to do that.
// VI: Đổ dữ liệu demo vào NexBid: ảnh thật, lô đang đấu giá dở, lô sắp mở, hàng chờ duyệt, và một người
//     mua đã thắng, đã trả tiền, đã bị vượt giá. Mọi thứ đi qua API công khai, nên bid, auto bid, đóng
//     phiên, thanh toán, thông báo và audit log đều là thật. Chỉ vai trò SELLER và ADMIN được cấp bằng SQL —
//     API cố ý không có cách làm việc đó.
//
//   node docs/demo/seed.mjs                              # dev services + backend on :8080
//   STACK=docker node docs/demo/seed.mjs                 # the stack from `docker compose up`
//   NEXBID_API_URL=http://localhost:18080 node docs/demo/seed.mjs
//
// EN: Needs Node 20+, Docker, and internet access for the photos (images.unsplash.com). Takes about a minute.
// VI: Cần Node 20+, Docker, và internet để tải ảnh (images.unsplash.com). Mất khoảng một phút.

import { execFileSync } from "node:child_process";
import path from "node:path";
import { fileURLToPath } from "node:url";

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "../..");
const API = (process.env.NEXBID_API_URL ?? "http://localhost:8080").replace(/\/$/, "");
const COMPOSE_FILE = process.env.STACK === "docker" ? "docker-compose.yml" : "docker/compose.yaml";
const PASSWORD = process.env.DEMO_PASSWORD ?? "nexbid-demo";
const DOMAIN = "nexbid.test";

// EN: `pin` is the account to sign in with — it buys and sells. / VI: `pin` là tài khoản để đăng nhập — vừa mua vừa bán.
const PEOPLE = {
  admin: { name: "Nora Adams", role: "ADMIN" },
  pin: { name: "Pin Le", role: "SELLER" },
  atelier: { name: "Atelier Nord", role: "SELLER" },
  vault: { name: "The Vault Collective", role: "SELLER" },
  lumen: { name: "Lumen Optics", role: "SELLER" },
  alex: { name: "Alex Turner" },
  john: { name: "John Mercer" },
  mika: { name: "Mika Tanaka" },
  sara: { name: "Sara Novak" },
  dmitri: { name: "Dmitri Volkov" },
};

// EN: Unsplash ids, each checked against its product (Unsplash License). / VI: Id ảnh Unsplash, mỗi ảnh đã đối chiếu với sản phẩm (Unsplash License).
const photo = (id) => `https://images.unsplash.com/${id}?fm=jpg&fit=crop&w=1400&q=80`;

// EN: Times are minutes from now; bids are placed in order, each at the lowest amount the lot accepts.
// VI: Thời gian tính bằng phút kể từ bây giờ; bid được đặt theo thứ tự, mỗi lượt ở mức thấp nhất lô chấp nhận.
const LOTS = [
  {
    seller: "atelier", category: "technology", condition: "LIKE_NEW",
    name: 'MacBook Pro 16" M3 Max',
    description: "Space Black, 48GB unified memory, 1TB SSD. Purchased new in March and used in a smoke-free studio for colour grading. Battery health reports 97% with 41 cycles. Ships in the original box with the 140W adapter, braided USB-C cable and unused documentation.",
    photos: ["photo-1517336714731-489689fd1ca8", "photo-1651241680016-cc9e407e7dc3", "photo-1531297484001-80022131f5a1"],
    price: 45_000_000, step: 500_000,
    plan: { state: "live", start: -20 * 60, end: 42, bids: ["alex", "pin", "john", "pin", "alex"] },
  },
  {
    seller: "vault", category: "watches", condition: "LIKE_NEW",
    name: "Rolex Submariner Date 126610LN",
    description: "2023 production, 41mm Oystersteel case on an Oyster bracelet with Glidelock clasp. Unpolished with crisp lugs and a clean bezel insert. Runs +2 seconds per day on a recent timegrapher check. Complete set: boxes, warranty card, swing tags and booklets.",
    photos: ["photo-1662384197911-e82189f4dc60", "photo-1662384201132-c181be518cd2", "photo-1662384205880-2c7a9879cc0c"],
    price: 250_000_000, step: 2_000_000,
    plan: { state: "live", start: -30 * 60, end: 5 * 60, autoBids: [["dmitri", 270_000_000]], bids: ["mika", "pin", "mika"] },
  },
  {
    seller: "lumen", category: "cameras", condition: "LIKE_NEW",
    name: "Leica Q3",
    description: "60MP full-frame compact with the fixed Summilux 28mm f/1.7 ASPH. Shutter count just under 3,200. Body and lens barrel are free of brassing. Includes two batteries, the original charger, strap, thumb rest and a quick-release plate.",
    photos: ["photo-1676276549741-c3de6775e782", "photo-1683309615950-599999bdaa45", "photo-1683309137836-3f0a640692b4"],
    price: 95_000_000, step: 1_000_000,
    plan: { state: "live", start: -22 * 60, end: 18, antiSniping: true, bids: ["sara", "pin", "sara", "pin"] },
  },
  {
    seller: "vault", category: "sneakers", condition: "NEW",
    name: 'Air Jordan 1 Retro High OG "Chicago"',
    description: "US 10, deadstock and never laced. Stored flat in a climate-controlled case since release day. Original box is intact with a clean label. Includes both lace sets and the original tissue.",
    photos: ["photo-1686931463322-916e93213d86", "photo-1731132198530-e4b2dc51d511", "photo-1656944227421-416b1d2186c9"],
    price: 7_500_000, step: 250_000,
    plan: { state: "live", start: -3 * 60, end: 26 * 60, bids: ["alex", "mika"] },
  },
  {
    seller: "lumen", category: "cameras", condition: "GOOD",
    name: "Sony α7R V Body",
    description: "61MP sensor with the AI processing unit, roughly 6,400 actuations. Light handling marks on the base plate, sensor professionally cleaned last month. Bundled with three genuine batteries, a dual charger and an L-bracket.",
    photos: ["photo-1647920564028-5756c7af4bd1", "photo-1692030179940-c65af6a0e6b0"],
    price: 52_000_000, step: 500_000,
    plan: { state: "live", start: -60, end: 2 * 24 * 60, bids: [] },
  },
  {
    seller: "vault", category: "watches", condition: "GOOD",
    name: "Vintage Omega Seamaster 300",
    description: "1966 reference with an even tropical dial and original tritium plots aged to warm cream. Serviced two years ago by an independent specialist; runs within COSC tolerance. Presented on a period-correct leather strap with the original bracelet included.",
    photos: ["photo-1769472564136-b73481c3b8a4", "photo-1774744287122-4a0c96786dbd", "photo-1762708052083-757ae92b7203"],
    price: 80_000_000, step: 1_000_000,
    plan: { state: "live", start: -40 * 60, end: 7 * 60, autoBids: [["pin", 95_000_000]], bids: ["john", "sara", "dmitri", "alex", "john"] },
  },
  {
    seller: "vault", category: "collectibles", condition: "GOOD",
    name: "Charizard Base Set Holo — PSA 8",
    description: "1999 unlimited print, graded PSA 8 NM-MT with excellent centring and a clean holofoil surface. The certification number verifies against the PSA registry. Ships double-boxed with a card saver.",
    photos: ["photo-1613771404784-3a5686aa2be3"],
    price: 18_000_000, step: 250_000,
    plan: { state: "live", start: -5 * 60, end: 3 * 60, bids: ["dmitri", "alex"] },
  },
  {
    seller: "atelier", category: "fashion", condition: "LIKE_NEW",
    name: "Hermès Birkin 30 Togo Leather",
    description: "Gold Togo leather with palladium hardware. Corners are sharp with no rubbing and the hardware retains its factory film. Complete with dust bag, lock, keys, clochette, rain cover and box. Independently authenticated.",
    photos: ["photo-1590874103328-eac38a683ce7", "photo-1691480150204-66dd1eb77391", "photo-1637759292654-a12cb2be085e"],
    price: 420_000_000, step: 5_000_000,
    plan: { state: "live", start: -10 * 60, end: 30 * 60, bids: ["mika", "sara"] },
  },
  {
    seller: "atelier", category: "technology", condition: "LIKE_NEW",
    name: "Focal Utopia 2022 Headphones",
    description: "Reference open-back headphones with pure beryllium M-shaped domes. Pads and headband show no flaking. Includes the hard carry case and both the unbalanced and balanced XLR cables.",
    photos: ["photo-1505740420928-5e560c06d30e", "photo-1583394838336-acd977736f90", "photo-1484704849700-f032a568e944"],
    price: 60_000_000, step: 500_000,
    plan: { state: "live", start: -23 * 60, end: 55, bids: ["john"] },
  },
  {
    seller: "atelier", category: "technology", condition: "LIKE_NEW",
    name: "Apple Vision Pro 1TB",
    description: "1TB configuration with both the Solo Knit and Dual Loop bands. Under 20 hours of total use; light seal and cushions are pristine. Ships with the travel case, battery, 30W adapter and polishing cloth.",
    photos: ["photo-1706990769341-d450bb0c52b7", "photo-1707167144646-cea7d2027ef7", "photo-1707228773518-7ca0492d0c4d"],
    price: 48_000_000, step: 500_000,
    plan: { state: "upcoming", start: 20 * 60, end: 68 * 60 },
  },
  {
    seller: "lumen", category: "cameras", condition: "GOOD",
    name: "Canon AE-1 Program + FD 50mm f/1.4",
    description: "Fully serviced film body with fresh light seals and an accurate shutter across all speeds. Paired with a clean FD 50mm f/1.4 showing no haze, fungus or separation. Includes strap, cap and a fresh battery.",
    photos: ["photo-1655762019268-fc8a4220506d", "photo-1678083894886-ba818cdcfcfb"],
    price: 4_000_000, step: 100_000,
    plan: { state: "upcoming", start: 3 * 60, end: 27 * 60 },
  },
  {
    seller: "pin", category: "art", condition: "NEW",
    name: 'Mara Ellis — "Northfall No. 7"',
    description: "Six-colour screen print on 300gsm Somerset Satin, 70 × 100cm. Numbered 14/40 in pencil and signed by the artist. Never framed, stored flat in archival tissue. Includes the certificate of authenticity.",
    photos: ["photo-1579783902614-a3fb3927b6a5", "photo-1549289524-06cf8837ace5", "photo-1577720580479-7d839d829c73"],
    price: 10_000_000, step: 250_000,
    plan: { state: "upcoming", start: 26 * 60, end: 98 * 60 },
  },
  {
    seller: "pin", category: "sneakers", condition: "GOOD",
    name: "Sacai x Nike LDWaffle — US 9.5",
    description: "Worn twice indoors with original insoles intact and no creasing on the toe box. Midsoles are bright with no yellowing. Comes with the original box, both lace options and the hangtag.",
    photos: ["photo-1724921195080-31c2d6d82a16", "photo-1724921195757-ca6918647a5e", "photo-1724921196547-08aee1bab2da"],
    price: 4_500_000, step: 100_000,
    plan: { state: "pending", start: 2 * 24 * 60, end: 5 * 24 * 60 },
  },
  {
    seller: "vault", category: "collectibles", condition: "FAIR",
    name: "1974 Fender Stratocaster — Olympic White",
    description: "All-original hardware and electronics with a lightly checked nitro finish aged to a warm cream. Frets show honest play wear with plenty of life remaining. Ships in the original hard case.",
    photos: ["photo-1642322356665-974c658cee81", "photo-1606041281659-3f2cec516ac0"],
    price: 75_000_000, step: 1_000_000,
    plan: { state: "pending", start: 3 * 24 * 60, end: 6 * 24 * 60 },
  },
  {
    seller: "pin", category: "technology", condition: "GOOD",
    name: "Vintage Tube Amplifier",
    description: "Integrated valve amplifier, recapped two years ago. Warm output with no hum at idle. Tubes tested and matched.",
    photos: [],
    price: 12_000_000, step: 250_000,
    plan: { state: "rejected", start: 24 * 60, end: 4 * 24 * 60,
      reason: "No photos yet. Add the front and back panels and the tube serials so bidders can check the recap." },
  },
  {
    seller: "pin", category: "cameras", condition: "LIKE_NEW",
    name: "Voigtländer 35mm f/1.2 Nokton",
    description: "Manual focus M-mount lens with clean glass throughout — no haze, fungus or separation. Aperture blades are snappy and oil-free. Includes both caps, the original hood and the box.",
    photos: [],
    price: 18_000_000, step: 250_000,
    plan: { state: "draft", start: 4 * 24 * 60, end: 7 * 24 * 60 },
  },
  {
    seller: "atelier", category: "technology", condition: "LIKE_NEW",
    name: "Tokyo60 Limited Mechanical Keyboard",
    description: "Hand-assembled 60% board in anodised silver, one of 300 units from the original group buy. Lubed and filmed linear switches, brass weight and a foam-dampened case. Includes the aluminium carry case.",
    photos: ["photo-1602025882379-e01cf08baa51", "photo-1595225476474-87563907a212", "photo-1599356170802-dea0583b7cea"],
    price: 6_500_000, step: 100_000,
    plan: { state: "closing", bids: ["alex", "pin"], pay: true },
  },
  {
    seller: "vault", category: "collectibles", condition: "GOOD",
    name: "First Pressing Vinyl Archive — 12 LPs",
    description: "Twelve first-pressing LPs spanning jazz and soul, all graded VG+ to NM with original inner sleeves. Jackets show light shelf wear only. Track-by-track condition notes are included.",
    photos: ["photo-1603048588665-791ca8aea617", "photo-1488841714725-bb4c32d1ac94", "photo-1483412033650-1015ddeb83d1"],
    price: 15_000_000, step: 250_000,
    plan: { state: "closing", bids: ["pin", "john", "pin"], pay: false },
  },
];

// EN: Lots that must finish during seeding close this many seconds after they open.
// VI: Các lô phải kết thúc ngay trong lúc seed sẽ đóng sau từng này giây kể từ khi mở.
const CLOSING_SECONDS = 25;
const WATCHED_BY_PIN = ["Rolex Submariner Date 126610LN", "Hermès Birkin 30 Togo Leather", "Apple Vision Pro 1TB", "Sony α7R V Body"];

const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));
const minutesFromNow = (minutes) => new Date(Date.now() + minutes * 60_000).toISOString();
const email = (key) => `${key}@${DOMAIN}`;
const vnd = (amount) => `${Number(amount).toLocaleString("en-US")} VND`;

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
    // EN: Spec §20.2 allows 10 bids per 10 seconds a person; wait the window out. / VI: Spec §20.2 cho 10 bid mỗi 10 giây một người; chờ hết cửa sổ.
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

// EN: Downloads a few photos at a time; a photo that fails is left out rather than failing the seed.
// VI: Tải vài ảnh một lúc; ảnh nào lỗi thì bỏ qua thay vì làm hỏng cả lần seed.
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
      // EN: Someone's auto bid moved the price in between; read it again. / VI: Auto bid của ai đó vừa đổi giá; đọc lại.
      if (error.code !== "BID_TOO_LOW" || attempt >= 3) throw error;
    }
  }
}

async function main() {
  console.log(`Seeding demo data into ${API} (SQL via ${COMPOSE_FILE})`);

  try {
    await call("GET", "/api/server-time");
  } catch {
    throw new Error(`No backend at ${API}. Start it first (see README → Run).`);
  }
  if (sql(`SELECT count(*) FROM users WHERE email LIKE '%@${DOMAIN}'`) !== "0") {
    throw new Error(`Demo accounts (@${DOMAIN}) already exist. Wipe the data first: README → Demo data.`);
  }

  console.log("• accounts");
  for (const [key, person] of Object.entries(PEOPLE)) {
    await call("POST", "/api/auth/register", { body: { fullName: person.name, email: email(key), password: PASSWORD } });
  }
  const grants = Object.entries(PEOPLE)
    .filter(([, person]) => person.role)
    .map(([key, person]) => `('${email(key)}', '${person.role}')`)
    .join(", ");
  sql(`INSERT INTO user_roles (user_id, role_id)
       SELECT u.id, r.id FROM users u JOIN roles r ON (u.email, r.name) IN (${grants})
       ON CONFLICT DO NOTHING`);
  // EN: Signed in after the grant, so each token carries its roles. / VI: Đăng nhập sau khi cấp quyền, để token mang theo vai trò.
  const tokens = {};
  for (const key of Object.keys(PEOPLE)) {
    tokens[key] = (await call("POST", "/api/auth/login", { body: { email: email(key), password: PASSWORD } })).accessToken;
  }

  const categories = Object.fromEntries((await call("GET", "/api/categories")).map((category) => [category.slug, category.id]));

  console.log("• photos");
  const photos = await downloadPhotos(LOTS.flatMap((lot) => lot.photos));

  console.log("• lots");
  const closing = [];
  // EN: Closing lots go last, so their short clock starts only once everything else is in.
  // VI: Các lô sắp đóng đi sau cùng, để đồng hồ ngắn của chúng chỉ bắt đầu khi mọi thứ khác đã xong.
  const ordered = [...LOTS.filter((lot) => lot.plan.state !== "closing"), ...LOTS.filter((lot) => lot.plan.state === "closing")];
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
      },
    });
    lot.id = auction.id;

    if (plan.state !== "draft") await call("POST", `/api/seller/auctions/${auction.id}/submit`, { token: seller });
    if (plan.state === "rejected") {
      await call("POST", `/api/admin/auctions/${auction.id}/reject`, { token: tokens.admin, body: { reason: plan.reason } });
    }
    // EN: A start time already passed opens the lot on approval (spec §7.5). / VI: Giờ mở đã qua thì lô mở ngay khi được duyệt (spec §7.5).
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

  for (const name of WATCHED_BY_PIN) {
    await call("POST", `/api/auctions/${LOTS.find((lot) => lot.name === name).id}/watch`, { token: tokens.pin });
  }

  console.log(`• waiting for ${closing.length} lots to close`);
  for (const lot of closing) {
    const deadline = Date.now() + 3 * 60_000;
    for (;;) {
      const { auction } = await call("GET", `/api/auctions/${lot.id}`);
      if (["ENDED", "COMPLETED"].includes(auction.status)) break;
      if (Date.now() > deadline) throw new Error(`${lot.name} did not close; is the backend's scheduler running?`);
      await sleep(1000);
    }
  }

  for (const lot of closing.filter((each) => each.plan.pay)) {
    const deadline = Date.now() + 60_000;
    let entry;
    while (!(entry = (await call("GET", "/api/users/me/payments", { token: tokens.pin })).find((each) => each.payment.auctionId === lot.id))) {
      if (Date.now() > deadline) throw new Error(`No payment was opened for ${lot.name}`);
      await sleep(1000);
    }
    await call("POST", `/api/payments/${entry.payment.id}/pay`, { token: tokens.pin, body: { outcome: "SUCCESS" } });
    console.log(`  paid     ${lot.name}  (${vnd(entry.payment.amount)})`);
  }

  console.log(`
Done. Every account uses the password "${PASSWORD}".
  ${email("pin")}      buyer and seller: bids, an auto bid, wins (one paid, one to pay), watchlist, own listings
  ${email("admin")}    admin: approval queue, users, audit log
  ${email("atelier")}  ${email("vault")}  ${email("lumen")}   sellers
  ${email("alex")}  ${email("john")}  ${email("mika")}  ${email("sara")}  ${email("dmitri")}   other bidders`);
}

main().catch((error) => {
  console.error(`\nSeeding failed: ${error.message}`);
  process.exit(1);
});
