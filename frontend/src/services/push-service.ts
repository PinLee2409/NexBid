"use client";

import { api } from "./api/http";

/**
 * EN: Browser push for this browser: supported here at all, and on or off.
 * VI: Thông báo đẩy cho trình duyệt này: có hỗ trợ không, và đang bật hay tắt.
 */
export type PushState = "unsupported" | "denied" | "off" | "on";

function pushSupported(): boolean {
  return typeof window !== "undefined" && "serviceWorker" in navigator && "PushManager" in window && "Notification" in window;
}

async function registration(): Promise<ServiceWorkerRegistration> {
  const existing = await navigator.serviceWorker.getRegistration("/");
  return existing ?? navigator.serviceWorker.register("/sw.js", { scope: "/" });
}

function keyBytes(base64Url: string): Uint8Array<ArrayBuffer> {
  const base64 = (base64Url + "=".repeat((4 - (base64Url.length % 4)) % 4)).replace(/-/g, "+").replace(/_/g, "/");
  const raw = atob(base64);
  const bytes = new Uint8Array(new ArrayBuffer(raw.length));
  for (let i = 0; i < raw.length; i++) bytes[i] = raw.charCodeAt(i);
  return bytes;
}

/** EN: Whether this browser is subscribed for the signed-in user. / VI: Trình duyệt này đã đăng ký cho người đang đăng nhập chưa. */
export async function getPushState(): Promise<PushState> {
  if (!pushSupported()) return "unsupported";
  if (Notification.permission === "denied") return "denied";
  const existing = await navigator.serviceWorker.getRegistration("/");
  const subscription = await existing?.pushManager.getSubscription();
  if (!subscription) return "off";
  const { subscribed } = await api<{ subscribed: boolean }>("/api/users/me/push/status", {
    method: "POST",
    body: { endpoint: subscription.endpoint },
  });
  return subscribed ? "on" : "off";
}

/** EN: Asks permission, subscribes, and tells NexBid. / VI: Xin quyền, đăng ký, và báo cho NexBid. */
export async function turnPushOn(locale: string): Promise<PushState> {
  if (!pushSupported()) return "unsupported";
  const permission = await Notification.requestPermission();
  if (permission !== "granted") return permission === "denied" ? "denied" : "off";
  const { publicKey } = await api<{ publicKey: string }>("/api/users/me/push/key");
  const worker = await registration();
  await navigator.serviceWorker.ready;
  let subscription = await worker.pushManager.getSubscription();
  // EN: A subscription made with another key cannot be reused. / VI: Đăng ký làm bằng khoá khác thì không dùng lại được.
  if (subscription) {
    const current = subscription.options.applicationServerKey;
    const wanted = keyBytes(publicKey);
    const same =
      current !== null && current !== undefined && new Uint8Array(current).every((byte, i) => byte === wanted[i]);
    if (!same) {
      await subscription.unsubscribe();
      subscription = null;
    }
  }
  subscription ??= await worker.pushManager.subscribe({ userVisibleOnly: true, applicationServerKey: keyBytes(publicKey) });
  const json = subscription.toJSON();
  await api("/api/users/me/push", {
    method: "POST",
    body: { endpoint: subscription.endpoint, p256dh: json.keys?.p256dh, auth: json.keys?.auth, locale },
  });
  return "on";
}

/** EN: Forgets this browser on NexBid and in the browser. / VI: Quên trình duyệt này ở NexBid và trong trình duyệt. */
export async function turnPushOff(): Promise<PushState> {
  if (!pushSupported()) return "unsupported";
  const existing = await navigator.serviceWorker.getRegistration("/");
  const subscription = await existing?.pushManager.getSubscription();
  if (subscription) {
    await api("/api/users/me/push/unsubscribe", { method: "POST", body: { endpoint: subscription.endpoint } });
    await subscription.unsubscribe();
  }
  return "off";
}
