"use client";

import { useCallback, useEffect, useReducer } from "react";

import type { AuctionDetail, AuctionEvent, Bid } from "@/types";
import { subscribeToAuction } from "@/services/realtime-service";

/**
 * Live state for one auction.
 *
 * Everything that can change while the page is open — price, bid count, end
 * time, history, viewers — lives here and is driven by `AuctionEvent`s. The
 * page never refetches or reloads; swapping the mock transport for a real
 * WebSocket does not touch this reducer.
 */

export interface AuctionRoomState {
  currentPrice: number;
  bidCount: number;
  endTime: string;
  viewerCount: number;
  bids: Bid[];
  /** Set when the signed-in user held the top bid and just lost it. */
  outbid: boolean;
  isHighestBidder: boolean;
  /** Seconds added by the most recent anti-sniping extension, if any. */
  lastExtensionSeconds: number | null;
  /** Bid ids that arrived after mount, so only those animate in. */
  freshBidIds: string[];
  ended: boolean;
}

type RoomAction =
  | { type: "event"; event: AuctionEvent; viewerId: string | null }
  | { type: "ended" }
  | { type: "dismissOutbid" }
  | { type: "dismissExtension" };

function reducer(state: AuctionRoomState, action: RoomAction): AuctionRoomState {
  switch (action.type) {
    case "event": {
      const { event, viewerId } = action;

      switch (event.type) {
        case "BID_PLACED": {
          const byViewer = event.bid.bidderId === viewerId;

          return {
            ...state,
            currentPrice: event.currentPrice,
            bidCount: event.totalBids,
            bids: [event.bid, ...state.bids],
            freshBidIds: [event.bid.id, ...state.freshBidIds].slice(0, 6),
            isHighestBidder: byViewer,
            // Losing the top spot to someone else is the only way to be outbid.
            outbid: !byViewer && state.isHighestBidder ? true : state.outbid,
          };
        }

        case "AUCTION_EXTENDED":
          return {
            ...state,
            endTime: event.endTime,
            lastExtensionSeconds: event.extensionSeconds,
          };

        case "VIEWERS_CHANGED":
          return { ...state, viewerCount: event.viewerCount };

        case "AUCTION_ENDED":
          return { ...state, ended: true };

        default:
          return state;
      }
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
    viewerCount: auction.viewerCount,
    bids: auction.recentBids,
    outbid: false,
    isHighestBidder: auction.viewerState?.isHighestBidder ?? false,
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
}

export function useAuctionRoom(
  auction: AuctionDetail,
  viewerId: string | null,
): AuctionRoom {
  const [state, dispatch] = useReducer(reducer, auction, initialState);

  useEffect(() => {
    return subscribeToAuction(auction.id, (event) => {
      dispatch({ type: "event", event, viewerId });
    });
  }, [auction.id, viewerId]);

  const markEnded = useCallback(() => dispatch({ type: "ended" }), []);
  const dismissOutbid = useCallback(() => dispatch({ type: "dismissOutbid" }), []);
  const dismissExtension = useCallback(
    () => dispatch({ type: "dismissExtension" }),
    [],
  );

  return { ...state, markEnded, dismissOutbid, dismissExtension };
}
