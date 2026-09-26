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
