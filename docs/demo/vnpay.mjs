// EN: Plays VNPay for the demo scripts: starts a checkout and sends VNPay's signed "paid" report to the IPN endpoint,
//     the same way the real VNPay server confirms a payment. Signs with NEXBID_VNPAY_HASH_SECRET, defaulting to the
//     local placeholder in application.yml — so it only works against a backend you run yourself.
// VI: Đóng vai VNPay cho các script demo: bắt đầu checkout và gửi báo cáo "đã trả" có chữ ký tới endpoint IPN, đúng cách
//     server VNPay thật xác nhận một khoản thanh toán. Ký bằng NEXBID_VNPAY_HASH_SECRET, mặc định là giá trị giữ chỗ
//     local trong application.yml — nên chỉ chạy được với backend bạn tự chạy.

import { createHmac } from "node:crypto";
import { existsSync, readFileSync } from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

/**
 * EN: The backend's hash secret: the environment first, then backend/.env (what the "local" profile reads) unless the
 *     target is the Docker stack, then the placeholder. Never printed.
 * VI: Hash secret của backend: biến môi trường trước, rồi backend/.env (thứ profile "local" đọc) trừ khi đích là stack
 *     Docker, rồi giá trị giữ chỗ. Không bao giờ in ra.
 */
function hashSecret() {
  if (process.env.NEXBID_VNPAY_HASH_SECRET) return process.env.NEXBID_VNPAY_HASH_SECRET;
  const file = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "../../backend/.env");
  if (process.env.STACK !== "docker" && existsSync(file)) {
    const line = readFileSync(file, "utf8").split(/\r?\n/).find((each) => each.startsWith("NEXBID_VNPAY_HASH_SECRET="));
    const value = line?.slice("NEXBID_VNPAY_HASH_SECRET=".length).trim();
    if (value) return value;
  }
  return "change-me-local-vnpay-secret";
}

const SECRET = hashSecret();

/** EN: Java's URLEncoder, which VNPay's signature is defined over. / VI: Giống URLEncoder của Java, thứ chữ ký VNPay dựa vào. */
function javaEncode(value) {
  return encodeURIComponent(value)
    .replace(/%20/g, "+")
    .replace(/[!'()~]/g, (char) => "%" + char.charCodeAt(0).toString(16).toUpperCase());
}

function sign(fields) {
  const data = Object.keys(fields)
    .filter((key) => key !== "vnp_SecureHash" && key !== "vnp_SecureHashType" && fields[key] !== "")
    .sort()
    .map((key) => `${javaEncode(key)}=${javaEncode(fields[key])}`)
    .join("&");
  return createHmac("sha512", SECRET).update(data, "utf8").digest("hex");
}

/**
 * @param {string} api        backend base URL
 * @param {(method: string, route: string, options?: object) => Promise<any>} call  the script's API helper
 */
/**
 * EN: Starts a checkout and returns VNPay's signed "paid" reply for it, without sending it.
 * VI: Bắt đầu một lần checkout và trả về câu trả lời "đã trả" có chữ ký của VNPay cho nó, chưa gửi đi.
 */
export async function paidReply(call, paymentId, token) {
  const { paymentUrl } = await call("POST", `/api/payments/${paymentId}/vnpay`, { token, body: { locale: "vi" } });
  const sent = Object.fromEntries(new URL(paymentUrl).searchParams);
  const reply = {
    vnp_TmnCode: sent.vnp_TmnCode,
    vnp_Amount: sent.vnp_Amount,
    vnp_TxnRef: sent.vnp_TxnRef,
    vnp_OrderInfo: sent.vnp_OrderInfo,
    vnp_BankCode: "NCB",
    vnp_PayDate: sent.vnp_CreateDate,
    vnp_ResponseCode: "00",
    vnp_TransactionStatus: "00",
    vnp_TransactionNo: String(Date.now()) + Math.floor(Math.random() * 1000),
  };
  reply.vnp_SecureHash = sign(reply);
  return reply;
}

/** EN: Sends a reply to the IPN endpoint, as VNPay's server would. / VI: Gửi câu trả lời tới endpoint IPN, như server VNPay. */
export async function sendIpn(api, reply) {
  return (await fetch(`${api}/api/payments/vnpay/ipn?${new URLSearchParams(reply)}`)).json();
}

export async function payThroughVnpay(api, call, paymentId, token) {
  // EN: Checkout needs a delivery address; demo buyers keep theirs if they already have one. / VI: Checkout cần địa chỉ nhận hàng.
  const current = await call("GET", "/api/users/me/address", { token });
  if (!current) await call("PUT", "/api/users/me/address", { token, body: { recipient: "Demo Buyer", phone: "0901234567", line: "12 Nguyen Hue", ward: "Ben Nghe", district: "District 1", province: "Ho Chi Minh City" } });
  const { paymentUrl } = await call("POST", `/api/payments/${paymentId}/vnpay`, { token, body: { locale: "vi" } });
  const sent = Object.fromEntries(new URL(paymentUrl).searchParams);
  const reply = {
    vnp_TmnCode: sent.vnp_TmnCode,
    vnp_Amount: sent.vnp_Amount,
    vnp_TxnRef: sent.vnp_TxnRef,
    vnp_OrderInfo: sent.vnp_OrderInfo,
    vnp_BankCode: "NCB",
    vnp_PayDate: sent.vnp_CreateDate,
    vnp_ResponseCode: "00",
    vnp_TransactionStatus: "00",
    vnp_TransactionNo: String(Date.now()),
  };
  reply.vnp_SecureHash = sign(reply);
  const answer = await (await fetch(`${api}/api/payments/vnpay/ipn?${new URLSearchParams(reply)}`)).json();
  if (answer.RspCode !== "00") {
    throw new Error(`VNPay IPN refused payment ${paymentId}: ${JSON.stringify(answer)}. Is NEXBID_VNPAY_HASH_SECRET the backend's?`);
  }
}
