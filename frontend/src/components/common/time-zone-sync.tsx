"use client";

import { useTimeZone } from "next-intl";
import { useRouter } from "next/navigation";
import { useEffect } from "react";

import { TIME_ZONE_COOKIE } from "@/i18n/config";

// EN: At most one refresh per page load, even if the server does not accept the zone.
// VI: Tối đa một lần refresh mỗi lần tải trang, kể cả khi server không nhận múi giờ này.
let refreshed = false;

/**
 * EN: Tells the server the browser's time zone through a cookie, then re-renders once, so every time on
 *     the page is in the reader's zone. Before that, the server renders in UTC.
 * VI: Báo cho server múi giờ của trình duyệt qua cookie rồi render lại một lần, để mọi mốc giờ trên trang
 *     theo múi giờ người xem. Trước đó server render theo UTC.
 */
export function TimeZoneSync() {
  const rendered = useTimeZone();
  const router = useRouter();

  useEffect(() => {
    const browser = Intl.DateTimeFormat().resolvedOptions().timeZone;
    if (!browser || browser === rendered || refreshed) return;

    refreshed = true;
    // EN: IANA names (e.g. Asia/Ho_Chi_Minh) are valid cookie values as they are.
    // VI: Tên IANA (vd. Asia/Ho_Chi_Minh) dùng làm giá trị cookie được luôn.
    document.cookie = `${TIME_ZONE_COOKIE}=${browser}; path=/; max-age=${60 * 60 * 24 * 365}; samesite=lax`;
    router.refresh();
  }, [rendered, router]);

  return null;
}
