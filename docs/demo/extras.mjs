#!/usr/bin/env node
// EN: Fills what seed.mjs and enrich.mjs leave empty, through the real API: delivery addresses and bank accounts,
//     seller requests (pending, approved, refused), saved searches and followed sellers, questions and answers, lot
//     reports, shipping vouchers, a set, a free-shipping lot and timed drafts, and four sales that run to the end:
//       1. paid, shipped, talked about, received, reviewed in detail both ways, then paid out to the seller;
//       2. paid with a voucher, then disputed and refunded by bank transfer;
//       3. never paid, so the runner-up takes a second-chance offer, is charged twice by VNPay and sent the extra back;
//       4. paid and waiting to be shipped.
//     Also turns on two-step sign-in for secure@nexbid.test, makes the browser-push key and clears a shill check.
//     Safe to run again: a part whose data is already there is skipped.
// VI: Lấp những gì seed.mjs và enrich.mjs để trống, qua API thật: địa chỉ nhận hàng và tài khoản ngân hàng, yêu cầu
//     bán hàng (chờ, duyệt, từ chối), bộ lọc đã lưu và người bán được theo dõi, hỏi đáp, báo cáo lô, voucher phí ship,
//     một bộ nhiều món, một lô miễn phí ship và các bản nháp hẹn giờ, và bốn giao dịch chạy tới cuối:
//       1. trả tiền, gửi hàng, nhắn tin, nhận hàng, đánh giá chi tiết hai chiều, rồi chi trả cho người bán;
//       2. trả bằng voucher, rồi khiếu nại và được hoàn tiền bằng chuyển khoản;
//       3. không trả tiền, nên người về nhì nhận đề nghị cơ hội thứ hai, bị VNPay thu hai lần và được trả lại phần thừa;
//       4. đã trả tiền, chờ gửi hàng.
//     Đồng thời bật đăng nhập hai bước cho secure@nexbid.test, tạo khoá thông báo đẩy và xử lý một nghi vấn tự trả giá.
//     Chạy lại được: phần nào đã có dữ liệu thì bỏ qua.
//
//   node docs/demo/extras.mjs                 # dev services + backend on :8080 (reads backend/.env for VNPay)
//   STACK=docker node docs/demo/extras.mjs    # the stack from `docker compose up`
//
// EN: Run seed.mjs and enrich.mjs first. Takes about three minutes: one sale waits for the unpaid-payment job.
// VI: Chạy seed.mjs và enrich.mjs trước. Mất khoảng ba phút: một giao dịch phải chờ job xử lý khoản chưa trả.

import { createHmac } from "node:crypto";
import { execFileSync } from "node:child_process";
import path from "node:path";
import { fileURLToPath } from "node:url";

import { ensureDeposit, paidReply, payThroughVnpay, sendIpn } from "./vnpay.mjs";

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "../..");
const API = (process.env.NEXBID_API_URL ?? "http://localhost:8080").replace(/\/$/, "");
const COMPOSE_FILE = process.env.STACK === "docker" ? "docker-compose.yml" : "docker/compose.yaml";
const PASSWORD = process.env.DEMO_PASSWORD ?? "nexbid-demo";
const PEOPLE = ["admin", "pin", "atelier", "vault", "lumen", "kicks", "saigon", "hanoiart", "tempo",
  "alex", "john", "mika", "sara", "dmitri", "linh", "minh", "khoa", "chloe", "raj", "emma"];
const NEW_PEOPLE = { secure: "Secure Buyer", lan: "Lan Hoang", quang: "Quang Vo" };
const emailOf = (key) => `${key}@nexbid.test`;
const photo = (id) => `https://images.unsplash.com/${id}?fm=jpg&fit=crop&w=1400&q=80`;
const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));
const minutesFromNow = (minutes) => new Date(Date.now() + minutes * 60_000).toISOString();
const quote = (text) => `'${String(text).replace(/'/g, "''")}'`;

const STREETS = ["12 Nguyen Hue", "45 Le Loi", "8 Hang Bai", "102 Tran Hung Dao", "27 Pasteur", "3 Ly Thuong Kiet",
  "66 Vo Van Tan", "19 Hai Ba Trung", "150 Nguyen Trai", "5 Dong Khoi"];
const PLACES = [["Ben Nghe", "District 1", "Ho Chi Minh City"], ["Trang Tien", "Hoan Kiem", "Ha Noi"],
  ["Hai Chau 1", "Hai Chau", "Da Nang"], ["Phuong 6", "District 3", "Ho Chi Minh City"]];
const BANKS = ["Vietcombank", "Techcombank", "MB Bank", "ACB", "VPBank", "BIDV"];

// EN: The four sales. Bids alternate so the last bidder wins and the one before is the runner-up.
// VI: Bốn giao dịch. Lượt trả giá xen kẽ để người trả cuối thắng và người trước đó về nhì.
const SALES = [
  {
    key: "complete", seller: "lumen", category: "cameras", condition: "GOOD", price: 4_000_000, step: 200_000,
    name: "Polaroid SX-70 Land Camera", freeShipping: true,
    description: "The folding SLR instant camera in brown leather and chrome, serviced this year with new rollers. Takes SX-70 and 600 film with a filter. Comes with the original strap and a pack of fresh film.",
    photos: ["photo-1526170375885-4d8ecf77b99f", "photo-1495121553079-4c61bcce1894"],
    bids: ["mika", "alex", "mika", "alex"],
  },
  {
    key: "dispute", seller: "atelier", category: "technology", condition: "LIKE_NEW", price: 9_000_000, step: 250_000,
    name: "Fujifilm X100V Silver",
    description: "Silver X100V with the 23mm f/2 lens, shutter count around 5,000. Includes two batteries, the lens hood and a leather half case. Sensor clean, no dust in the viewfinder.",
    photos: ["photo-1606986628253-04e2ee9f3df9", "photo-1617005082133-548c4dd27f35"],
    bids: ["john", "sara", "john", "sara"],
  },
  {
    key: "second", seller: "tempo", category: "watches", condition: "GOOD", price: 60_000_000, step: 1_000_000,
    name: "Omega Seamaster Diver 300M",
    description: "Blue wave dial, 42mm, ceramic bezel. Serviced by an Omega centre last year, keeps excellent time. Comes on the steel bracelet with all links, plus the box and the service card.",
    photos: ["photo-1523170335258-f5ed11844a49", "photo-1622434641406-a158123450f9"],
    bids: ["raj", "chloe", "raj", "chloe"],
  },
  {
    key: "waiting", seller: "hanoiart", category: "art", condition: "GOOD", price: 5_000_000, step: 200_000,
    name: "Hanoi Old Quarter — Watercolour",
    description: "Original watercolour on cotton paper, 40 x 55 cm, signed lower right. Rooftops of Hang Bac street at dusk. Framed under glass in natural oak, ready to hang.",
    photos: ["photo-1579783902614-a3fb3927b6a5"],
    bids: ["linh", "emma", "linh"],
  },
];

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

async function waitFor(what, read, timeoutMs = 90_000) {
  const deadline = Date.now() + timeoutMs;
  for (;;) {
    const value = await read();
    if (value) return value;
    if (Date.now() > deadline) throw new Error(`Timed out waiting for ${what}`);
    await sleep(1500);
  }
}

/** EN: The authenticator app's code (RFC 6238). / VI: Mã của ứng dụng xác thực (RFC 6238). */
function totp(secret, at = Date.now()) {
  const alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
  let bits = "";
  for (const char of secret.replace(/=/g, "").toUpperCase()) bits += alphabet.indexOf(char).toString(2).padStart(5, "0");
  const key = Buffer.from(bits.match(/.{8}/g).map((byte) => parseInt(byte, 2)));
  const counter = Buffer.alloc(8);
  counter.writeBigUInt64BE(BigInt(Math.floor(at / 30_000)));
  const hash = createHmac("sha1", key).update(counter).digest();
  const offset = hash[hash.length - 1] & 0x0f;
  const binary = (hash.readUInt32BE(offset) & 0x7fffffff) % 1_000_000;
  return String(binary).padStart(6, "0");
}

async function downloadPhotos(ids) {
  const photos = new Map();
  for (const id of new Set(ids)) {
    try {
      const response = await fetch(photo(id));
      if (!response.ok) throw new Error(`HTTP ${response.status}`);
      photos.set(id, new Blob([await response.arrayBuffer()], { type: "image/jpeg" }));
    } catch (error) {
      console.warn(`  ! photo ${id} skipped: ${error.message}`);
    }
  }
  return photos;
}

async function placeBid(lotId, token) {
  await ensureDeposit(API, call, lotId, token);
  for (let attempt = 1; ; attempt++) {
    const { minimumNextBid } = await call("GET", `/api/auctions/${lotId}`);
    try {
      return await call("POST", `/api/auctions/${lotId}/bids`, { token, body: { amount: minimumNextBid } });
    } catch (error) {
      if (error.code !== "BID_TOO_LOW" || attempt >= 3) throw error;
    }
  }
}

const has = (statement) => Number(sql(statement)) > 0;

async function main() {
  console.log(`Filling the rest of ${API} (SQL via ${COMPOSE_FILE})`);
  await call("GET", "/api/server-time").catch(() => {
    throw new Error(`No backend at ${API}. Start it first.`);
  });

  console.log("• accounts");
  for (const [key, name] of Object.entries(NEW_PEOPLE)) {
    await call("POST", "/api/auth/register", { body: { fullName: name, email: emailOf(key), password: PASSWORD } })
      .catch((error) => {
        if (error.code !== "EMAIL_ALREADY_EXISTS") throw error;
      });
  }
  const tokens = {};
  const ids = {};
  for (const key of [...PEOPLE, ...Object.keys(NEW_PEOPLE)]) {
    try {
      const session = await call("POST", "/api/auth/login", { body: { email: emailOf(key), password: PASSWORD } });
      if (!session.accessToken) continue; // EN: Two-step accounts sign in with a code. / VI: Tài khoản hai bước đăng nhập bằng mã.
      tokens[key] = session.accessToken;
      ids[key] = (await call("GET", "/api/users/me", { token: tokens[key] })).id;
    } catch (error) {
      throw new Error(`Cannot sign in as ${emailOf(key)} (${error.message}). Run seed.mjs and enrich.mjs first.`);
    }
  }
  ids.lumen ??= sql(`SELECT id FROM users WHERE email = ${quote(emailOf("lumen"))}`);

  console.log("• addresses and bank accounts");
  let index = 0;
  for (const [key, token] of Object.entries(tokens)) {
    const current = await call("GET", "/api/users/me/address", { token });
    if (!current) {
      const [ward, district, province] = PLACES[index % PLACES.length];
      await call("PUT", "/api/users/me/address", {
        token,
        body: { recipient: key[0].toUpperCase() + key.slice(1) + " Demo", phone: `09${String(10_000_000 + index * 7919).slice(0, 8)}`,
          line: STREETS[index % STREETS.length], ward, district, province },
      });
    }
    // EN: Everyone: sellers are paid there, buyers are refunded there. / VI: Mọi người: người bán nhận tiền ở đó, người mua được hoàn tiền ở đó.
    if (!(await call("GET", "/api/users/me/bank-account", { token }))) {
      await call("PUT", "/api/users/me/bank-account", {
        token,
        body: { bankName: BANKS[index % BANKS.length], accountNumber: String(1_000_000_000 + index * 104_729), accountHolder: `${key} demo` },
      });
    }
    index++;
  }

  console.log("• seller requests");
  if (!has("SELECT COUNT(*) FROM seller_applications")) {
    for (const [key, note] of [["linh", "I restore vintage film cameras and would like to sell a few each month."],
      ["minh", "Collector of Vietnamese lacquer art, selling part of my collection."],
      ["khoa", "Sneaker reseller."]]) {
      await call("POST", "/api/users/me/seller-application", { token: tokens[key], body: { note } });
    }
    const pending = await call("GET", "/api/admin/seller-applications", { token: tokens.admin });
    const of = (key) => pending.items.find((each) => each.userId === ids[key]);
    if (of("minh")) await call("POST", `/api/admin/seller-applications/${of("minh").id}/approve`, { token: tokens.admin });
    if (of("khoa")) {
      await call("POST", `/api/admin/seller-applications/${of("khoa").id}/reject`, {
        token: tokens.admin, body: { reason: "Please add photos of past sales and an ID check, then apply again." },
      });
    }
  }

  console.log("• saved searches and followed sellers");
  if (!has("SELECT COUNT(*) FROM saved_searches")) {
    await call("POST", "/api/alerts/searches", { token: tokens.alex, body: { q: "rolex", category: ["watches"] } });
    await call("POST", "/api/alerts/searches", { token: tokens.mika, body: { category: ["cameras"], maxPrice: 20_000_000 } });
    await call("POST", "/api/alerts/searches", { token: tokens.emma, body: { q: "dong ho", condition: ["LIKE_NEW", "NEW"] } });
    await call("POST", "/api/alerts/searches", { token: tokens.raj, body: { category: ["art"], minPrice: 3_000_000 } });
  }
  for (const [who, seller] of [["alex", "lumen"], ["mika", "lumen"], ["sara", "atelier"], ["raj", "hanoiart"],
    ["chloe", "tempo"], ["emma", "vault"], ["linh", "hanoiart"]]) {
    await call("PUT", `/api/alerts/sellers/${ids[seller]}`, { token: tokens[who] }).catch(() => {});
  }

  console.log("• questions and reports");
  const live = (await call("GET", "/api/auctions?status=ACTIVE&size=30")).items;
  if (live.length > 0 && Number(sql("SELECT COUNT(*) FROM lot_questions")) < 4) {
    const asks = [["alex", "Does it come with the original box and papers?"], ["mika", "Any scratches on the glass or the case back?"],
      ["sara", "Could you ship to Da Nang, and how is it packed?"], ["raj", "Is the battery health still above 90%?"],
      ["emma", "When was it last serviced?"], ["john", "Would you accept pickup in District 1?"]];
    for (const [position, [who, body]] of asks.entries()) {
      const lot = live[position % live.length];
      if (lot.seller.id === ids[who]) continue;
      const asked = await call("POST", `/api/auctions/${lot.auction.id}/questions`, { token: tokens[who], body: { body } }).catch(() => null);
      const sellerKey = Object.keys(ids).find((key) => ids[key] === lot.seller.id);
      if (asked?.id && sellerKey && tokens[sellerKey] && position % 2 === 0) {
        await call("POST", `/api/seller/questions/${asked.id}/answer`, {
          token: tokens[sellerKey], body: { answer: "Yes — everything shown in the photos is included, and it ships double boxed." },
        }).catch(() => {});
      }
    }
  }
  if (!has("SELECT COUNT(*) FROM lot_reports") && live.length > 2) {
    const first = await call("POST", `/api/auctions/${live[1].auction.id}/reports`, {
      token: tokens.emma, body: { reason: "NOT_AS_DESCRIBED", details: "The photos look like a different model from the one in the title." },
    }).catch(() => null);
    await call("POST", `/api/auctions/${live[2].auction.id}/reports`, {
      token: tokens.khoa, body: { reason: "COUNTERFEIT", details: "The logo font on the second photo does not match the genuine item." },
    }).catch(() => null);
    if (first?.id) {
      await call("POST", `/api/admin/reports/${first.id}/dismiss`, {
        token: tokens.admin, body: { note: "Checked with the seller: same model, the photos show the other side." },
      });
    }
  }

  console.log("• vouchers");
  if (!has("SELECT COUNT(*) FROM promo_codes")) {
    const until = new Date(Date.now() + 30 * 24 * 60 * 60_000).toISOString();
    await call("POST", "/api/admin/promo-codes", { token: tokens.admin, body: { code: "FREESHIP-30K", amount: 30_000, maxUses: 100, expiresAt: until, note: "Launch month" } });
    await call("POST", "/api/admin/promo-codes", { token: tokens.admin, body: { code: "WELCOME-20K", amount: 20_000, maxUses: 500, note: "New buyers" } });
    await call("POST", "/api/admin/promo-codes", { token: tokens.admin, body: { code: "SUMMER-50K", amount: 50_000, maxUses: 20, note: "Ended campaign" } });
    await call("POST", "/api/admin/promo-codes/SUMMER-50K/active", { token: tokens.admin, body: { active: false } });
  }

  const categories = Object.fromEntries((await call("GET", "/api/categories")).map((category) => [category.slug, category.id]));
  const existing = new Set(sql("SELECT name FROM products").split("\n").filter(Boolean));

  console.log("• a set, and timed drafts");
  if (!existing.has("Bat Trang Tea Set")) {
    const product = await call("POST", "/api/seller/products", {
      token: tokens.saigon,
      body: {
        name: "Bat Trang Tea Set", categoryId: categories.collectibles, condition: "LIKE_NEW", publishNow: true,
        description: "Hand-painted blue and white Bat Trang porcelain: a teapot, six cups and a serving tray, from a family kiln in the 1990s. No chips or hairlines; the lid fits snugly.",
        items: [{ name: "Teapot", quantity: 1 }, { name: "Tea cup", quantity: 6 }, { name: "Serving tray", quantity: 1 }],
      },
    });
    const lot = await call("POST", "/api/seller/auctions", {
      token: tokens.saigon,
      body: { productId: product.id, startingPrice: 2_500_000, minimumIncrement: 100_000, startTime: minutesFromNow(-30),
        endTime: minutesFromNow(3 * 24 * 60), freeShipping: true },
    });
    await call("POST", `/api/seller/auctions/${lot.id}/submit`, { token: tokens.saigon });
    await call("POST", `/api/admin/auctions/${lot.id}/approve`, { token: tokens.admin });
    await placeBid(lot.id, tokens.emma);
  }
  if (!has("SELECT COUNT(*) FROM auctions WHERE submit_at IS NOT NULL")) {
    const drafts = [];
    for (const name of ["Vintage Zippo Lighter 1978", "Seiko 5 Sports SNK809"]) {
      if (existing.has(name)) continue;
      const product = await call("POST", "/api/seller/products", {
        token: tokens.kicks,
        body: { name, categoryId: categories.collectibles, condition: "GOOD", publishNow: true,
          description: "Draft lot for the next weekend sale. Photos and final description coming with the listing." },
      });
      drafts.push((await call("POST", "/api/seller/auctions", {
        token: tokens.kicks,
        body: { productId: product.id, startingPrice: 800_000, minimumIncrement: 50_000, startTime: minutesFromNow(3 * 24 * 60),
          endTime: minutesFromNow(5 * 24 * 60) },
      })).id);
    }
    if (drafts.length > 0) {
      await call("POST", "/api/seller/auctions/batch", {
        token: tokens.kicks, body: { auctionIds: drafts, submit: true, submitAt: minutesFromNow(2 * 24 * 60) },
      });
    }
  }

  const todo = SALES.filter((sale) => !existing.has(sale.name));
  if (todo.length > 0) {
    console.log("• sales");
    const photos = await downloadPhotos(todo.flatMap((sale) => sale.photos));
    for (const sale of todo) {
      const token = tokens[sale.seller];
      const product = await call("POST", "/api/seller/products", {
        token,
        body: { name: sale.name, description: sale.description, categoryId: categories[sale.category], condition: sale.condition, publishNow: true },
      });
      const files = sale.photos.filter((id) => photos.has(id));
      if (files.length > 0) {
        const form = new FormData();
        files.forEach((id, position) => form.append("files", photos.get(id), `photo-${position + 1}.jpg`));
        await call("POST", `/api/seller/products/${product.id}/images`, { token, form });
      }
      const lot = await call("POST", "/api/seller/auctions", {
        token,
        body: { productId: product.id, startingPrice: sale.price, minimumIncrement: sale.step, startTime: minutesFromNow(-60),
          endTime: new Date(Date.now() + 45_000).toISOString(), freeShipping: Boolean(sale.freeShipping) },
      });
      sale.id = lot.id;
      await call("POST", `/api/seller/auctions/${lot.id}/submit`, { token });
      await call("POST", `/api/admin/auctions/${lot.id}/approve`, { token: tokens.admin });
      for (const who of sale.bids) {
        await placeBid(lot.id, tokens[who]);
        await sleep(200);
      }
      console.log(`  closing  ${sale.name}`);
    }

    console.log("  waiting for the lots to close");
    for (const sale of todo) {
      await waitFor(`${sale.name} to close`, async () =>
        ["ENDED", "COMPLETED"].includes((await call("GET", `/api/auctions/${sale.id}`)).auction.status), 3 * 60_000);
    }
    const paymentOf = (sale, who) => waitFor(`a payment for ${sale.name}`, async () =>
      (await call("GET", "/api/users/me/payments", { token: tokens[who] })).find((each) => each.payment.auctionId === sale.id && ["PENDING", "FAILED"].includes(each.payment.status)));
    const orderOf = (sale, status) => waitFor(`a ${status} order for ${sale.name}`, async () =>
      (await call("GET", "/api/seller/orders", { token: tokens[sale.seller] }))
        .find((each) => each.order.auctionId === sale.id && each.order.status === status));
    const ship = (sale, order) => call("POST", `/api/seller/orders/${order.order.id}/ship`, {
      token: tokens[sale.seller], body: { carrier: "Viettel Post", trackingNumber: `VTP${Date.now().toString().slice(-9)}` },
    });

    for (const sale of todo) {
      const winner = sale.bids.at(-1);
      const runnerUp = sale.bids.at(-2);
      if (sale.key === "complete") {
        const payment = await paymentOf(sale, winner);
        await payThroughVnpay(API, call, payment.payment.id, tokens[winner]);
        const order = await orderOf(sale, "PAID");
        const talk = (who, body) => call("POST", `/api/orders/${order.order.id}/messages`, { token: tokens[who], body: { body } });
        await talk(winner, "Hi! Could you include an extra pack of film if you have one?");
        await talk(sale.seller, "Sure, I'll add a pack of 600 film. Shipping it today.");
        await ship(sale, order);
        await talk(winner, "Got the tracking number, thank you!");
        await call("POST", `/api/users/me/orders/${order.order.id}/received`, { token: tokens[winner] });
        await call("POST", `/api/users/me/orders/${order.order.id}/review`, {
          token: tokens[winner],
          body: { rating: 5, packaging: 5, accuracy: 5, speed: 4, comment: "Exactly as described and beautifully packed. The extra film was a lovely touch." },
        });
        await call("POST", `/api/seller/orders/${order.order.id}/buyer-review`, {
          token: tokens[sale.seller], body: { rating: 5, comment: "Paid at once and kind messages. Welcome back any time." },
        });
        const due = await call("GET", "/api/admin/payouts/due", { token: tokens.admin });
        const owed = due.find((each) => each.sellerId === ids[sale.seller]);
        if (owed) {
          await call("POST", "/api/admin/payouts", {
            token: tokens.admin, body: { sellerId: ids[sale.seller], reference: `FT${Date.now().toString().slice(-10)}`, amount: owed.balance },
          });
        }
        console.log(`  paid, shipped, reviewed and paid out   ${sale.name}`);
      } else if (sale.key === "dispute") {
        const payment = await paymentOf(sale, winner);
        await call("POST", `/api/payments/${payment.payment.id}/shipping-quote`, { token: tokens[winner] });
        await call("POST", `/api/payments/${payment.payment.id}/promo`, { token: tokens[winner], body: { code: "FREESHIP-30K" } });
        await payThroughVnpay(API, call, payment.payment.id, tokens[winner]);
        const order = await orderOf(sale, "PAID");
        await ship(sale, order);
        await call("POST", `/api/users/me/orders/${order.order.id}/dispute`, {
          token: tokens[winner], body: { reason: "NOT_AS_DESCRIBED", details: "The lens has fungus inside that was not mentioned in the listing." },
        });
        const disputes = await call("GET", "/api/admin/disputes", { token: tokens.admin });
        const dispute = disputes.find((each) => each.entry.order.id === order.order.id);
        if (dispute) {
          await call("POST", `/api/admin/disputes/${dispute.dispute.id}/resolve`, {
            token: tokens.admin, body: { refund: true, note: "Fungus confirmed from the buyer's photos; refunded in full.", reference: `FT${Date.now().toString().slice(-10)}` },
          });
        }
        console.log(`  paid with a voucher, disputed, refunded  ${sale.name}`);
      } else if (sale.key === "second") {
        const unpaid = await paymentOf(sale, winner);
        sql(`UPDATE payments SET expired_at = now() - interval '1 second' WHERE id = ${quote(unpaid.payment.id)}`);
        console.log("  waiting for the unpaid-payment job (up to two minutes)");
        await waitFor("the unpaid payment to lapse", async () =>
          has(`SELECT COUNT(*) FROM payments WHERE id = ${quote(unpaid.payment.id)} AND status = 'EXPIRED'`), 3 * 60_000);
        await waitFor("the lot to be offered again", async () =>
          call("POST", `/api/seller/auctions/${sale.id}/second-chance`, { token: tokens[sale.seller] }).then(() => true, () => false), 60_000);
        const offers = await call("GET", "/api/users/me/offers", { token: tokens[runnerUp] });
        const offer = offers.find((each) => each.offer.auctionId === sale.id);
        await call("POST", `/api/users/me/offers/${offer.offer.id}/accept`, { token: tokens[runnerUp] });
        const payment = await paymentOf(sale, runnerUp);
        if (!(await call("GET", "/api/users/me/address", { token: tokens[runnerUp] }))) throw new Error("runner-up has no address");
        // EN: Two browser tabs, both paid: VNPay charges twice and the second becomes money owed back.
        // VI: Hai tab trình duyệt, cả hai đều trả: VNPay thu hai lần và lần thứ hai thành tiền phải trả lại.
        const first = await paidReply(call, payment.payment.id, tokens[runnerUp]);
        const second = await paidReply(call, payment.payment.id, tokens[runnerUp]);
        await sendIpn(API, first);
        await sendIpn(API, second);
        const strays = await call("GET", "/api/admin/stray-charges", { token: tokens.admin });
        const stray = strays.find((each) => each.txnRef === second.vnp_TxnRef && !each.refundedAt);
        if (stray) {
          await call("POST", `/api/admin/stray-charges/${stray.id}/refund`, { token: tokens.admin, body: { reference: `FT${Date.now().toString().slice(-10)}` } });
        }
        const order = await orderOf(sale, "PAID");
        await ship(sale, order);
        console.log(`  second chance, charged twice, sent back  ${sale.name}`);
      } else {
        const payment = await paymentOf(sale, winner);
        await payThroughVnpay(API, call, payment.payment.id, tokens[winner]);
        console.log(`  paid, waiting to ship                   ${sale.name}`);
      }
    }
  }

  console.log("• two-step sign-in, push key, trust check");
  if (tokens.secure && !has(`SELECT COUNT(*) FROM user_totp t JOIN users u ON u.id = t.user_id WHERE u.email = ${quote(emailOf("secure"))}`)) {
    const { secret } = await call("POST", "/api/users/me/2fa/setup", { token: tokens.secure });
    const enabled = await call("POST", "/api/users/me/2fa/enable", { token: tokens.secure, body: { code: totp(secret) } });
    console.log(`  secure@nexbid.test: authenticator key ${secret}`);
    console.log(`  recovery codes: ${enabled.recoveryCodes.join(" ")}`);
  }
  await call("GET", "/api/users/me/push/key", { token: tokens.alex });
  if (!has("SELECT COUNT(*) FROM shill_reviews")) {
    await call("POST", "/api/admin/shill-suspects/clear", {
      token: tokens.admin, body: { sellerId: ids.lumen, bidderId: ids.alex, note: "Long-time buyer of film cameras; bids spread across many sellers." },
    });
  }

  const counts = sql(`SELECT string_agg(t || ' ' || n, ', ' ORDER BY t) FROM (
      SELECT 'addresses' t, COUNT(*) n FROM user_addresses UNION ALL SELECT 'bank accounts', COUNT(*) FROM bank_accounts
      UNION ALL SELECT 'messages', COUNT(*) FROM order_messages UNION ALL SELECT 'disputes', COUNT(*) FROM order_disputes
      UNION ALL SELECT 'refunds', COUNT(*) FROM payment_refunds UNION ALL SELECT 'payouts', COUNT(*) FROM seller_payouts
      UNION ALL SELECT 'stray charges', COUNT(*) FROM stray_charges UNION ALL SELECT 'offers', COUNT(*) FROM second_chance_offers
      UNION ALL SELECT 'vouchers used', COUNT(*) FROM promo_redemptions UNION ALL SELECT 'buyer reviews', COUNT(*) FROM buyer_reviews
      UNION ALL SELECT 'lot alerts', COUNT(*) FROM lot_alerts) counts`);
  console.log(`
Done. ${counts}
  secure@nexbid.test / ${PASSWORD} signs in with a code from the key printed above (the first run prints it).
  Still empty on purpose: push_subscriptions (a real browser must allow notifications), user_identities (needs a
  Google sign-in), outbox_events (the outbox empties itself once events are published).`);
}

main().catch((error) => {
  console.error(`\nFilling failed: ${error.message}`);
  process.exit(1);
});
