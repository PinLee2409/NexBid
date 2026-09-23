"use client";

import { useTranslations } from "next-intl";
import { useState } from "react";

import { cn } from "@/lib/utils";

/** Every auction state the terminal can be forced into while building. */
export type PreviewState =
  | "normal"
  | "leading"
  | "outbid"
  | "finalMinute"
  | "extended"
  | "sold"
  | "won";

const STATES: PreviewState[] = [
  "normal",
  "leading",
  "outbid",
  "finalMinute",
  "extended",
  "sold",
  "won",
];

interface StatePreviewProps {
  value: PreviewState;
  onChange: (next: PreviewState) => void;
}

/**
 * Development-only control rail.
 *
 * Auction states are mostly reachable only by waiting for other people to bid,
 * so every one of them can be forced here and reviewed on demand. It is not
 * rendered in a production build.
 */
export function StatePreview({ value, onChange }: StatePreviewProps) {
  const t = useTranslations("devtools");
  const [open, setOpen] = useState(false);

  if (process.env.NODE_ENV === "production") return null;

  return (
    <div className="fixed bottom-24 left-4 z-[60] lg:bottom-4 print:hidden">
      {open ? (
        <div className="border-line bg-surface-2 w-[min(19rem,88vw)] border">
          <div className="border-line flex items-center justify-between gap-3 border-b px-3 py-2">
            <span className="label-sm text-signal-text">{t("title")}</span>
            <button
              type="button"
              onClick={() => setOpen(false)}
              className="text-dim hover:text-foreground text-xs"
              aria-label={t("reset")}
            >
              ✕
            </button>
          </div>

          <div className="grid grid-cols-2 gap-px p-px">
            {STATES.map((state) => (
              <button
                key={state}
                type="button"
                onClick={() => onChange(state)}
                aria-pressed={value === state}
                className={cn(
                  "label-sm px-3 py-2.5 transition-colors",
                  value === state
                    ? "bg-signal text-signal-ink"
                    : "bg-surface text-muted-foreground hover:text-foreground",
                )}
              >
                {t(state)}
              </button>
            ))}
          </div>

          <p className="text-dim border-line border-t px-3 py-2 text-[11px] leading-relaxed">
            {t("hint")}
          </p>
        </div>
      ) : (
        <button
          type="button"
          onClick={() => setOpen(true)}
          className="label-sm border-line bg-surface-2 text-muted-foreground hover:text-signal-text border px-3 py-2 transition-colors"
        >
          {t("title")}
          {value !== "normal" ? (
            <span className="text-signal-text ml-2">{t(value)}</span>
          ) : null}
        </button>
      )}
    </div>
  );
}
