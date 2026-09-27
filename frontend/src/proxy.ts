import { NextResponse, type NextRequest } from "next/server";

/**
 * EN: A Content Security Policy for every page, with a fresh nonce per request. Only scripts carrying the
 *     nonce run — Next.js adds it to its own — so a script injected into the page, or loaded from anywhere
 *     else, does not. The page cannot be framed by another site, and may only connect to itself and the
 *     realtime socket.
 * VI: Content Security Policy cho mọi trang, với nonce mới cho mỗi request. Chỉ script mang nonce mới được chạy
 *     — Next.js tự gắn nonce cho script của nó — nên script bị chèn vào trang, hay tải từ nơi khác, đều không chạy.
 *     Trang không thể bị trang khác nhúng vào khung, và chỉ được kết nối tới chính nó và socket realtime.
 */

const SOCKET_URL = process.env.NEXT_PUBLIC_WS_URL ?? "ws://localhost:8080/ws";

function policy(nonce: string): string {
  const development = process.env.NODE_ENV === "development";
  return [
    "default-src 'self'",
    // EN: React needs eval in development only, for its error overlays. / VI: React chỉ cần eval khi dev, cho màn hình báo lỗi.
    `script-src 'self' 'nonce-${nonce}' 'strict-dynamic'${development ? " 'unsafe-eval'" : ""}`,
    // EN: Toasts and animations write inline styles as they run, which no nonce can cover; styles cannot run code.
    // VI: Toast và hiệu ứng động ghi style inline lúc chạy, thứ mà nonce không phủ được; còn style thì không chạy được code.
    "style-src 'self' 'unsafe-inline'",
    // EN: blob: for photo previews before upload. / VI: blob: cho ảnh xem trước khi tải lên.
    "img-src 'self' blob: data:",
    "font-src 'self'",
    `connect-src 'self' ${new URL(SOCKET_URL).origin}`,
    "object-src 'none'",
    "base-uri 'self'",
    "form-action 'self'",
    "frame-ancestors 'none'",
  ].join("; ");
}

export function proxy(request: NextRequest) {
  const nonce = Buffer.from(crypto.randomUUID()).toString("base64");
  const csp = policy(nonce);

  // EN: Next.js reads the nonce from the request's CSP; the layout reads x-nonce for the theme script.
  // VI: Next.js đọc nonce từ CSP của request; layout đọc x-nonce cho script giao diện.
  const requestHeaders = new Headers(request.headers);
  requestHeaders.set("x-nonce", nonce);
  requestHeaders.set("Content-Security-Policy", csp);

  const response = NextResponse.next({ request: { headers: requestHeaders } });
  response.headers.set("Content-Security-Policy", csp);
  return response;
}

export const config = {
  matcher: [
    {
      // EN: Pages only: not the API and photos the backend answers, nor static files. / VI: Chỉ trang: không gồm API và ảnh do backend trả, cũng không gồm file tĩnh.
      source: "/((?!api/|media/|_next/static|_next/image|favicon.ico|.*\\.(?:svg|png|jpg|jpeg|webp|ico|txt)$).*)",
      missing: [
        { type: "header", key: "next-router-prefetch" },
        { type: "header", key: "purpose", value: "prefetch" },
      ],
    },
  ],
};
