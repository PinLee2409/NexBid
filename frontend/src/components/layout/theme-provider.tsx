"use client";

import { ThemeProvider as NextThemesProvider } from "next-themes";

/**
 * Dark is NexBid's default register — the auction room is a dark room. The
 * light theme is the same system re-lit, offered for daylight and for readers
 * who need it, but it is not what the brand opens with.
 */
export function ThemeProvider({
  children,
  nonce,
}: {
  children: React.ReactNode;
  /** The page's CSP nonce, for the script that applies the theme before paint. */
  nonce?: string;
}) {
  return (
    <NextThemesProvider
      nonce={nonce}
      attribute="class"
      defaultTheme="dark"
      enableSystem
      disableTransitionOnChange
      storageKey="nexbid-theme"
    >
      {children}
    </NextThemesProvider>
  );
}
