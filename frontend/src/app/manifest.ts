import type { MetadataRoute } from "next";

/**
 * EN: The web app manifest: NexBid can be installed to a phone's home screen and opens on its own, without the browser's
 *     bars. Colours match the dark identity.
 * VI: Web app manifest: NexBid cài được lên màn hình chính điện thoại và mở như một ứng dụng riêng, không có thanh trình
 *     duyệt. Màu theo nhận diện tối.
 */
export default function manifest(): MetadataRoute.Manifest {
  return {
    name: "NexBid",
    short_name: "NexBid",
    description: "Live auctions for watches, cameras, art and collectibles.",
    start_url: "/",
    scope: "/",
    display: "standalone",
    background_color: "#0a0a0a",
    theme_color: "#0a0a0a",
    lang: "vi",
    icons: [
      { src: "/icon-192.png", sizes: "192x192", type: "image/png", purpose: "any" },
      { src: "/icon.png", sizes: "512x512", type: "image/png", purpose: "any" },
      { src: "/icon-maskable-512.png", sizes: "512x512", type: "image/png", purpose: "maskable" },
    ],
    shortcuts: [
      { name: "Live auctions", url: "/auctions?status=ACTIVE" },
      { name: "My bids", url: "/my-bids" },
      { name: "Calendar", url: "/calendar" },
    ],
  };
}
