"use client";

import { useEffect, useRef, useState } from "react";

import { toDuration, type Duration } from "@/lib/format";
import { getUrgency, type UrgencyLevel } from "@/lib/auction-rules";

export interface CountdownState {
  /** `null` before the browser clock takes over and no server time was given. */
  duration: Duration | null;
  msRemaining: number | null;
  urgency: UrgencyLevel;
  isComplete: boolean;
}

interface UseCountdownOptions {
  /** Server clock at page load. The client aligns to it (spec §11). */
  serverTime?: string;
  /** Fires once when the remaining time reaches zero. */
  onComplete?: () => void;
}

/**
 * Counts down to `targetTime` using a server-aligned clock.
 *
 * The browser is never the authority on whether an auction has ended — it only
 * renders the remaining time. The first render is derived purely from the
 * server timestamps so server and client markup agree; on mount the hook
 * measures the clock difference and ticks once a second from there.
 */
export function useCountdown(
  targetTime: string,
  { serverTime, onComplete }: UseCountdownOptions = {},
): CountdownState {
  const targetMs = new Date(targetTime).getTime();

  const [msRemaining, setMsRemaining] = useState<number | null>(() =>
    serverTime ? Math.max(0, targetMs - new Date(serverTime).getTime()) : null,
  );

  const onCompleteRef = useRef(onComplete);
  useEffect(() => {
    onCompleteRef.current = onComplete;
  });

  useEffect(() => {
    /**
     * Difference between the server clock and this device's clock, measured
     * once on mount so a skewed local clock cannot drift the countdown.
     */
    const offset = serverTime ? new Date(serverTime).getTime() - Date.now() : 0;
    let completed = false;

    function tick() {
      const remaining = Math.max(0, targetMs - (Date.now() + offset));
      setMsRemaining(remaining);

      if (remaining <= 0 && !completed) {
        completed = true;
        onCompleteRef.current?.();
      }
    }

    tick();
    const interval = window.setInterval(tick, 1000);
    return () => window.clearInterval(interval);
  }, [targetMs, serverTime]);

  return {
    duration: msRemaining === null ? null : toDuration(msRemaining),
    msRemaining,
    urgency: msRemaining === null ? "none" : getUrgency(msRemaining),
    isComplete: msRemaining !== null && msRemaining <= 0,
  };
}
