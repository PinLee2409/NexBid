"use client";

import { CheckCircle2, Clock, Loader2, LogIn, Store, XCircle } from "lucide-react";
import { useFormatter, useTranslations } from "next-intl";
import { useEffect, useState, useTransition, type FormEvent } from "react";
import { toast } from "sonner";

import { ApiErrorState } from "@/components/common/api-error-state";
import { EmptyState } from "@/components/common/empty-state";
import { FormField } from "@/components/common/form-field";
import { PageHeader } from "@/components/layout/page-header";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { Textarea } from "@/components/ui/textarea";
import { useAsyncData } from "@/hooks/use-async-data";
import { useApiErrorMessage } from "@/hooks/use-labels";
import { applyToSell, getSellerApplication } from "@/services/account-service";
import { hasRole, reloadAccount, useSession } from "@/services/session-service";
import type { SellerApplication } from "@/types";

const NOTE_LIMIT = 1000;

/**
 * EN: Becoming a seller (spec §7.1): send a request, an admin decides, and the seller tools open on approval.
 * VI: Trở thành người bán (spec §7.1): gửi yêu cầu, admin quyết định, và công cụ người bán mở ra khi được duyệt.
 */
export default function BecomeSellerPage() {
  const t = useTranslations("becomeSeller");
  const { user, hydrated } = useSession();
  const isSeller = hasRole(user, "SELLER");

  return (
    <>
      <PageHeader title={t("title")} description={t("subtitle")} />
      {!hydrated ? (
        <PanelSkeleton />
      ) : !user ? (
        <EmptyState
          icon={LogIn}
          title={t("signInTitle")}
          description={t("signInBody")}
          action={{ label: t("signInTitle"), href: "/login" }}
          className="mt-10"
        />
      ) : isSeller ? (
        <Approved />
      ) : (
        <Request key={user.id} />
      )}
    </>
  );
}

function Request() {
  const { state, refresh } = useAsyncData("seller-application", getSellerApplication);

  if (state.status === "loading") return <PanelSkeleton />;
  if (state.status === "error") {
    return (
      <div className="mt-10">
        <ApiErrorState icon={Store} error={state.error} />
      </div>
    );
  }

  const application = state.data;
  if (application?.status === "APPROVED") return <CatchUp />;
  if (application?.status === "PENDING") return <Pending application={application} />;
  return (
    <>
      <Steps />
      {application?.status === "REJECTED" ? <Rejected application={application} /> : null}
      <ApplyForm onSent={refresh} />
    </>
  );
}

/** EN: The three steps, so the wait after sending is expected. / VI: Ba bước, để việc chờ sau khi gửi là điều đã biết trước. */
function Steps() {
  const t = useTranslations("becomeSeller");
  const steps = [t("stepSend"), t("stepReview"), t("stepSell")];

  return (
    <ol className="border-line mt-10 grid border-y sm:grid-cols-3">
      {steps.map((step, index) => (
        <li
          key={step}
          className="border-line flex items-baseline gap-3 border-b py-4 last:border-b-0 sm:border-b-0 sm:border-l sm:px-5 sm:first:border-l-0 sm:first:pl-0"
        >
          <span className="mono-figure text-signal-text text-xs">{String(index + 1).padStart(2, "0")}</span>
          <span className="text-sm">{step}</span>
        </li>
      ))}
    </ol>
  );
}

function ApplyForm({ onSent }: { onSent: () => void }) {
  const t = useTranslations("becomeSeller");
  const tc = useTranslations("common");
  const errorMessage = useApiErrorMessage();

  const [note, setNote] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [isPending, startTransition] = useTransition();

  function handleSubmit(event: FormEvent) {
    event.preventDefault();
    if (!note.trim()) {
      setError(tc("required"));
      return;
    }

    startTransition(async () => {
      try {
        await applyToSell(note);
      } catch (failure) {
        setError(errorMessage(failure));
        return;
      }
      toast.success(t("sent"), { description: t("sentBody") });
      onSent();
    });
  }

  return (
    <form onSubmit={handleSubmit} noValidate className="mt-10 max-w-xl space-y-6">
      <FormField label={t("noteLabel")} hint={t("noteHint")} error={error} required>
        {(field) => (
          <Textarea
            {...field}
            rows={5}
            maxLength={NOTE_LIMIT}
            value={note}
            placeholder={t("notePlaceholder")}
            onChange={(event) => {
              setNote(event.target.value);
              setError(null);
            }}
          />
        )}
      </FormField>

      <Button type="submit" disabled={isPending}>
        {isPending ? <Loader2 className="size-4 animate-spin" /> : null}
        {t("submit")}
      </Button>
    </form>
  );
}

function Pending({ application }: { application: SellerApplication }) {
  const t = useTranslations("becomeSeller");
  const format = useFormatter();

  return (
    <section className="mt-10">
      <EmptyState
        icon={Clock}
        title={t("pendingTitle")}
        description={t("pendingBody", {
          date: format.dateTime(new Date(application.createdAt), { dateStyle: "medium", timeStyle: "short" }),
        })}
      />
      <Note label={t("yourNote")} text={application.note} />
    </section>
  );
}

function Rejected({ application }: { application: SellerApplication }) {
  const t = useTranslations("becomeSeller");
  const format = useFormatter();
  const decided = application.decidedAt ?? application.createdAt;

  return (
    <section className="bg-danger-dim mt-8 px-5 py-4" aria-labelledby="rejected-heading">
      <h2 id="rejected-heading" className="text-danger-text flex items-center gap-2 text-sm font-medium">
        <XCircle className="size-4 shrink-0" aria-hidden="true" />
        {t("rejectedTitle")}
      </h2>
      <p className="text-muted-foreground mt-1 text-sm">
        {t("rejectedBody", { date: format.dateTime(new Date(decided), { dateStyle: "medium" }) })}
      </p>
      {application.rejectionReason ? (
        <p className="mt-3 text-sm">
          <span className="label-sm text-dim">{t("reasonLabel")}:</span> {application.rejectionReason}
        </p>
      ) : null}
    </section>
  );
}

function Note({ label, text }: { label: string; text: string }) {
  return (
    <div className="mt-6 max-w-xl">
      <p className="label-sm text-dim">{label}</p>
      <p className="mt-2 text-sm leading-relaxed whitespace-pre-line">{text}</p>
    </div>
  );
}

function Approved() {
  const t = useTranslations("becomeSeller");
  return (
    <EmptyState
      icon={CheckCircle2}
      title={t("approvedTitle")}
      description={t("approvedBody")}
      action={{ label: t("listItem"), href: "/seller/products/create" }}
      secondaryAction={{ label: t("openWorkspace"), href: "/seller/dashboard" }}
      className="mt-10"
    />
  );
}

/**
 * EN: Approved while this tab was asleep: renew the session so the new role counts, then the page follows.
 * VI: Được duyệt khi tab đang ngủ: gia hạn phiên để vai trò mới có hiệu lực, rồi trang tự cập nhật theo.
 */
function CatchUp() {
  useEffect(() => {
    void reloadAccount().catch(() => undefined);
  }, []);
  return <Approved />;
}

function PanelSkeleton() {
  return (
    <div className="mt-10 space-y-4">
      <Skeleton className="h-6 w-2/3 rounded-none" />
      <Skeleton className="h-28 w-full max-w-xl rounded-none" />
    </div>
  );
}
