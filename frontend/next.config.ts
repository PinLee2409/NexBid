import createNextIntlPlugin from "next-intl/plugin";
import type { NextConfig } from "next";

const withNextIntl = createNextIntlPlugin("./src/i18n/request.ts");

// Where the backend lives. Read when the server starts (next dev) or when it
// is built (next build), so a Docker image is built for its compose network.
const apiUrl = process.env.NEXBID_API_URL ?? "http://localhost:8080";

const nextConfig: NextConfig = {
  // A self-contained server in .next/standalone, so the Docker image ships
  // without a full node_modules. `next dev` is unaffected.
  output: "standalone",
  // No advertising which framework serves the site.
  poweredByHeader: false,
  // AVIF where the browser takes it (smaller photos), WebP otherwise.
  images: {
    formats: ["image/avif", "image/webp"],
  },
  // Browser safety headers for pages; the Content Security Policy, which needs
  // a fresh nonce per request, is set in src/proxy.ts. Answers from the backend
  // (/api, /media) carry Spring Security's own headers.
  async headers() {
    return [
      {
        source: "/((?!api/|media/).*)",
        headers: [
          { key: "X-Content-Type-Options", value: "nosniff" },
          { key: "X-Frame-Options", value: "DENY" },
          { key: "Referrer-Policy", value: "strict-origin-when-cross-origin" },
          { key: "Permissions-Policy", value: "camera=(), microphone=(), geolocation=(), payment=()" },
          // Ignored by browsers on plain HTTP; on HTTPS, keeps them there.
          { key: "Strict-Transport-Security", value: "max-age=63072000; includeSubDomains" },
        ],
      },
    ];
  },
  // The browser only ever talks to this origin: API calls and product photos
  // are passed through to the backend, so no CORS setup is needed.
  async rewrites() {
    return [
      { source: "/api/:path*", destination: `${apiUrl}/api/:path*` },
      { source: "/media/:path*", destination: `${apiUrl}/media/:path*` },
    ];
  },
};

export default withNextIntl(nextConfig);
