"use client";

import { Gavel } from "lucide-react";
import { useTranslations } from "next-intl";
import { useCallback, useEffect, useMemo, useState } from "react";

import { AuctionClock } from "@/components/nexbid/auction-clock";
import {
  AuctionClosingOverlay,
  AuctionExtendedOverlay,
  SoldOverlay,
} from "@/components/nexbid/auction-overlays";
import { BidTerminal } from "@/components/nexbid/bid-terminal";
import { LiveBidTape } from "@/components/nexbid/live-bid-tape";
import {
  StatePreview,
  type PreviewState,
} from "@/components/nexbid/state-preview";
import { useAuctionRoom, type AuctionRoom } from "@/hooks/use-auction-room";
import { formatCurrency } from "@/lib/format";
import { useSession } from "@/services/session-service";
import type { AuctionDetail } from "@/types";

interface AuctionRoomViewProps {
  auction: AuctionDetail;
  serverTime: string;
}

/**
 * Owns everything that moves on the lot page: the terminal, the tape, the
 * mobile action bar and the three event overlays. A development-only control
 * rail can force any auction state so each one can be reviewed on demand.
 */
export function AuctionRoomView({ auction, serverTime }: AuctionRoomViewProps) {
  const t = useTranslations("terminal");
  const tc = useTranslations("common");
  const { user } = useSession();
  const live = useAuctionRoom(auction, user?.id ?? null);

  const [preview, setPreview] = useState<PreviewState>("normal");
  const [previewEndTime, setPreviewEndTime] = useState<string | null>(null);
  const [resultOpen, setResultOpen] = useState(false);
  const [closingPlaying, setClosingPlaying] = useState(false);
  const [extendedOpen, setExtendedOpen] = useState(false);

  /** Everything a preview switch implies happens here, in the handler. */
  const handlePreviewChange = useCallback((next: PreviewState) => {
    setPreview(next);
    setPreviewEndTime(
      next === "finalMinute" ? new Date(Date.now() + 40_000).toISOString() : null,
    );
    if (next === "extended") setExtendedOpen(true);
    if (next === "sold" || next === "won") setClosingPlaying(true);
  }, []);

  /** A ticking, server-aligned clock for the values the overlays display. */
  const [now, setNow] = useState(() => new Date(serverTime).getTime());
  useEffect(() => {
    const offset = new Date(serverTime).getTime() - Date.now();
    const interval = window.setInterval(() => setNow(Date.now() + offset), 1000);
    return () => window.clearInterval(interval);
  }, [serverTime]);

  const room: AuctionRoom = useMemo(() => {
    switch (preview) {
      case "leading":
        return { ...live, isHighestBidder: true, outbid: false };
      case "outbid":
        return { ...live, isHighestBidder: false, outbid: true };
      case "finalMinute":
        return previewEndTime ? { ...live, endTime: previewEndTime } : live;
      case "sold":
      case "won":
        return { ...live, ended: true };
      default:
        return live;
    }
  }, [live, preview, previewEndTime]);

  const extensionSeconds = auction.antiSniping.extensionSeconds;

  /**
   * The hammer plays on the transition into `ended`, not on arrival at an
   * already-finished lot — so opening an old result does not replay it.
   */
  const [wasEnded, setWasEnded] = useState(room.ended);
  if (wasEnded !== room.ended) {
    setWasEnded(room.ended);
    if (room.ended) setClosingPlaying(true);
  }

  /** A real anti-sniping event opens the same overlay the preview does. */
  const [seenExtension, setSeenExtension] = useState(live.lastExtensionSeconds);
  if (seenExtension !== live.lastExtensionSeconds) {
    setSeenExtension(live.lastExtensionSeconds);
    if (live.lastExtensionSeconds !== null) setExtendedOpen(true);
  }

  const winnerBid = room.bids[0];
  const viewerWon =
    preview === "won" || (room.ended && winnerBid?.bidderId === user?.id);
  const winnerName =
    preview === "won"
      ? (user?.displayName ?? t("you"))
      : (winnerBid?.bidderDisplayName ?? null);

  return (
    <>
      <BidTerminal
        auction={auction}
        room={room}
        user={user}
        serverTime={serverTime}
        className="lg:sticky lg:top-24"
      />

      <section className="mt-10" aria-labelledby="tape-heading">
        <div className="border-line bg-surface border p-5 sm:p-6">
          <div className="flex items-center justify-between gap-3">
            <h2 id="tape-heading" className="label text-muted-foreground">
              {t("liveBids")}
            </h2>
            <span className="mono-figure text-dim text-xs">
              {String(room.bidCount).padStart(2, "0")}
            </span>
          </div>

          <LiveBidTape
            className="mt-3"
            bids={room.bids}
            freshBidIds={room.freshBidIds}
            viewerId={user?.id ?? null}
          />
        </div>
      </section>

      {/* Phones keep the price, the clock and the action within thumb reach. */}
      {auction.status === "ACTIVE" && !room.ended ? (
        <div className="bg-background/95 border-line fixed inset-x-0 bottom-0 z-40 border-t p-3 backdrop-blur-md lg:hidden">
          <div className="mx-auto flex max-w-[1320px] items-center gap-3">
            <div className="min-w-0 flex-1">
              <p className="text-muted-foreground text-[11px]">{t("currentBid")}</p>
              <p className="figure truncate text-lg leading-tight">
                {formatCurrency(room.currentPrice)}
              </p>
            </div>

            <AuctionClock
              endTime={room.endTime}
              serverTime={serverTime}
              variant="inline"
              onComplete={room.markEnded}
            />

            <a
              href="#bid-panel"
              className="label bg-signal text-signal-ink inline-flex shrink-0 items-center gap-2 px-6 py-4"
            >
              <Gavel className="size-4" />
              {tc("placeBid")}
            </a>
          </div>
        </div>
      ) : null}

      <AuctionExtendedOverlay
        open={extendedOpen}
        extensionSeconds={extensionSeconds}
        remainingBeforeMs={Math.max(
          0,
          new Date(room.endTime).getTime() - now - extensionSeconds * 1000,
        )}
        onDone={() => {
          setExtendedOpen(false);
          if (preview === "extended") handlePreviewChange("normal");
          live.dismissExtension();
        }}
      />

      <AuctionClosingOverlay
        open={closingPlaying}
        onDone={() => {
          setClosingPlaying(false);
          setResultOpen(true);
        }}
      />

      <SoldOverlay
        open={resultOpen}
        auction={auction}
        finalPrice={room.currentPrice}
        winnerName={winnerName}
        viewerWon={viewerWon}
        onClose={() => {
          setResultOpen(false);
          // Leaving the result also leaves the forced state.
          if (preview === "sold" || preview === "won") {
            handlePreviewChange("normal");
          }
        }}
      />

      <StatePreview value={preview} onChange={handlePreviewChange} />
    </>
  );
}
