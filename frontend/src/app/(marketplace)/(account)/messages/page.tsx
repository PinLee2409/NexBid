"use client";

import { Loader2, MessagesSquare, Send, ShieldCheck } from "lucide-react";
import { useFormatter, useTranslations } from "next-intl";
import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { Suspense, useEffect, useRef, useState, useTransition, type FormEvent } from "react";

import { ApiErrorState } from "@/components/common/api-error-state";
import { EmptyState } from "@/components/common/empty-state";
import { AuctionListRowSkeleton } from "@/components/common/loading-skeleton";
import { PageHeader } from "@/components/layout/page-header";
import { Button } from "@/components/ui/button";
import { Textarea } from "@/components/ui/textarea";
import { useAsyncData } from "@/hooks/use-async-data";
import { useApiErrorMessage } from "@/hooks/use-labels";
import { cn } from "@/lib/utils";
import {
  listThreads,
  announceTyping,
  markThreadSeen,
  onConversationSignal,
  onMessageArrival,
  openThread,
  sendMessage,
  type MessageThread,
  type OrderMessage,
} from "@/services/message-service";

/**
 * EN: The inbox: one conversation per order, with the buyer or the seller. `?order=` opens one. Contact details are
 *     refused by the server, so the deal stays covered by NexBid.
 * VI: Hộp thư: mỗi đơn một cuộc trò chuyện, với người mua hoặc người bán. `?order=` mở một cuộc. Server từ chối thông tin
 *     liên lạc, để giao dịch vẫn được NexBid bảo vệ.
 */
export default function MessagesPage() {
  return (
    <Suspense fallback={<AuctionListRowSkeleton />}>
      <Inbox />
    </Suspense>
  );
}

function Inbox() {
  const t = useTranslations("messages");
  const router = useRouter();
  const selected = useSearchParams().get("order");
  const [version, setVersion] = useState(0);
  const { state } = useAsyncData(`message-threads-${version}`, listThreads);

  // EN: A message for another thread refreshes the list. / VI: Tin của cuộc trò chuyện khác làm mới danh sách.
  useEffect(() => onMessageArrival(() => setVersion((value) => value + 1)), []);

  return (
    <>
      <PageHeader title={t("title")} description={t("subtitle")} />
      <p className="text-muted-foreground mt-3 flex items-center gap-2 text-xs">
        <ShieldCheck className="text-signal-text size-4" aria-hidden="true" />
        {t("safety")}
      </p>

      <div className="mt-8 grid gap-6 lg:grid-cols-[minmax(0,18rem)_minmax(0,1fr)]">
        <nav aria-label={t("threads")}>
          {state.status === "loading" ? (
            <AuctionListRowSkeleton />
          ) : state.status === "error" ? (
            <ApiErrorState icon={MessagesSquare} error={state.error} />
          ) : state.data.length === 0 && !selected ? (
            <EmptyState icon={MessagesSquare} title={t("emptyTitle")} description={t("emptyBody")} />
          ) : (
            <ul className="border-line divide-line divide-y border-y">
              {state.data.map((thread) => (
                <li key={thread.orderId}>
                  <ThreadLink thread={thread} active={thread.orderId === selected} />
                </li>
              ))}
            </ul>
          )}
        </nav>

        {selected ? (
          <Conversation
            key={selected}
            orderId={selected}
            onChanged={() => setVersion((value) => value + 1)}
            onMissing={() => router.replace("/messages")}
          />
        ) : null}
      </div>
    </>
  );
}

function ThreadLink({ thread, active }: { thread: MessageThread; active: boolean }) {
  const t = useTranslations("messages");
  const format = useFormatter();
  return (
    <Link
      href={`/messages?order=${thread.orderId}`}
      aria-current={active ? "page" : undefined}
      className={cn("block px-3 py-3 transition-colors", active ? "bg-surface" : "hover:bg-surface")}
    >
      <span className="flex items-center justify-between gap-2">
        <span className="truncate text-sm font-semibold">{thread.lotTitle}</span>
        {thread.unread > 0 ? (
          <span className="bg-signal text-signal-foreground mono-figure rounded-full px-1.5 text-[11px]">
            {thread.unread}
          </span>
        ) : null}
      </span>
      <span className="text-muted-foreground block truncate text-xs">
        {thread.asSeller ? t("withBuyer", { name: thread.counterpart }) : t("withSeller", { name: thread.counterpart })}
        {thread.lastAt ? ` · ${format.relativeTime(new Date(thread.lastAt))}` : ""}
      </span>
      {thread.lastMessage ? (
        <span className="text-dim mt-0.5 block truncate text-xs">
          {thread.lastMine ? `${t("you")}: ` : ""}
          {thread.lastMessage}
        </span>
      ) : null}
    </Link>
  );
}

function Conversation({
  orderId,
  onChanged,
  onMissing,
}: {
  orderId: string;
  onChanged: () => void;
  onMissing: () => void;
}) {
  const t = useTranslations("messages");
  const format = useFormatter();
  const errorMessage = useApiErrorMessage();
  const { state } = useAsyncData(`message-thread-${orderId}`, () => openThread(orderId));
  const [live, setLive] = useState<OrderMessage[]>([]);
  const [typing, setTyping] = useState(false);
  const [seenAt, setSeenAt] = useState<string | null>(null);
  const [draft, setDraft] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [isPending, startTransition] = useTransition();
  const bottom = useRef<HTMLDivElement>(null);

  useEffect(
    () =>
      onMessageArrival((message) => {
        if (message.orderId !== orderId) return;
        setLive((current) => (current.some((each) => each.id === message.id) ? current : [...current, message]));
        setTyping(false);
        // EN: Seen on screen: read it, so the count stays right and the writer sees "seen". / VI: Đã hiện trên màn hình: đánh dấu đã đọc để con số đúng và người viết thấy "đã xem".
        void markThreadSeen(orderId).catch(() => undefined);
      }),
    [orderId],
  );

  useEffect(() => {
    let quiet: ReturnType<typeof setTimeout> | undefined;
    const stop = onConversationSignal((signal) => {
      if (signal.orderId !== orderId) return;
      if (signal.kind === "read") {
        setSeenAt(signal.readAt);
      } else {
        setTyping(true);
        clearTimeout(quiet);
        quiet = setTimeout(() => setTyping(false), 5000);
      }
    });
    return () => {
      stop();
      clearTimeout(quiet);
    };
  }, [orderId]);

  const loaded = state.status === "success" ? state.data.messages : [];
  const messages = [...loaded, ...live.filter((message) => !loaded.some((each) => each.id === message.id))];
  // EN: "Seen" sits under my last message once the other side read it. / VI: "Đã xem" nằm dưới tin cuối của mình khi bên kia đã đọc.
  const lastMine = [...messages].reverse().find((message) => message.mine);
  const lastMineSeen =
    lastMine !== undefined &&
    (Boolean(lastMine.readAt) || (seenAt !== null && new Date(seenAt) >= new Date(lastMine.createdAt)));

  useEffect(() => {
    bottom.current?.scrollIntoView({ block: "end" });
  }, [messages.length]);

  useEffect(() => {
    if (state.status === "error") onMissing();
  }, [state.status, onMissing]);

  function send(event: FormEvent) {
    event.preventDefault();
    if (!draft.trim()) return;
    startTransition(async () => {
      try {
        const sent = await sendMessage(orderId, draft);
        setLive((current) => [...current, sent]);
        setDraft("");
        setError(null);
        onChanged();
      } catch (failure) {
        setError(errorMessage(failure));
      }
    });
  }

  if (state.status === "loading") return <AuctionListRowSkeleton />;
  if (state.status === "error") return null;
  const { thread } = state.data;

  return (
    <section aria-labelledby="thread-heading" className="border-line flex min-h-[24rem] flex-col border">
      <header className="border-line border-b px-4 py-3">
        <h2 id="thread-heading" className="text-sm font-semibold">
          <Link href={`/auctions/${thread.auctionId}`} className="hover:underline">
            {thread.lotTitle}
          </Link>
        </h2>
        <p className="text-muted-foreground text-xs">
          {thread.asSeller ? t("withBuyer", { name: thread.counterpart }) : t("withSeller", { name: thread.counterpart })}
        </p>
      </header>

      <ol className="flex-1 space-y-3 overflow-y-auto px-4 py-4" aria-live="polite">
        {messages.length === 0 ? <li className="text-dim text-sm">{t("startHint")}</li> : null}
        {messages.map((message) => (
          <li key={message.id} className={cn("flex", message.mine ? "justify-end" : "justify-start")}>
            <div
              className={cn(
                "max-w-[80%] px-3 py-2 text-sm leading-relaxed whitespace-pre-line",
                message.mine ? "bg-foreground text-background" : "bg-surface",
              )}
            >
              <p>{message.body}</p>
              <p className={cn("mono-figure mt-1 text-[10px]", message.mine ? "opacity-70" : "text-dim")}>
                {message.mine ? t("you") : message.sender} ·{" "}
                {format.dateTime(new Date(message.createdAt), { dateStyle: "short", timeStyle: "short" })}
              </p>
              {message === lastMine && lastMineSeen ? (
                <p className="mono-figure mt-0.5 text-right text-[10px] opacity-70" data-testid="message-seen">
                  {t("seen")}
                </p>
              ) : null}
            </div>
          </li>
        ))}
        {typing ? (
          <li className="text-dim text-xs italic" data-testid="message-typing">
            {t("typing", { name: thread.counterpart })}
          </li>
        ) : null}
        <div ref={bottom} />
      </ol>

      {thread.open ? (
        <form onSubmit={send} className="border-line border-t p-3">
          <label htmlFor="message-draft" className="sr-only">
            {t("draftLabel")}
          </label>
          <Textarea
            id="message-draft"
            value={draft}
            maxLength={1000}
            rows={2}
            placeholder={t("draftPlaceholder")}
            disabled={isPending}
            onChange={(event) => {
              setDraft(event.target.value);
              setError(null);
              if (event.target.value.trim()) announceTyping(orderId);
            }}
            onKeyDown={(event) => {
              if (event.key === "Enter" && !event.shiftKey) {
                event.preventDefault();
                event.currentTarget.form?.requestSubmit();
              }
            }}
          />
          {error ? (
            <p role="alert" className="text-danger-text mt-2 text-xs">
              {error}
            </p>
          ) : null}
          <div className="mt-2 flex justify-end">
            <Button type="submit" size="sm" disabled={isPending || !draft.trim()}>
              {isPending ? <Loader2 className="size-4 animate-spin" /> : <Send className="size-4" />}
              {t("send")}
            </Button>
          </div>
        </form>
      ) : (
        <p className="border-line text-dim border-t px-4 py-3 text-xs">{t("closed")}</p>
      )}
    </section>
  );
}
