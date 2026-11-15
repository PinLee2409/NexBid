"use client";

import { FileSpreadsheet, Loader2 } from "lucide-react";
import { useTranslations } from "next-intl";
import { useState, useTransition, type FormEvent } from "react";
import { toast } from "sonner";

import { Button } from "@/components/ui/button";
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { useApiErrorMessage } from "@/hooks/use-labels";
import { cn } from "@/lib/utils";
import { importLots, type ImportResult } from "@/services/seller-service";

/**
 * EN: Lists many lots from a CSV file and their photos. Each row becomes a draft lot to check and submit, or a line
 *     saying what is wrong with it; good rows go through even when others fail.
 * VI: Đăng nhiều lô từ một file CSV kèm ảnh. Mỗi dòng thành một lô nháp để kiểm tra rồi gửi duyệt, hoặc một dòng báo
 *     sai chỗ nào; dòng đúng vẫn được tạo dù dòng khác lỗi.
 */
export function ImportLotsDialog({ onImported }: { onImported: () => void }) {
  const t = useTranslations("seller");
  const errorMessage = useApiErrorMessage();
  const [open, setOpen] = useState(false);
  const [csv, setCsv] = useState<File | null>(null);
  const [photos, setPhotos] = useState<File[]>([]);
  const [result, setResult] = useState<ImportResult | null>(null);
  const [isPending, startTransition] = useTransition();

  function submit(event: FormEvent) {
    event.preventDefault();
    if (!csv) return;
    startTransition(async () => {
      try {
        const done = await importLots(csv, photos);
        setResult(done);
        if (done.created > 0) {
          toast.success(t("importDone", { count: done.created }));
          onImported();
        }
      } catch (error) {
        toast.error(errorMessage(error));
      }
    });
  }

  return (
    <>
      <Button variant="outline" onClick={() => setOpen(true)}>
        <FileSpreadsheet className="size-4" />
        {t("importLots")}
      </Button>
      <Dialog
        open={open}
        onOpenChange={(next) => {
          setOpen(next);
          if (!next) setResult(null);
        }}
      >
        <DialogContent className="max-h-[90vh] overflow-y-auto sm:max-w-xl">
          <DialogHeader>
            <DialogTitle>{t("importTitle")}</DialogTitle>
            <DialogDescription>
              {t("importBody")}{" "}
              <a href="/templates/nexbid-lots.csv" download className="text-signal-text underline-offset-4 hover:underline">
                {t("importTemplate")}
              </a>
            </DialogDescription>
          </DialogHeader>

          <form onSubmit={submit} noValidate className="space-y-5">
            <div className="space-y-2">
              <Label htmlFor="import-csv" className="label-sm text-dim">
                {t("importCsv")}
              </Label>
              <Input
                id="import-csv"
                type="file"
                accept=".csv,text/csv"
                onChange={(event) => setCsv(event.target.files?.[0] ?? null)}
              />
            </div>
            <div className="space-y-2">
              <Label htmlFor="import-photos" className="label-sm text-dim">
                {t("importPhotos")}
              </Label>
              <Input
                id="import-photos"
                type="file"
                accept="image/jpeg,image/png,image/webp"
                multiple
                onChange={(event) => setPhotos(Array.from(event.target.files ?? []))}
              />
              <p className="text-dim text-xs">{t("importPhotosHint")}</p>
            </div>
            <Button type="submit" disabled={isPending || !csv}>
              {isPending ? <Loader2 className="size-4 animate-spin" /> : <FileSpreadsheet className="size-4" />}
              {t("importAction")}
            </Button>
          </form>

          {result ? (
            <div className="border-line border-t pt-4">
              <p className="text-sm font-medium">{t("importSummary", { created: result.created, failed: result.failed })}</p>
              <ul className="mt-3 space-y-2 text-sm" data-testid="import-results">
                {result.rows.map((row) => (
                  <li key={row.line} className={cn(row.auctionId ? "text-success" : "text-danger-text")}>
                    <span className="mono-figure text-dim mr-2">{t("importLine", { line: row.line })}</span>
                    {row.name || "—"}
                    {row.errors.length > 0 ? (
                      <ul className="text-dim mt-1 list-disc pl-5 text-xs">
                        {row.errors.map((error) => (
                          <li key={error}>{error}</li>
                        ))}
                      </ul>
                    ) : null}
                  </li>
                ))}
              </ul>
            </div>
          ) : null}
        </DialogContent>
      </Dialog>
    </>
  );
}
