"use client";

import { useEffect } from "react";

/**
 * EN: Registers NexBid's service worker once the page has loaded, so the site can be installed and shows an offline
 *     page when the network drops. Not in development, where it would serve stale builds.
 * VI: Đăng ký service worker của NexBid sau khi trang tải xong, để trang cài được và hiện trang offline khi mất mạng.
 *     Không chạy khi dev, vì khi đó nó sẽ phục vụ bản build cũ.
 */
export function ServiceWorker() {
  useEffect(() => {
    if (process.env.NODE_ENV !== "production" || !("serviceWorker" in navigator)) return;
    const register = () => void navigator.serviceWorker.register("/sw.js", { scope: "/" }).catch(() => undefined);
    if (document.readyState === "complete") register();
    else window.addEventListener("load", register, { once: true });
  }, []);
  return null;
}
