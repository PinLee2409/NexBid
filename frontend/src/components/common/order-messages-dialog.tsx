"use client";

import { Loader2, MessagesSquare } from "lucide-react";
import { useFormatter, useTranslations } from "next-intl";
import { useState } from "react";

import { Button } from "@/components/ui/button";
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { useApiErrorMessage } from "@/hooks/use-labels";
import { cn } from "@/lib/utils";
import { listOrderMessages, type AdminOrderMessage } from "@/services/admin-service";

/**
 * EN: For an admin settling a dispute: the order's messages, read-only. Loaded only when opened, since every read is
 *     written to the audit log.
 * VI: Cho admin khi xử lý khiếu nại: tin nhắn của đơn, chỉ đọc. Chỉ tải khi mở, vì mỗi lần đọc đều được ghi vào nhật ký.
 */
export function OrderMessagesButton({ orderId }: { orderId: string }) {
  const t = useTranslations("admin");
  const format = useFormatter();
  const errorMessage = useApiErrorMessage();
  const [open, setOpen] = useState(false);
  const [messages, setMessages] = useState<AdminOrderMessage[] | null>(null);
  const [error, setError] = useState<string | null>(null);

  function show() {
    setOpen(true);
    setMessages(null);
    setError(null);
    listOrderMessages(orderId).then(setMessages, (cause: unknown) => setError(errorMessage(cause)));
  }

  return (
    <>
      <Button size="sm" variant="ghost" className="mt-2 h-7 px-2 text-xs" onClick={show}>
        <MessagesSquare className="size-3.5" />
        {t("orderMessages")}
      </Button>
      <Dialog open={open} onOpenChange={setOpen}>
        <DialogContent className="sm:max-w-lg">
          <DialogHeader>
            <DialogTitle>{t("orderMessagesTitle")}</DialogTitle>
            <DialogDescription>{t("orderMessagesBody")}</DialogDescription>
          </DialogHeader>
          <div className="border-line mt-4 max-h-[60vh] overflow-y-auto border-t pt-4">
            {error ? (
              <p className="text-danger-text text-sm">{error}</p>
            ) : messages === null ? (
              <Loader2 className="text-dim mx-auto size-5 animate-spin" aria-label={t("orderMessagesLoading")} />
            ) : messages.length === 0 ? (
              <p className="text-muted-foreground text-sm">{t("orderMessagesNone")}</p>
            ) : (
              <ol className="space-y-3">
                {messages.map((message) => (
                  <li
                    key={message.id}
                    className={cn("border-l-2 pl-3", message.fromBuyer ? "border-signal-text" : "border-line-strong")}
                  >
                    <p className="text-dim text-[11px]">
                      <span className="text-foreground font-medium">{message.sender}</span>
                      {" · "}
                      {message.fromBuyer ? t("orderMessagesBuyer") : t("orderMessagesSeller")}
                      {" · "}
                      <span className="mono-figure">
                        {format.dateTime(new Date(message.createdAt), { dateStyle: "medium", timeStyle: "short" })}
                      </span>
                    </p>
                    <p className="mt-1 text-sm leading-relaxed whitespace-pre-line">{message.body}</p>
                  </li>
                ))}
              </ol>
            )}
          </div>
        </DialogContent>
      </Dialog>
    </>
  );
}
