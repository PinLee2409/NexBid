"use client";

import { BookmarkPlus, FileStack } from "lucide-react";
import { useTranslations } from "next-intl";
import Link from "next/link";
import { useState, useTransition } from "react";
import { toast } from "sonner";

import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { useAsyncData } from "@/hooks/use-async-data";
import { useApiErrorMessage } from "@/hooks/use-labels";
import {
  listTemplates,
  saveTemplate,
  type ListingTemplate,
  type TemplateInput,
} from "@/services/market-service";

interface TemplateBarProps {
  /** EN: What the form holds now, to save as a template. / VI: Những gì form đang có, để lưu thành mẫu. */
  current: TemplateInput;
  onApply: (template: ListingTemplate) => void;
}

/**
 * EN: Above a new product's form: fill it from a saved template, or keep what is typed as a new one.
 * VI: Phía trên form sản phẩm mới: điền từ một mẫu đã lưu, hoặc giữ những gì đã gõ thành mẫu mới.
 */
export function TemplateBar({ current, onApply }: TemplateBarProps) {
  const t = useTranslations("templates");
  const errorMessage = useApiErrorMessage();
  const { state, refresh } = useAsyncData("listing-templates", listTemplates);
  const [naming, setNaming] = useState(false);
  const [name, setName] = useState("");
  const [isPending, startTransition] = useTransition();
  const templates = state.status === "success" ? state.data : [];

  function save() {
    startTransition(async () => {
      try {
        await saveTemplate({ ...current, name: name.trim() });
        toast.success(t("saved", { name: name.trim() }));
        setNaming(false);
        setName("");
        refresh();
      } catch (error) {
        toast.error(errorMessage(error));
      }
    });
  }

  return (
    <div
      className="border-line bg-surface mt-8 max-w-2xl border p-4"
      data-testid="template-bar"
    >
      <div className="flex flex-wrap items-center gap-3">
        <FileStack className="text-signal-text size-4" aria-hidden="true" />
        {templates.length > 0 ? (
          <Select
            onValueChange={(id) => {
              const template = templates.find((each) => each.id === id);
              if (template) {
                onApply(template);
                toast.success(t("applied", { name: template.name }));
              }
            }}
          >
            <SelectTrigger className="h-9 w-56" aria-label={t("use")}>
              <SelectValue placeholder={t("use")} />
            </SelectTrigger>
            <SelectContent>
              {templates.map((template) => (
                <SelectItem key={template.id} value={template.id}>
                  {template.name}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        ) : (
          <span className="text-muted-foreground text-sm">{t("none")}</span>
        )}
        <Button
          type="button"
          size="sm"
          variant="ghost"
          onClick={() => setNaming((value) => !value)}
          disabled={!current.description.trim()}
        >
          <BookmarkPlus className="size-4" />
          {t("saveCurrent")}
        </Button>
        <Link
          href="/seller/settings#templates"
          className="label-sm text-dim hover:text-foreground ml-auto"
        >
          {t("manage")}
        </Link>
      </div>
      {naming ? (
        <div className="mt-3 flex flex-wrap gap-2">
          <Input
            value={name}
            onChange={(event) => setName(event.target.value)}
            placeholder={t("namePlaceholder")}
            aria-label={t("name")}
            maxLength={80}
            className="h-9 w-64"
            autoFocus
          />
          <Button
            type="button"
            size="sm"
            onClick={save}
            disabled={isPending || !name.trim()}
          >
            {t("save")}
          </Button>
        </div>
      ) : null}
    </div>
  );
}
