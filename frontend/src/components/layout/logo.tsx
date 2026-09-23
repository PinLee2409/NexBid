import Image from "next/image";
import Link from "next/link";

import { cn } from "@/lib/utils";

interface LogoProps {
  variant?: "lockup" | "mark" | "stacked";
  className?: string;
  href?: string | null;
  /** Height of the mark in px; the wordmark is scaled to match optically. */
  size?: number;
}

/**
 * Both cuts of the artwork ship, and CSS picks one. Choosing in JavaScript
 * would mean waiting for the theme to resolve on the client, which shows the
 * wrong logo for a frame on every cold load.
 */
function ThemedArt({
  name,
  width,
  height,
  size,
}: {
  name: string;
  width: number;
  height: number;
  size: number;
}) {
  // Purely decorative: the name is carried by the wrapper, once, so that the
  // two cuts of the same artwork are not announced twice.
  const shared = {
    width,
    height,
    priority: true,
    style: { height: size, width: "auto" },
  } as const;

  return (
    <>
      <Image
        {...shared}
        alt=""
        src={`/${name}-light.png`}
        className="block select-none dark:hidden"
      />
      <Image
        {...shared}
        alt=""
        src={`/${name}.png`}
        className="hidden select-none dark:block"
      />
    </>
  );
}

/**
 * NexBid's mark. The dark cut renders the artwork's charcoal as ivory with the
 * lime accent intact; the light cut keeps near-black ink and darkens the lime,
 * which would otherwise sit at roughly 1.1:1 on a pale page.
 */
export function LogoMark({
  className,
  size = 28,
}: {
  className?: string;
  size?: number;
}) {
  return (
    <span className={cn("inline-flex items-center", className)}>
      <ThemedArt name="nexbid-mark" width={734} height={641} size={size} />
    </span>
  );
}

export function Logo({
  variant = "lockup",
  className,
  href = "/",
  size = 26,
}: LogoProps) {
  const art =
    variant === "stacked" ? (
      <span className="inline-flex items-center">
        <ThemedArt
          name="nexbid-logo"
          width={1050}
          height={924}
          size={size * 3.4}
        />
      </span>
    ) : variant === "mark" ? (
      <LogoMark size={size} />
    ) : (
      <span className="inline-flex items-center gap-2.5">
        <LogoMark size={size} />
        <ThemedArt
          name="nexbid-wordmark"
          width={1050}
          height={218}
          size={size * 0.62}
        />
      </span>
    );

  if (href === null) {
    return (
      <span className={cn("inline-flex items-center", className)}>
        {art}
        <span className="sr-only">NexBid</span>
      </span>
    );
  }

  return (
    <Link
      href={href}
      aria-label="NexBid"
      className={cn(
        "inline-flex items-center transition-opacity hover:opacity-80",
        className,
      )}
    >
      {art}
    </Link>
  );
}
