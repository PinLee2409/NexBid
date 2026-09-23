"use client";

import { LivePulse } from "@/components/nexbid/live-pulse";
import { useEnumLabels } from "@/hooks/use-labels";
import { cn } from "@/lib/utils";
import type { AuctionStatus } from "@/types";

interface AuctionStatusBadgeProps {
  status: AuctionStatus;
  className?: string;
  size?: "sm" | "md";
}

/*
 * Status is set as micro-caps, not a pill. Only the three states that need a
 * decision carry colour — everything else is quiet ink, because a catalogue
 * full of coloured chips reads as noise rather than information.
 */
const STATUS_STYLES: Record<AuctionStatus, string> = {
  ACTIVE: "text-danger-text",
  SCHEDULED: "text-muted-foreground",
  ENDED: "text-dim",
  COMPLETED: "text-success",
  DRAFT: "text-dim",
  PENDING_APPROVAL: "text-warning",
  REJECTED: "text-danger-text",
  CANCELLED: "text-dim",
};

/** One treatment for every auction state (spec §5.1). */
export function AuctionStatusBadge({
  status,
  className,
  size = "md",
}: AuctionStatusBadgeProps) {
  const labels = useEnumLabels();

  return (
    <span
      className={cn(
        "inline-flex items-center gap-1.5",
        size === "sm" ? "label-sm" : "label",
        STATUS_STYLES[status],
        className,
      )}
    >
      {status === "ACTIVE" ? <LivePulse tone="danger" /> : null}
      {labels.auctionStatus(status)}
    </span>
  );
}
