"use client";

import { Loader2, MessageCircleQuestion } from "lucide-react";
import { useFormatter, useTranslations } from "next-intl";
import Link from "next/link";
import { useState, useTransition } from "react";
import { toast } from "sonner";

import { FormField } from "@/components/common/form-field";
import { Button } from "@/components/ui/button";
import { Textarea } from "@/components/ui/textarea";
import { useApiErrorMessage } from "@/hooks/use-labels";
import { answerQuestion, askQuestion, type LotQuestion } from "@/services/question-service";
import { useSession } from "@/services/session-service";
import type { AuctionStatus } from "@/types";

const OPEN: AuctionStatus[] = ["SCHEDULED", "ACTIVE"];

/**
 * EN: Questions and answers on a lot. Everyone reads them; a signed-in buyer asks while the lot is open; the seller
 *     answers each one, once, right under it.
 * VI: Hỏi đáp trên một lô. Ai cũng đọc được; người mua đã đăng nhập hỏi khi lô còn mở; người bán trả lời từng câu, một
 *     lần, ngay bên dưới.
 */
export function LotQuestions({
  auctionId,
  sellerId,
  status,
  initial,
}: {
  auctionId: string;
  sellerId: string;
  status: AuctionStatus;
  initial: LotQuestion[];
}) {
  const t = useTranslations("auction");
  const format = useFormatter();
  const { user } = useSession();
  const [questions, setQuestions] = useState(initial);

  const isSeller = user?.id === sellerId;
  const open = OPEN.includes(status);

  function replace(updated: LotQuestion) {
    setQuestions((current) => current.map((question) => (question.id === updated.id ? updated : question)));
  }

  return (
    <section id="questions" className="border-line mt-14 scroll-mt-24 border-t pt-8" aria-labelledby="questions-heading">
      <h2 id="questions-heading" className="display text-2xl">
        {t("questionsTitle")}
      </h2>
      <p className="text-muted-foreground mt-2 max-w-2xl text-sm">{t("questionsSubtitle")}</p>

      {open && !isSeller ? (
        user ? (
          <AskForm auctionId={auctionId} onAsked={(question) => setQuestions((current) => [question, ...current])} />
        ) : (
          <p className="mt-6 text-sm">
            <Link
              href={`/login?redirect=${encodeURIComponent(`/auctions/${auctionId}#questions`)}`}
              className="text-signal-text underline-offset-4 hover:underline"
            >
              {t("questionsSignIn")}
            </Link>
          </p>
        )
      ) : null}

      {questions.length === 0 ? (
        <p className="text-dim mt-6 flex items-center gap-2 text-sm">
          <MessageCircleQuestion className="size-4" aria-hidden="true" />
          {t("questionsEmpty")}
        </p>
      ) : (
        <ul className="mt-6 space-y-6">
          {questions.map((question) => (
            <li key={question.id} className="border-line border-l-2 pl-4">
              <p className="text-sm leading-relaxed font-medium">{question.body}</p>
              <p className="mono-figure text-dim mt-1 text-[11px]">
                {question.asker}
                {" · "}
                {format.dateTime(new Date(question.createdAt), { dateStyle: "medium" })}
              </p>
              {question.answer ? (
                <div className="bg-surface mt-3 px-3.5 py-2.5">
                  <p className="label-sm text-dim">{t("questionsSellerAnswer")}</p>
                  <p className="mt-1 text-sm leading-relaxed whitespace-pre-line">{question.answer}</p>
                </div>
              ) : isSeller ? (
                <AnswerForm question={question} onAnswered={replace} />
              ) : (
                <p className="label-sm text-warning mt-2">{t("questionsWaiting")}</p>
              )}
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}

function AskForm({ auctionId, onAsked }: { auctionId: string; onAsked: (question: LotQuestion) => void }) {
  const t = useTranslations("auction");
  const errorMessage = useApiErrorMessage();
  const [body, setBody] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [isPending, startTransition] = useTransition();

  function send() {
    if (body.trim().length < 5) {
      setError(t("questionsTooShort"));
      return;
    }
    startTransition(async () => {
      try {
        onAsked(await askQuestion(auctionId, body));
      } catch (failure) {
        setError(errorMessage(failure));
        return;
      }
      setBody("");
      toast.success(t("questionsPosted"), { description: t("questionsPostedBody") });
    });
  }

  return (
    <div className="mt-6 max-w-2xl space-y-3">
      <FormField label={t("questionsAskLabel")} error={error}>
        {(field) => (
          <Textarea
            {...field}
            rows={3}
            maxLength={500}
            value={body}
            disabled={isPending}
            placeholder={t("questionsAskPlaceholder")}
            onChange={(event) => {
              setBody(event.target.value);
              setError(null);
            }}
          />
        )}
      </FormField>
      <Button size="sm" onClick={send} disabled={isPending}>
        {isPending ? <Loader2 className="size-4 animate-spin" /> : null}
        {t("questionsAsk")}
      </Button>
    </div>
  );
}

function AnswerForm({ question, onAnswered }: { question: LotQuestion; onAnswered: (question: LotQuestion) => void }) {
  const t = useTranslations("auction");
  const tc = useTranslations("common");
  const errorMessage = useApiErrorMessage();
  const [answer, setAnswer] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [isPending, startTransition] = useTransition();

  function send() {
    if (!answer.trim()) {
      setError(tc("required"));
      return;
    }
    startTransition(async () => {
      try {
        onAnswered(await answerQuestion(question.id, answer));
      } catch (failure) {
        setError(errorMessage(failure));
        return;
      }
      toast.success(t("questionsAnswered"));
    });
  }

  return (
    <div className="mt-3 space-y-2">
      <FormField label={t("questionsAnswerLabel")} error={error}>
        {(field) => (
          <Textarea
            {...field}
            rows={2}
            maxLength={1000}
            value={answer}
            disabled={isPending}
            onChange={(event) => {
              setAnswer(event.target.value);
              setError(null);
            }}
          />
        )}
      </FormField>
      <Button size="sm" variant="outline" onClick={send} disabled={isPending}>
        {isPending ? <Loader2 className="size-4 animate-spin" /> : null}
        {t("questionsAnswer")}
      </Button>
    </div>
  );
}
