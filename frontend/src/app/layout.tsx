import type { Metadata, Viewport } from "next";
import { Anton, Geist, Geist_Mono } from "next/font/google";
import { NextIntlClientProvider } from "next-intl";
import { getLocale, getTranslations } from "next-intl/server";
import { headers } from "next/headers";

import { MotionProvider } from "@/components/common/motion-provider";
import { TimeZoneSync } from "@/components/common/time-zone-sync";
import { ThemeProvider } from "@/components/layout/theme-provider";
import { Toaster } from "@/components/ui/sonner";

import "./globals.css";

const geistSans = Geist({
  variable: "--font-geist-sans",
  subsets: ["latin", "vietnamese"],
  display: "swap",
  // Not preloaded: a preload fetches every subset, the Vietnamese one on English pages too. Left to the
  // browser, only the subsets a page shows are fetched, while a metric-matched fallback fills in.
  preload: false,
});

const geistMono = Geist_Mono({
  variable: "--font-geist-mono",
  subsets: ["latin"],
  display: "swap",
  // Only for figures: loaded when used, not raced against the page's first image.
  preload: false,
});

/** Editorial display face — condensed, heavy, set in caps. */
const anton = Anton({
  variable: "--font-anton",
  subsets: ["latin"],
  weight: "400",
  display: "swap",
});

export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations("common");

  return {
    title: {
      default: `${t("appName")} — ${t("tagline")}`,
      template: `%s · ${t("appName")}`,
    },
    description: t("description"),
    applicationName: t("appName"),
    icons: { icon: "/icon.png", apple: "/icon.png" },
  };
}

export const viewport: Viewport = {
  themeColor: [
    { media: "(prefers-color-scheme: light)", color: "#F7F7F4" },
    { media: "(prefers-color-scheme: dark)", color: "#0A0A0A" },
  ],
  width: "device-width",
  initialScale: 1,
};

export default async function RootLayout({ children }: LayoutProps<"/">) {
  const locale = await getLocale();
  // The Content Security Policy's nonce for this request (src/proxy.ts).
  const nonce = (await headers()).get("x-nonce") ?? undefined;

  return (
    // next-themes writes the theme class on the client before paint, so the
    // server markup will never match — that mismatch is expected, not a bug.
    <html
      lang={locale}
      suppressHydrationWarning
      className={`${geistSans.variable} ${geistMono.variable} ${anton.variable} antialiased`}
    >
      <body className="flex min-h-dvh flex-col">
        <ThemeProvider nonce={nonce}>
          <NextIntlClientProvider>
            <TimeZoneSync />
            <MotionProvider>{children}</MotionProvider>
            <Toaster
              position="bottom-right"
              toastOptions={{
                classNames: {
                  toast:
                    "!bg-surface-2 !border-line !text-foreground !rounded-none !shadow-none",
                  description: "!text-muted-foreground",
                },
              }}
            />
          </NextIntlClientProvider>
        </ThemeProvider>
      </body>
    </html>
  );
}
