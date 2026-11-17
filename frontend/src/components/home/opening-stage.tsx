"use client";

import { AnimatePresence, m, useReducedMotion } from "framer-motion";
import { ArrowLeft, ArrowRight, Pause, Play } from "lucide-react";
import { useTranslations } from "next-intl";
import Link from "next/link";
import { useRef, useState, type KeyboardEvent, type PointerEvent } from "react";

import { ForeignPrice } from "@/components/common/foreign-price";
import { ProductPhoto } from "@/components/common/product-photo";
import { AuctionClock } from "@/components/nexbid/auction-clock";
import { LiveTag } from "@/components/nexbid/live-pulse";
import { LotNumber } from "@/components/nexbid/lot-number";
import { AuctionPriceTicker } from "@/components/nexbid/price-ticker";
import { useMoney } from "@/hooks/use-money";
import { cn } from "@/lib/utils";
import type { AuctionSummary } from "@/types";

interface OpeningStageProps {
  /** EN: The lots on show, most contested first. / VI: Các lô trưng bày, lô được tranh nhiều nhất trước. */
  auctions: AuctionSummary[];
  serverTime: string;
  liveCount: number;
}

/** EN: How long each lot stays on stage. / VI: Mỗi lô ở trên sân khấu bao lâu. */
const SLIDE_MS = 7000;
const EASE = [0.22, 1, 0.36, 1] as const;

/**
 * The first screen is not a hero — it is the room.
 *
 * EN: The most contested lots take turns on a full-bleed stage: the photo cross-fades and drifts slowly closer, the
 *     figures rise in after it, and a row of bars shows how long each lot has left on stage. The show pauses while
 *     the pointer or focus is on it, has its own pause button, follows a swipe or the arrow keys, and with reduced
 *     motion never moves on by itself.
 * VI: Các lô được tranh nhiều nhất lần lượt lên sân khấu tràn màn hình: ảnh chuyển mờ dần và tiến lại gần chậm rãi, các
 *     con số trồi lên theo sau, và một hàng thanh cho biết mỗi lô còn ở trên sân khấu bao lâu. Trình chiếu dừng khi con
 *     trỏ hay focus ở trên nó, có nút tạm dừng riêng, theo cú vuốt hoặc phím mũi tên, và khi giảm chuyển động thì không
 *     bao giờ tự chuyển.
 */
export function OpeningStage({ auctions, serverTime, liveCount }: OpeningStageProps) {
  const money = useMoney();
  const t = useTranslations("stage");
  const reduceMotion = useReducedMotion() ?? false;
  const [index, setIndex] = useState(0);
  const [stopped, setStopped] = useState(false);
  const [hovered, setHovered] = useState(false);
  const [focused, setFocused] = useState(false);
  const swipeFrom = useRef<number | null>(null);

  const total = auctions.length;
  const auction = auctions[Math.min(index, total - 1)];
  if (!auction) return null;

  const showControls = total > 1;
  const autoplay = showControls && !reduceMotion && !stopped;
  const running = autoplay && !hovered && !focused;

  function go(next: number) {
    setIndex(((next % total) + total) % total);
  }

  function onKeyDown(event: KeyboardEvent<HTMLElement>) {
    if (!showControls) return;
    if (event.key === "ArrowRight") go(index + 1);
    if (event.key === "ArrowLeft") go(index - 1);
  }

  function onPointerDown(event: PointerEvent<HTMLElement>) {
    if (event.pointerType !== "mouse") swipeFrom.current = event.clientX;
  }

  function onPointerUp(event: PointerEvent<HTMLElement>) {
    const from = swipeFrom.current;
    swipeFrom.current = null;
    if (from === null || !showControls) return;
    const moved = event.clientX - from;
    if (Math.abs(moved) > 50) go(index + (moved < 0 ? 1 : -1));
  }

  return (
    // The stage is a cinema, not a page: it stays dark in both themes so the
    // object is lit and the type on top is always legible.
    <section
      className="on-media bg-background relative -mt-16 flex min-h-[100svh] flex-col overflow-hidden"
      aria-roledescription="carousel"
      aria-label={t("showcase")}
      onMouseEnter={() => setHovered(true)}
      onMouseLeave={() => setHovered(false)}
      // EN: Keyboard focus pauses; a mouse click on a control does not. / VI: Focus bằng bàn phím thì dừng; bấm chuột vào nút thì không.
      onFocusCapture={(event) => setFocused((event.target as HTMLElement).matches(":focus-visible"))}
      onBlurCapture={(event) => {
        if (!event.currentTarget.contains(event.relatedTarget as Node | null)) setFocused(false);
      }}
      onKeyDown={onKeyDown}
      onPointerDown={onPointerDown}
      onPointerUp={onPointerUp}
      data-testid="opening-stage"
    >
      {/* EN: Every photo stays mounted, so the next one is already loaded when its turn comes. / VI: Mọi ảnh đều được giữ, nên ảnh kế tiếp đã tải sẵn khi tới lượt. */}
      <div className="absolute inset-0" aria-hidden="true">
        {auctions.map((lot, position) => {
          const cover = lot.product.images[0];
          const active = position === index;
          return (
            <div
              key={lot.id}
              className={cn(
                "absolute inset-0 transition-opacity duration-[1200ms] ease-out",
                active ? "opacity-100" : "opacity-0",
              )}
            >
              <m.div
                className="absolute inset-0"
                initial={false}
                animate={active && !reduceMotion ? { scale: 1.08 } : { scale: 1 }}
                transition={active ? { duration: SLIDE_MS / 1000 + 1.5, ease: "linear" } : { duration: 1.2 }}
              >
                <ProductPhoto
                  src={cover?.url}
                  alt=""
                  sizes="100vw"
                  // The page's largest paint: the first photo is fetched ahead of everything else.
                  loading={position === 0 ? "eager" : "lazy"}
                  fetchPriority={position === 0 ? "high" : "low"}
                  className="object-cover object-center"
                  fallback="none"
                />
              </m.div>
            </div>
          );
        })}
        <div className="stage-fade absolute inset-0" />
        <div className="scrim-bottom absolute inset-x-0 bottom-0 h-[70%]" />
        <div className="scrim-top absolute inset-x-0 top-0 h-40" />
      </div>

      <div className="relative mx-auto flex w-full max-w-[1680px] flex-1 flex-col px-5 pt-24 pb-10 sm:px-8">
        {/* Top rail: the session status, opposite the lot index. */}
        <div className="flex items-start justify-between gap-6">
          <LiveTag label={t("auctionsLive", { count: liveCount })} />
          <LotNumber
            lot={auction.lotNumber}
            size="xl"
            bare
            decorative
            className="text-foreground/12 -mt-6 leading-[0.8] select-none sm:-mt-10"
          />
        </div>

        <div className="mt-auto" aria-live={running ? "off" : "polite"}>
          <AnimatePresence mode="wait" initial={false}>
            <m.div
              key={auction.id}
              role="group"
              aria-roledescription="slide"
              aria-label={t("slide", { index: index + 1, total })}
              initial={reduceMotion ? { opacity: 0 } : { opacity: 0, y: 28 }}
              animate={{ opacity: 1, y: 0 }}
              exit={reduceMotion ? { opacity: 0 } : { opacity: 0, y: -16 }}
              transition={{ duration: 0.55, ease: EASE }}
            >
              {/* Sits where the scrim is still thin: the title's shadow keeps it legible on a bright photo. */}
              <p className="label over-image text-foreground/85 mb-3">
                {t("openingLot")} · {auction.category.name}
              </p>

              {/* The name is allowed to be the largest thing on the page. */}
              <h1 className="display over-image max-w-[16ch] text-[clamp(2.75rem,9vw,8.5rem)]">
                {auction.product.name}
              </h1>

              <div className="border-line mt-10 flex flex-wrap items-end gap-x-14 gap-y-8 border-t pt-7">
                <div>
                  <p className="label text-muted-foreground mb-2">{t("current")}</p>
                  <AuctionPriceTicker
                    amount={auction.currentPrice}
                    flashOnChange={false}
                    className="display text-[clamp(2.25rem,5.5vw,4.5rem)]"
                  />
                  <ForeignPrice amount={auction.currentPrice} className="mt-1 text-sm" />
                </div>

                <div>
                  <p className="label text-muted-foreground mb-2">{t("endsIn")}</p>
                  <AuctionClock
                    endTime={auction.endTime}
                    serverTime={serverTime}
                    variant="hero"
                    className="text-[clamp(2.25rem,5.5vw,4.5rem)]"
                  />
                </div>

                <div className="flex gap-10">
                  <div>
                    <p className="label text-muted-foreground mb-2">{t("bids", { count: auction.bidCount })}</p>
                    <p className="mono-figure text-lg">{String(auction.bidCount).padStart(2, "0")}</p>
                  </div>
                  {auction.viewerCount !== undefined ? (
                    <div>
                      <p className="label text-muted-foreground mb-2">{t("watching", { count: "" }).trim()}</p>
                      <p className="mono-figure text-lg">{money.compactNumber(auction.viewerCount)}</p>
                    </div>
                  ) : null}
                </div>

                <Link
                  href={`/auctions/${auction.id}`}
                  className="group label bg-signal text-signal-ink hover:bg-foreground ml-auto inline-flex items-center gap-3 px-8 py-5 transition-colors"
                >
                  {t("bidNow")}
                  <ArrowRight className="size-4 transition-transform group-hover:translate-x-1" />
                </Link>
              </div>
            </m.div>
          </AnimatePresence>

          {showControls ? (
            <div className="mt-8 flex items-center gap-3">
              <button
                type="button"
                onClick={() => go(index - 1)}
                aria-label={t("previous")}
                className="text-foreground/70 hover:text-foreground p-2 transition-colors"
              >
                <ArrowLeft className="size-4" aria-hidden="true" />
              </button>

              <ol className="flex flex-1 gap-2">
                {auctions.map((lot, position) => (
                  <li key={lot.id} className="flex-1">
                    <button
                      type="button"
                      onClick={() => go(position)}
                      aria-label={t("goTo", { index: position + 1, name: lot.product.name })}
                      aria-current={position === index ? "true" : undefined}
                      className="group block w-full py-3"
                    >
                      <span className="bg-foreground/20 group-hover:bg-foreground/35 relative block h-0.5 overflow-hidden transition-colors">
                        {position < index ? <span className="bg-foreground absolute inset-0" /> : null}
                        {position === index ? (
                          autoplay ? (
                            <span
                              key={`${lot.id}-${index}`}
                              className="stage-progress bg-foreground absolute inset-0 origin-left"
                              style={{
                                animationDuration: `${SLIDE_MS}ms`,
                                animationPlayState: running ? "running" : "paused",
                              }}
                              onAnimationEnd={() => go(index + 1)}
                            />
                          ) : (
                            <span className="bg-foreground absolute inset-0" />
                          )
                        ) : null}
                      </span>
                    </button>
                  </li>
                ))}
              </ol>

              <button
                type="button"
                onClick={() => go(index + 1)}
                aria-label={t("next")}
                className="text-foreground/70 hover:text-foreground p-2 transition-colors"
              >
                <ArrowRight className="size-4" aria-hidden="true" />
              </button>

              {!reduceMotion ? (
                <button
                  type="button"
                  onClick={() => setStopped((value) => !value)}
                  aria-label={stopped ? t("play") : t("pause")}
                  aria-pressed={stopped}
                  className="text-foreground/70 hover:text-foreground p-2 transition-colors"
                  data-testid="stage-toggle"
                >
                  {stopped ? <Play className="size-4" aria-hidden="true" /> : <Pause className="size-4" aria-hidden="true" />}
                </button>
              ) : null}

              <span className="mono-figure text-foreground/70 min-w-[3.5rem] text-right text-xs" aria-hidden="true">
                {String(index + 1).padStart(2, "0")} / {String(total).padStart(2, "0")}
              </span>
            </div>
          ) : null}
        </div>
      </div>
    </section>
  );
}
