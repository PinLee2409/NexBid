/*
 * EN: NexBid's service worker: shows browser push notices and opens the right page when one is clicked, and shows an
 *     offline page when a page cannot load. Nothing else is cached, so prices and bids are always live.
 * VI: Service worker của NexBid: hiện thông báo đẩy và mở đúng trang khi bấm vào, và hiện trang offline khi một trang
 *     không tải được. Ngoài ra không cache gì, nên giá và lượt trả giá luôn là mới nhất.
 */
const OFFLINE_CACHE = "nexbid-offline-v1";
const OFFLINE_FILES = ["/offline.html", "/icon-192.png"];

self.addEventListener("install", (event) => {
  event.waitUntil(caches.open(OFFLINE_CACHE).then((cache) => cache.addAll(OFFLINE_FILES)).then(() => self.skipWaiting()));
});
self.addEventListener("activate", (event) => {
  event.waitUntil(
    caches
      .keys()
      .then((keys) => Promise.all(keys.filter((key) => key !== OFFLINE_CACHE).map((key) => caches.delete(key))))
      .then(() => self.clients.claim()),
  );
});

// EN: Pages go to the network; only when that fails is the offline page shown. / VI: Trang luôn lấy từ mạng; chỉ khi lỗi mới hiện trang offline.
self.addEventListener("fetch", (event) => {
  if (event.request.mode !== "navigate") return;
  event.respondWith(fetch(event.request).catch(() => caches.match("/offline.html")));
});

self.addEventListener("push", (event) => {
  let notice = { title: "NexBid", body: "", url: "/notifications", tag: undefined };
  try {
    notice = { ...notice, ...(event.data ? event.data.json() : {}) };
  } catch {
    // EN: Not JSON: show the text as it is. / VI: Không phải JSON: hiện nguyên văn.
    notice.body = event.data ? event.data.text() : "";
  }
  // EN: Only same-site paths are opened. / VI: Chỉ mở đường dẫn trong site.
  const url = typeof notice.url === "string" && notice.url.startsWith("/") && !notice.url.startsWith("//")
    ? notice.url
    : "/notifications";
  event.waitUntil(
    self.registration.showNotification(String(notice.title), {
      body: String(notice.body ?? ""),
      tag: notice.tag,
      renotify: Boolean(notice.tag),
      icon: "/icon.png",
      badge: "/nexbid-mark.png",
      data: { url },
    }),
  );
});

self.addEventListener("notificationclick", (event) => {
  event.notification.close();
  const target = new URL(event.notification.data?.url ?? "/", self.location.origin).href;
  event.waitUntil(
    self.clients.matchAll({ type: "window", includeUncontrolled: true }).then((windows) => {
      const open = windows.find((client) => client.url === target);
      if (open) return open.focus();
      return self.clients.openWindow(target);
    }),
  );
});
