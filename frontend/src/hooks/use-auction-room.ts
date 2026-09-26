"use client";

import { useCallback, useEffect, useReducer, useRef } from "react";

import { getAutoBid, listBids } from "@/services/bid-service";
import { subscribeToAuction } from "@/services/realtime-service";
import type { AuctionDetail, AuctionEvent, AuctionStatus, AutoBid, Bid } from "@/types";

/**
 * Live state for one auction.
 *
 * Everything that can change while the page is open — price, bid count, end
 * time, status, history — lives here and is driven by `AuctionEvent`s from the
 * socket. The broadcast never says who bid, so whether the signed-in reader
 * leads is settled by re-reading the bid history (which marks their own bids)
 * right after each bid.
 */

export interface AuctionRoomState {
  currentPrice: number;
  bidCount: number;
  endTime: string;
  status: AuctionStatus;
  /** Absent until the API reports viewers (spec §20.3). */
  viewerCount?: number;
  bids: Bid[];
  /** Set when the signed-in user held the top bid and just lost it. */
  outbid: boolean;
  isHighestBidder: boolean;
  /** The reader's own auto bid on this lot, once known. */
  autoBid: AutoBid | null;
  /** Seconds added by the most recent anti-sniping extension, if any. */
  lastExtensionSeconds: number | null;
  /** Bid ids that arrived after mount, so only those animate in. */
  freshBidIds: string[];
  ended: boolean;
}

type RoomAction =
  | { type: "event"; event: AuctionEvent }
  | { type: "reconcile"; bids: Bid[]; autoBid: AutoBid | null; viewerId: string }
  | { type: "ended" }
  | { type: "dismissOutbid" }
  | { type: "dismissExtension" };

function reducer(state: AuctionRoomState, action: RoomAction): AuctionRoomState {
  switch (action.type) {
    case "event": {
      const { event } = action;

      switch (event.type) {
        case "BID_PLACED":
          return {
            ...state,
            currentPrice: event.currentPrice,
            bidCount: event.totalBids,
            bids: [event.bid, ...state.bids],
            freshBidIds: [event.bid.id, ...state.freshBidIds].slice(0, 6),
          };

        case "AUCTION_EXTENDED": {
          const added =
            event.extensionSeconds ||
            Math.max(0, Math.round((new Date(event.endTime).getTime() - new Date(state.endTime).getTime()) / 1000));
          return { ...state, endTime: event.endTime, lastExtensionSeconds: added };
        }

        case "AUCTION_STARTED":
          return state.status === "SCHEDULED" ? { ...state, status: "ACTIVE" } : state;

        case "VIEWERS_CHANGED":
          return { ...state, viewerCount: event.viewerCount };

        case "AUCTION_ENDED":
          return { ...state, ended: true, status: "ENDED" };

        default:
          return state;
      }
    }

    case "reconcile": {
      // EN: Live rows carry a made-up id; keep their animation on the real rows that replace them.
      // VI: Dòng realtime mang id tạm; giữ hiệu ứng của chúng cho các dòng thật thay thế.
      const freshAmounts = new Set(
        state.bids.filter((bid) => state.freshBidIds.includes(bid.id)).map((bid) => bid.amount),
      );
      const leading = action.bids[0]?.bidderId === action.viewerId;
      return {
        ...state,
        bids: action.bids,
        freshBidIds: action.bids.filter((bid) => freshAmounts.has(bid.amount)).map((bid) => bid.id),
        isHighestBidder: leading,
        // Losing the top spot to someone else is the only way to be outbid.
        outbid: state.isHighestBidder && !leading ? true : leading ? false : state.outbid,
        autoBid: action.autoBid,
      };
    }

    case "ended":
      return state.ended ? state : { ...state, ended: true };

    case "dismissOutbid":
      return { ...state, outbid: false };

    case "dismissExtension":
      return { ...state, lastExtensionSeconds: null };

    default:
      return state;
  }
}

function initialState(auction: AuctionDetail): AuctionRoomState {
  return {
    currentPrice: auction.currentPrice,
    bidCount: auction.bidCount,
    endTime: auction.endTime,
    status: auction.status,
    viewerCount: auction.viewerCount,
    bids: auction.recentBids,
    outbid: false,
    isHighestBidder: false,
    autoBid: null,
    lastExtensionSeconds: null,
    freshBidIds: [],
    ended:
      auction.status === "ENDED" ||
      auction.status === "COMPLETED" ||
      auction.status === "CANCELLED",
  };
}

export interface AuctionRoom extends AuctionRoomState {
  /** Called by the countdown when it reaches zero. */
  markEnded: () => void;
  dismissOutbid: () => void;
  dismissExtension: () => void;
  /** Re-reads where the signed-in reader stands, e.g. after they bid or set an auto bid. */
  refreshViewer: () => void;
}

export function useAuctionRoom(
  auction: AuctionDetail,
  viewerId: string | null,
): AuctionRoom {
  const [state, dispatch] = useReducer(reducer, auction, initialState);
  const pending = useRef<number | null>(null);

  const reconcile = useCallback(async () => {
    if (!viewerId) return;
    try {
      const [bids, autoBid] = await Promise.all([listBids(auction.id), getAutoBid(auction.id)]);
      dispatch({ type: "reconcile", bids, autoBid, viewerId });
    } catch {
      // The live state stays; the next bid tries again.
    }
  }, [auction.id, viewerId]);

  /** Bids tend to arrive in bursts; one re-read covers a burst. */
  const scheduleReconcile = useCallback(() => {
    if (!viewerId) return;
    if (pending.current !== null) window.clearTimeout(pending.current);
    pending.current = window.setTimeout(() => {
      pending.current = null;
      void reconcile();
    }, 400);
  }, [reconcile, viewerId]);

  useEffect(() => {
    // Initial read of the reader's own standing, once the session is known.
    const timer = window.setTimeout(() => void reconcile(), 0);
    return () => window.clearTimeout(timer);
  }, [reconcile]);

  useEffect(() => {
    return subscribeToAuction(auction.id, (event) => {
      dispatch({ type: "event", event });
      if (event.type === "BID_PLACED") scheduleReconcile();
    });
  }, [auction.id, scheduleReconcile]);

  useEffect(() => () => {
    if (pending.current !== null) window.clearTimeout(pending.current);
  }, []);

  const markEnded = useCallback(() => dispatch({ type: "ended" }), []);
  const dismissOutbid = useCallback(() => dispatch({ type: "dismissOutbid" }), []);
  const dismissExtension = useCallback(
    () => dispatch({ type: "dismissExtension" }),
    [],
  );
  const refreshViewer = useCallback(() => void reconcile(), [reconcile]);

  return { ...state, markEnded, dismissOutbid, dismissExtension, refreshViewer };
}
