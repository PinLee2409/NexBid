"use client";

import { CalendarClock, Loader2 } from "lucide-react";
import { useTranslations } from "next-intl";
import { useState, useTransition, type FormEvent } from "react";
import { toast } from "sonner";

import { Button } from "@/components/ui/button";
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { useApiErrorMessage } from "@/hooks/use-labels";
import { scheduleDrafts } from "@/services/seller-service";
import type { AuctionSummary } from "@/types";

type When = "now" | "later" | "no";

/** EN: A Vietnam date and time typed in the form, as an instant. / VI: Ngày giờ Việt Nam gõ trong form, đổi ra thời điểm. */
function at(date: string, time: string): string | null {
  return date && time ? new Date(`${date}T${time}:00+07:00`).toISOString() : null;
}

/**
 * EN: Gives many drafts the same opening and closing time and sends them for approval now, at a set time, or not yet —
 *     handy after a CSV import. Times are Vietnam time; leaving them empty keeps each lot's own.
 * VI: Đặt cùng giờ mở và giờ đóng cho nhiều lô nháp và gửi duyệt ngay, hẹn giờ, hoặc chưa gửi — tiện sau khi nhập CSV.
 *     Giờ tính theo giờ Việt Nam; để trống thì giữ giờ riêng của từng lô.
 */
export function ScheduleDraftsDialog({ drafts, onDone }: { drafts: AuctionSummary[]; onDone: () => void }) {
  const t = useTranslations("seller");
  const errorMessage = useApiErrorMessage();
  const [open, setOpen] = useState(false);
  const [chosen, setChosen] = useState<Set<string>>(new Set());
  const [startDate, setStartDate] = useState("");
  const [startTime, setStartTime] = useState("20:00");
  const [endDate, setEndDate] = useState("");
  const [endTime, setEndTime] = useState("21:00");
  const [when, setWhen] = useState<When>("now");
  const [submitDate, setSubmitDate] = useState("");
  const [submitTime, setSubmitTime] = useState("08:00");
  const [isPending, startTransition] = useTransition();

  if (drafts.length === 0) return null;

  function show() {
    setChosen(new Set(drafts.map((draft) => draft.id)));
    setOpen(true);
  }

  function submit(event: FormEvent) {
    event.preventDefault();
    startTransition(async () => {
      try {
        const rows = await scheduleDrafts({
          auctionIds: [...chosen],
          startTime: at(startDate, startTime),
          endTime: at(endDate, endTime),
          submit: when !== "no",
          submitAt: when === "later" ? at(submitDate, submitTime) : null,
        });
        const failed = rows.filter((row) => !row.done);
        if (failed.length === 0) toast.success(t("batchDone", { count: rows.length }));
        else toast.error(t("batchSomeFailed", { failed: failed.length, total: rows.length }), { description: failed[0].error ?? "" });
        setOpen(false);
        onDone();
      } catch (error) {
        toast.error(errorMessage(error));
      }
    });
  }

  return (
    <>
      <Button variant="outline" onClick={show}>
        <CalendarClock className="size-4" />
        {t("batchOpen", { count: drafts.length })}
      </Button>
      <Dialog open={open} onOpenChange={setOpen}>
        <DialogContent className="max-h-[90vh] overflow-y-auto sm:max-w-xl">
          <DialogHeader>
            <DialogTitle>{t("batchTitle")}</DialogTitle>
            <DialogDescription>{t("batchBody")}</DialogDescription>
          </DialogHeader>
          <form onSubmit={submit} noValidate className="space-y-5">
            <ul className="border-line divide-line max-h-48 divide-y overflow-y-auto border-y text-sm">
              {drafts.map((draft) => (
                <li key={draft.id} className="flex items-center gap-3 py-2">
                  <input
                    id={`draft-${draft.id}`}
                    type="checkbox"
                    className="size-4"
                    checked={chosen.has(draft.id)}
                    onChange={(event) =>
                      setChosen((current) => {
                        const next = new Set(current);
                        if (event.target.checked) next.add(draft.id);
                        else next.delete(draft.id);
                        return next;
                      })
                    }
                  />
                  <label htmlFor={`draft-${draft.id}`} className="truncate">
                    {draft.product.name}
                  </label>
                </li>
              ))}
            </ul>

            <fieldset className="grid gap-3 sm:grid-cols-2">
              <legend className="label-sm text-dim mb-2">{t("batchTimes")}</legend>
              <div className="space-y-1">
                <Label htmlFor="batch-start-date" className="label-sm text-dim">{t("batchOpens")}</Label>
                <div className="flex gap-2">
                  <Input id="batch-start-date" type="date" value={startDate} onChange={(e) => setStartDate(e.target.value)} />
                  <Input aria-label={t("batchOpensTime")} type="time" value={startTime} onChange={(e) => setStartTime(e.target.value)} className="w-28" />
                </div>
              </div>
              <div className="space-y-1">
                <Label htmlFor="batch-end-date" className="label-sm text-dim">{t("batchCloses")}</Label>
                <div className="flex gap-2">
                  <Input id="batch-end-date" type="date" value={endDate} onChange={(e) => setEndDate(e.target.value)} />
                  <Input aria-label={t("batchClosesTime")} type="time" value={endTime} onChange={(e) => setEndTime(e.target.value)} className="w-28" />
                </div>
              </div>
              <p className="text-dim text-xs sm:col-span-2">{t("batchTimesHint")}</p>
            </fieldset>

            <fieldset className="space-y-2">
              <legend className="label-sm text-dim mb-2">{t("batchApproval")}</legend>
              {(["now", "later", "no"] as const).map((option) => (
                <label key={option} className="flex items-center gap-2 text-sm">
                  <input type="radio" name="batch-when" checked={when === option} onChange={() => setWhen(option)} />
                  {t(`batchWhen.${option}`)}
                </label>
              ))}
              {when === "later" ? (
                <div className="flex gap-2 pl-6">
                  <Input aria-label={t("batchSubmitDate")} type="date" value={submitDate} onChange={(e) => setSubmitDate(e.target.value)} />
                  <Input aria-label={t("batchSubmitTime")} type="time" value={submitTime} onChange={(e) => setSubmitTime(e.target.value)} className="w-28" />
                </div>
              ) : null}
            </fieldset>

            <div className="flex justify-end">
              <Button
                type="submit"
                disabled={
                  isPending ||
                  chosen.size === 0 ||
                  Boolean(startDate) !== Boolean(endDate) ||
                  (when === "later" && !submitDate)
                }
              >
                {isPending ? <Loader2 className="size-4 animate-spin" /> : <CalendarClock className="size-4" />}
                {t("batchApply", { count: chosen.size })}
              </Button>
            </div>
          </form>
        </DialogContent>
      </Dialog>
    </>
  );
}
