"use client";

import { BookmarkPlus, X } from "lucide-react";
import { useTranslations } from "next-intl";
import { useTransition } from "react";
import { toast } from "sonner";

import { useAsyncData } from "@/hooks/use-async-data";
import { useApiErrorMessage } from "@/hooks/use-labels";
import { addQuickReply, deleteQuickReply, listQuickReplies } from "@/services/market-service";

interface QuickRepliesProps {
  /** EN: What is typed now, to keep as a reply. / VI: Những gì đang gõ, để lưu thành câu trả lời. */
  draft: string;
  onPick: (body: string) => void;
  disabled?: boolean;
}

/**
 * EN: Saved replies as chips above the message box: a tap puts one in the box, ready to edit or send. With nothing saved
 *     yet, a few common ones are offered; "Save" keeps what is typed.
 * VI: Các câu trả lời đã lưu dạng nút nhỏ phía trên ô tin nhắn: chạm một lần là câu đó vào ô, sẵn để sửa hoặc gửi. Khi
 *     chưa lưu câu nào, vài câu thường dùng được gợi ý; "Lưu" giữ lại những gì đang gõ.
 */
export function QuickReplies({ draft, onPick, disabled }: QuickRepliesProps) {
  const t = useTranslations("quickReplies");
  const errorMessage = useApiErrorMessage();
  const { state, refresh } = useAsyncData("quick-replies", listQuickReplies);
  const [isPending, startTransition] = useTransition();
  const saved = state.status === "success" ? state.data : [];
  const suggestions = saved.length === 0 && state.status === "success"
    ? [t("suggestShipped"), t("suggestAvailable"), t("suggestThanks")]
    : [];

  function run(task: () => Promise<unknown>, done?: string) {
    startTransition(async () => {
      try {
        await task();
        if (done) toast.success(done);
        refresh();
      } catch (error) {
        toast.error(errorMessage(error));
      }
    });
  }

  return (
    <div className="mb-2 flex flex-wrap items-center gap-1.5" data-testid="quick-replies">
      {saved.map((reply) => (
        <span key={reply.id} className="border-line inline-flex max-w-full items-center border text-xs">
          <button
            type="button"
            disabled={disabled}
            onClick={() => onPick(reply.body)}
            className="hover:text-signal-text max-w-[16rem] truncate px-2 py-1"
            title={reply.body}
          >
            {reply.body}
          </button>
          <button
            type="button"
            aria-label={t("delete", { body: reply.body })}
            disabled={isPending}
            onClick={() => run(() => deleteQuickReply(reply.id))}
            className="text-dim hover:text-foreground border-line border-l px-1.5 py-1"
          >
            <X className="size-3" />
          </button>
        </span>
      ))}
      {suggestions.map((body) => (
        <button
          key={body}
          type="button"
          disabled={disabled}
          onClick={() => onPick(body)}
          className="border-line text-muted-foreground hover:text-signal-text border border-dashed px-2 py-1 text-xs"
        >
          {body}
        </button>
      ))}
      <button
        type="button"
        disabled={isPending || !draft.trim() || saved.length >= 10}
        onClick={() => run(() => addQuickReply(draft), t("saved"))}
        className="text-dim hover:text-foreground inline-flex items-center gap-1 px-1 py-1 text-xs disabled:opacity-40"
      >
        <BookmarkPlus className="size-3.5" aria-hidden="true" />
        {t("save")}
      </button>
    </div>
  );
}
