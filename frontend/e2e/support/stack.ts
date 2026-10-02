import { execFileSync } from "node:child_process";
import path from "node:path";

/**
 * EN: The stack under test, reached the way a user would (the web app) and the way setup needs to (the API,
 *     plus the database for the one thing the API cannot do: granting roles).
 * VI: Stack được test, truy cập theo cách người dùng dùng (web) và theo cách phần chuẩn bị cần (API, cộng
 *     database cho việc duy nhất API không làm được: cấp vai trò).
 */
export const WEB_URL = process.env.E2E_WEB_URL ?? "http://localhost:3000";
export const API_URL = (process.env.E2E_API_URL ?? "http://localhost:8080").replace(/\/$/, "");
const COMPOSE_FILE = path.resolve(__dirname, "../..", process.env.E2E_COMPOSE_FILE ?? "../docker-compose.yml");

/** EN: Every test account's password. / VI: Mật khẩu của mọi tài khoản test. */
export const PASSWORD = "e2e-password";

/** EN: Where the web app keeps the signed-in session. / VI: Nơi web app giữ phiên đăng nhập. */
export const TOKEN_STORAGE_KEY = "nexbid.token";

export interface Session {
  token: string;
  expiresAt: string;
}

export interface PlacedBid {
  amount: number;
  createdAt: string;
}

async function call<T>(method: string, route: string, options: { token?: string; body?: unknown } = {}): Promise<T> {
  const response = await fetch(API_URL + route, {
    method,
    headers: {
      ...(options.body === undefined ? {} : { "Content-Type": "application/json" }),
      ...(options.token ? { Authorization: `Bearer ${options.token}` } : {}),
    },
    body: options.body === undefined ? undefined : JSON.stringify(options.body),
  });
  const json = await response.json().catch(() => null);
  if (!response.ok) {
    throw new Error(`${method} ${route} → ${response.status} ${JSON.stringify(json)}`);
  }
  return json?.data as T;
}

export async function register(email: string, fullName: string): Promise<void> {
  await call("POST", "/api/auth/register", { body: { fullName, email, password: PASSWORD } });
}

export async function login(email: string): Promise<Session> {
  const data = await call<{ accessToken: string; expiresAt: string }>("POST", "/api/auth/login", {
    body: { email, password: PASSWORD },
  });
  return { token: data.accessToken, expiresAt: data.expiresAt };
}

/**
 * EN: Clears the sign-in limiter, so runs of the suite in quick succession do not add up to the per-address limit.
 * VI: Xoá bộ đếm đăng nhập, để các lần chạy test liền nhau không cộng dồn tới giới hạn theo địa chỉ.
 */
export function resetSignInLimits(): void {
  execFileSync(
    "docker",
    ["compose", "-f", COMPOSE_FILE, "exec", "-T", "redis", "redis-cli", "EVAL",
      "for _, key in ipairs(redis.call('KEYS', ARGV[1])) do redis.call('DEL', key) end return 1",
      "0", "rate:login:*"],
    { stdio: ["ignore", "ignore", "inherit"] },
  );
}

/** EN: Grants roles in SQL, as the demo seed does. / VI: Cấp vai trò bằng SQL, như script seed demo. */
export function grantRoles(grants: Array<[email: string, role: string]>): void {
  const pairs = grants.map(([email, role]) => `('${email}', '${role}')`).join(", ");
  execFileSync(
    "docker",
    ["compose", "-f", COMPOSE_FILE, "exec", "-T", "postgres", "psql", "-U", "nexbid", "-d", "nexbid",
      "-v", "ON_ERROR_STOP=1", "-tAq", "-c",
      `INSERT INTO user_roles (user_id, role_id)
         SELECT u.id, r.id FROM users u JOIN roles r ON (u.email, r.name) IN (${pairs})
         ON CONFLICT DO NOTHING`],
    { stdio: ["ignore", "ignore", "inherit"] },
  );
}

/** EN: One SQL statement in the stack's database; returns the rows it printed. / VI: Một câu SQL trong database của stack; trả về các dòng nó in ra. */
function sql(statement: string): string[] {
  const output = execFileSync(
    "docker",
    ["compose", "-f", COMPOSE_FILE, "exec", "-T", "postgres", "psql", "-U", "nexbid", "-d", "nexbid",
      "-v", "ON_ERROR_STOP=1", "-tAq", "-c", statement],
    { encoding: "utf8", stdio: ["ignore", "pipe", "inherit"] },
  );
  return output.split("\n").filter(Boolean);
}

/**
 * EN: Moves a lot's close into the past, as two days of waiting would; the scheduler ends it within seconds.
 * VI: Dời giờ đóng của lô về quá khứ, như thể đã chờ hai ngày; scheduler sẽ đóng nó trong vài giây.
 */
export function endLotNow(auctionId: string): void {
  sql(`UPDATE auctions SET end_time = now() - interval '1 second' WHERE id = '${auctionId}'`);
}

/**
 * EN: Lets the open payment on a lot run out; the expiry job cancels the sale on its next run. Returns how many
 *     payments it found, which stays 0 until the scheduler has closed the lot.
 * VI: Cho khoản thanh toán đang mở của lô quá hạn; job hết hạn sẽ huỷ giao dịch ở lần chạy kế tiếp. Trả về số khoản
 *     tìm thấy, vẫn là 0 cho tới khi scheduler đóng lô.
 */
export function lapsePayment(auctionId: string): number {
  return sql(`UPDATE payments SET expired_at = now() - interval '1 second'
               WHERE auction_id = '${auctionId}' AND status IN ('PENDING', 'FAILED') RETURNING id`).length;
}

/**
 * EN: A lot with no photos. `open` approves it with a start time already passed, so it is live at once
 *     (spec §7.5); otherwise it waits in the approval queue.
 * VI: Một lô không ảnh. `open` duyệt nó với giờ mở đã qua, nên lô mở ngay (spec §7.5); nếu không, lô nằm
 *     trong hàng chờ duyệt.
 */
export async function createLot(
  seller: Session,
  admin: Session,
  name: string,
  open: boolean,
): Promise<{ id: string; startTime: string }> {
  const categories = await call<Array<{ id: string }>>("GET", "/api/categories");
  const product = await call<{ id: string }>("POST", "/api/seller/products", {
    token: seller.token,
    body: {
      name,
      description: "A lot created by the end-to-end tests.",
      categoryId: categories[0].id,
      condition: "GOOD",
      publishNow: true,
    },
  });
  const minute = 60_000;
  const startTime = new Date(Date.now() + (open ? -minute : 24 * 60 * minute)).toISOString();
  const auction = await call<{ id: string }>("POST", "/api/seller/auctions", {
    token: seller.token,
    body: {
      productId: product.id,
      startingPrice: 1_000_000,
      minimumIncrement: 100_000,
      startTime,
      endTime: new Date(Date.now() + 48 * 60 * minute).toISOString(),
    },
  });
  await call("POST", `/api/seller/auctions/${auction.id}/submit`, { token: seller.token });
  if (open) await call("POST", `/api/admin/auctions/${auction.id}/approve`, { token: admin.token });
  return { id: auction.id, startTime };
}

/** EN: Bids the minimum next amount. / VI: Trả mức tối thiểu kế tiếp. */
export async function placeBid(auctionId: string, bidder: Session): Promise<PlacedBid> {
  const detail = await call<{ minimumNextBid: number }>("GET", `/api/auctions/${auctionId}`);
  const placed = await call<{ bid: PlacedBid }>("POST", `/api/auctions/${auctionId}/bids`, {
    token: bidder.token,
    body: { amount: detail.minimumNextBid },
  });
  return placed.bid;
}
