"use client";

import { ThemeProvider as NextThemesProvider } from "next-themes";

/**
 * Dark is NexBid's default register — the auction room is a dark room. The
 * light theme is the same system re-lit, offered for daylight and for readers
 * who need it, but it is not what the brand opens with.
 */
export function ThemeProvider({ children }: { children: React.ReactNode }) {
  return (
    <NextThemesProvider
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
