"use client";

import { FileStack, Loader2, Palmtree, Trash2 } from "lucide-react";
import { useFormatter, useTranslations } from "next-intl";
import { useState, useTransition } from "react";
import { toast } from "sonner";

import { FormField } from "@/components/common/form-field";
import { PageHeader } from "@/components/layout/page-header";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Textarea } from "@/components/ui/textarea";
import { useAsyncData } from "@/hooks/use-async-data";
import { useApiErrorMessage } from "@/hooks/use-labels";
import {
  deleteTemplate,
  getVacation,
  listTemplates,
  saveTemplate,
  setVacation,
  type ListingTemplate,
} from "@/services/market-service";

/**
 * EN: Shop settings: going on vacation, and the listing templates kept for new products.
 * VI: Cài đặt cửa hàng: đi nghỉ, và các mẫu mô tả lưu sẵn cho sản phẩm mới.
 */
export default function SellerSettingsPage() {
  const t = useTranslations("shop");
  return (
    <>
      <PageHeader title={t("title")} description={t("subtitle")} />
      <VacationSection />
      <TemplatesSection />
    </>
  );
}

/** EN: yyyy-MM-dd in the reader's zone, a week from now. / VI: yyyy-MM-dd theo múi giờ người xem, một tuần sau. */
function aWeekFromNow(): string {
  const date = new Date(Date.now() + 7 * 86_400_000);
  return new Date(date.getTime() - date.getTimezoneOffset() * 60_000)
    .toISOString()
    .slice(0, 10);
}

function VacationSection() {
  const t = useTranslations("shop");
  const format = useFormatter();
  const errorMessage = useApiErrorMessage();
  const { state, refresh } = useAsyncData("seller-vacation", getVacation);
  const [until, setUntil] = useState(aWeekFromNow);
  // EN: Null until the seller types, so the saved note shows first. / VI: Null cho tới khi người bán gõ, để lời nhắn đã lưu hiện trước.
  const [typed, setNote] = useState<string | null>(null);
  const [isPending, startTransition] = useTransition();
  const away =
    state.status === "success" && state.data.until !== null ? state.data : null;

  const note = typed ?? away?.note ?? "";

  function save(on: boolean) {
    startTransition(async () => {
      try {
        // EN: The end of the chosen day, in the seller's own zone. / VI: Cuối ngày đã chọn, theo múi giờ của người bán.
        await setVacation(
          on ? new Date(`${until}T23:59:00`).toISOString() : null,
          note,
        );
        toast.success(on ? t("vacationOn") : t("vacationOff"));
        refresh();
      } catch (error) {
        toast.error(errorMessage(error));
      }
    });
  }

  return (
    <section
      className="border-line mt-8 border p-5 sm:p-6"
      aria-labelledby="vacation-heading"
      data-testid="vacation"
    >
      <div className="flex items-center gap-2">
        <Palmtree className="text-signal-text size-5" aria-hidden="true" />
        <h2 id="vacation-heading" className="label">
          {t("vacationTitle")}
        </h2>
      </div>
      <p className="text-muted-foreground mt-2 max-w-2xl text-sm">
        {t("vacationBody")}
      </p>

      {away ? (
        <div className="bg-surface mt-4 p-4 text-sm">
          <p>
            {t("awayNow", {
              date: format.dateTime(new Date(away.until!), {
                dateStyle: "long",
              }),
            })}
          </p>
          <Button
            className="mt-3"
            size="sm"
            onClick={() => save(false)}
            disabled={isPending}
          >
            {t("endVacation")}
          </Button>
        </div>
      ) : null}

      <div className="mt-4 grid max-w-2xl gap-4 sm:grid-cols-[12rem_1fr]">
        <FormField label={t("until")}>
          {(field) => (
            <Input
              {...field}
              type="date"
              value={until}
              onChange={(event) => setUntil(event.target.value)}
              className="h-10"
            />
          )}
        </FormField>
        <FormField label={t("note")}>
          {(field) => (
            <Input
              {...field}
              value={note}
              maxLength={200}
              placeholder={t("notePlaceholder")}
              onChange={(event) => setNote(event.target.value)}
              className="h-10"
            />
          )}
        </FormField>
      </div>
      <Button
        className="mt-4"
        variant="outline"
        onClick={() => save(true)}
        disabled={isPending || !until}
      >
        {isPending ? <Loader2 className="size-4 animate-spin" /> : null}
        {away ? t("updateVacation") : t("startVacation")}
      </Button>
    </section>
  );
}

function TemplatesSection() {
  const t = useTranslations("templates");
  const errorMessage = useApiErrorMessage();
  const { state, refresh } = useAsyncData("listing-templates", listTemplates);
  const [editing, setEditing] = useState<ListingTemplate | null>(null);
  const [isPending, startTransition] = useTransition();

  function run(task: () => Promise<unknown>, done: string) {
    startTransition(async () => {
      try {
        await task();
        toast.success(done);
        setEditing(null);
        refresh();
      } catch (error) {
        toast.error(errorMessage(error));
      }
    });
  }

  return (
    <section
      id="templates"
      className="border-line mt-8 scroll-mt-24 border p-5 sm:p-6"
      aria-labelledby="templates-heading"
    >
      <div className="flex items-center gap-2">
        <FileStack className="text-signal-text size-5" aria-hidden="true" />
        <h2 id="templates-heading" className="label">
          {t("title")}
        </h2>
      </div>
      <p className="text-muted-foreground mt-2 max-w-2xl text-sm">
        {t("body")}
      </p>

      {state.status === "success" && state.data.length === 0 ? (
        <p className="text-dim mt-4 text-sm">{t("empty")}</p>
      ) : null}
      <ul className="mt-4" data-testid="templates">
        {(state.status === "success" ? state.data : []).map((template) =>
          editing?.id === template.id ? (
            <li
              key={template.id}
              className="border-line space-y-3 border-b py-4"
            >
              <Input
                aria-label={t("name")}
                value={editing.name}
                maxLength={80}
                onChange={(event) =>
                  setEditing({ ...editing, name: event.target.value })
                }
                className="h-10"
              />
              <Textarea
                aria-label={t("description")}
                value={editing.description}
                rows={5}
                onChange={(event) =>
                  setEditing({ ...editing, description: event.target.value })
                }
              />
              <div className="flex gap-2">
                <Button
                  size="sm"
                  disabled={
                    isPending ||
                    !editing.name.trim() ||
                    !editing.description.trim()
                  }
                  onClick={() =>
                    run(
                      () =>
                        saveTemplate(
                          {
                            name: editing.name.trim(),
                            description: editing.description,
                            categoryId: editing.categoryId,
                            condition: editing.condition,
                            parcel: editing.parcel,
                          },
                          editing.id,
                        ),
                      t("saved", { name: editing.name.trim() }),
                    )
                  }
                >
                  {t("save")}
                </Button>
                <Button
                  size="sm"
                  variant="ghost"
                  onClick={() => setEditing(null)}
                >
                  {t("cancel")}
                </Button>
              </div>
            </li>
          ) : (
            <li
              key={template.id}
              className="border-line flex items-start justify-between gap-4 border-b py-4"
            >
              <div className="min-w-0">
                <p className="font-medium">{template.name}</p>
                <p className="text-muted-foreground mt-1 line-clamp-2 text-sm">
                  {template.description}
                </p>
              </div>
              <div className="flex shrink-0 gap-1">
                <Button
                  size="sm"
                  variant="ghost"
                  onClick={() => setEditing(template)}
                >
                  {t("edit")}
                </Button>
                <Button
                  size="sm"
                  variant="ghost"
                  aria-label={t("delete", { name: template.name })}
                  onClick={() =>
                    run(() => deleteTemplate(template.id), t("deleted"))
                  }
                  disabled={isPending}
                >
                  <Trash2 className="size-4" />
                </Button>
              </div>
            </li>
          ),
        )}
      </ul>
      <p className="text-dim mt-4 text-xs">{t("howToAdd")}</p>
    </section>
  );
}
