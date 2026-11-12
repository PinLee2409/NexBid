"use client";

import { Archive, ArchiveRestore, Loader2, Pencil, Plus, Tags } from "lucide-react";
import { useTranslations } from "next-intl";
import { useState, useTransition } from "react";
import { toast } from "sonner";

import { ApiErrorState } from "@/components/common/api-error-state";
import { EmptyState } from "@/components/common/empty-state";
import { PageHeader } from "@/components/layout/page-header";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Skeleton } from "@/components/ui/skeleton";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { Textarea } from "@/components/ui/textarea";
import { useAsyncData } from "@/hooks/use-async-data";
import { useApiErrorMessage } from "@/hooks/use-labels";
import {
  createCategory,
  listAllCategories,
  setCategoryStatus,
  updateCategory,
  type CategoryInput,
} from "@/services/admin-service";
import type { Category, CategoryStatus } from "@/types";

/** EN: The same limits `SaveCategoryRequest` enforces. / VI: Cùng giới hạn mà `SaveCategoryRequest` áp dụng. */
const LIMITS = { name: 80, slug: 80, description: 500, imageUrl: 500 } as const;

/** EN: `undefined` = dialog closed, `null` = creating, a category = editing it. / VI: `undefined` = đóng, `null` = tạo mới, một danh mục = đang sửa. */
type Editing = Category | null | undefined;

/**
 * EN: Categories buyers browse by. Archived ones keep their products but leave the browse list.
 * VI: Danh mục để người mua duyệt. Mục đã lưu trữ vẫn giữ sản phẩm nhưng rời khỏi danh sách duyệt.
 */
export default function AdminCategoriesPage() {
  const t = useTranslations("admin");

  const [tab, setTab] = useState<CategoryStatus>("ACTIVE");
  const [editing, setEditing] = useState<Editing>(undefined);
  const { state, refresh } = useAsyncData("admin-categories", listAllCategories);

  const shown =
    state.status === "success"
      ? state.data.filter((category) => (category.status ?? "ACTIVE") === tab)
      : [];

  return (
    <>
      <PageHeader
        title={t("categoriesTitle")}
        description={t("categoriesSubtitle")}
        actions={
          <Button onClick={() => setEditing(null)}>
            <Plus className="size-3.5" />
            {t("categoriesNew")}
          </Button>
        }
      />

      <Tabs value={tab} onValueChange={(value) => setTab(value as CategoryStatus)} className="mt-6 block">
        <TabsList>
          <TabsTrigger value="ACTIVE">{t("categoriesTabActive")}</TabsTrigger>
          <TabsTrigger value="ARCHIVED">{t("categoriesTabArchived")}</TabsTrigger>
        </TabsList>

        <TabsContent value={tab} className="mt-10">
          {state.status === "loading" ? (
            <div>
              {Array.from({ length: 4 }).map((_, index) => (
                <Skeleton key={index} className="border-line h-20 w-full rounded-none border-b" />
              ))}
            </div>
          ) : state.status === "error" ? (
            <ApiErrorState icon={Tags} error={state.error} />
          ) : shown.length === 0 ? (
            <EmptyState
              icon={Tags}
              title={t("categoriesEmptyTitle")}
              description={tab === "ACTIVE" ? t("categoriesEmptyBody") : t("categoriesArchivedEmptyBody")}
            />
          ) : (
            <ul>
              {shown.map((category) => (
                <CategoryRow
                  key={category.id}
                  category={category}
                  onEdit={() => setEditing(category)}
                  onChanged={refresh}
                />
              ))}
            </ul>
          )}
        </TabsContent>
      </Tabs>

      <CategoryDialog editing={editing} onClose={() => setEditing(undefined)} onSaved={refresh} />
    </>
  );
}

function CategoryRow({
  category,
  onEdit,
  onChanged,
}: {
  category: Category;
  onEdit: () => void;
  onChanged: () => void;
}) {
  const t = useTranslations("admin");
  const tc = useTranslations("common");
  const errorMessage = useApiErrorMessage();
  const [isPending, startTransition] = useTransition();

  const archived = category.status === "ARCHIVED";

  function toggleArchived() {
    startTransition(async () => {
      try {
        await setCategoryStatus(category.id, archived ? "ACTIVE" : "ARCHIVED");
      } catch (error) {
        toast.error(errorMessage(error));
        return;
      }
      onChanged();
      toast.success(archived ? t("categoryRestored") : t("categoryArchived"), {
        description: archived
          ? t("categoryRestoredBody", { name: category.name })
          : t("categoryArchivedBody", { name: category.name }),
      });
    });
  }

  return (
    <li className="border-line border-t last:border-b">
      <div className="flex flex-wrap items-start gap-x-6 gap-y-4 py-6">
        <div className="min-w-[14rem] flex-1">
          <h3 className="display text-xl">{category.name}</h3>
          <p className="mono-figure text-dim mt-1 text-xs break-all">/{category.slug}</p>
          {category.description ? (
            <p className="text-muted-foreground mt-3 max-w-2xl text-sm leading-relaxed">{category.description}</p>
          ) : null}
          {category.imageUrl ? (
            <p className="text-dim mt-2 max-w-2xl truncate text-xs" title={category.imageUrl}>
              {category.imageUrl}
            </p>
          ) : null}
        </div>

        <div className="flex shrink-0 items-center gap-2">
          <Button variant="outline" onClick={onEdit} disabled={isPending} aria-label={`${tc("edit")} ${category.name}`}>
            <Pencil className="size-3.5" />
            {tc("edit")}
          </Button>
          <Button
            variant={archived ? "default" : "destructive"}
            onClick={toggleArchived}
            disabled={isPending}
            aria-label={`${archived ? t("categoryRestore") : t("categoryArchive")} ${category.name}`}
          >
            {isPending ? (
              <Loader2 className="size-3.5 animate-spin" />
            ) : archived ? (
              <ArchiveRestore className="size-3.5" />
            ) : (
              <Archive className="size-3.5" />
            )}
            {archived ? t("categoryRestore") : t("categoryArchive")}
          </Button>
        </div>
      </div>
    </li>
  );
}

const EMPTY: CategoryInput = { name: "", slug: "", description: "", imageUrl: "" };

function CategoryDialog({
  editing,
  onClose,
  onSaved,
}: {
  editing: Editing;
  onClose: () => void;
  onSaved: () => void;
}) {
  const open = editing !== undefined;
  // EN: Keyed so each opening starts from the category's current values. / VI: Đặt key để mỗi lần mở bắt đầu từ giá trị hiện tại.
  return (
    <Dialog open={open} onOpenChange={(next) => !next && onClose()}>
      <DialogContent className="sm:max-w-lg">
        {open ? (
          <CategoryForm key={editing?.id ?? "new"} category={editing} onClose={onClose} onSaved={onSaved} />
        ) : null}
      </DialogContent>
    </Dialog>
  );
}

function CategoryForm({
  category,
  onClose,
  onSaved,
}: {
  category: Category | null;
  onClose: () => void;
  onSaved: () => void;
}) {
  const t = useTranslations("admin");
  const tc = useTranslations("common");
  const errorMessage = useApiErrorMessage();

  const [values, setValues] = useState<CategoryInput>(
    category
      ? { name: category.name, slug: category.slug, description: category.description, imageUrl: category.imageUrl }
      : EMPTY,
  );
  const [error, setError] = useState<string | null>(null);
  const [isPending, startTransition] = useTransition();

  const labels: Record<keyof CategoryInput, string> = {
    name: t("categoryName"),
    slug: t("categorySlug"),
    description: t("categoryDescription"),
    imageUrl: t("categoryImageUrl"),
  };

  function validate(): string | null {
    if (!values.name.trim()) return t("categoryNameRequired");
    for (const field of Object.keys(LIMITS) as (keyof CategoryInput)[]) {
      if (values[field].trim().length > LIMITS[field]) {
        return t("categoryTooLong", { field: labels[field], max: LIMITS[field] });
      }
    }
    const url = values.imageUrl.trim();
    if (url && !/^https?:\/\/\S+$/i.test(url)) return t("categoryImageUrlInvalid");
    return null;
  }

  function handleSubmit(event: React.FormEvent) {
    event.preventDefault();
    const problem = validate();
    if (problem) {
      setError(problem);
      return;
    }

    startTransition(async () => {
      try {
        if (category) await updateCategory(category.id, values);
        else await createCategory(values);
      } catch (failure) {
        setError(errorMessage(failure));
        return;
      }
      onClose();
      onSaved();
      toast.success(category ? t("categorySaved") : t("categoryCreated"), { description: values.name.trim() });
    });
  }

  function field(name: keyof CategoryInput) {
    return {
      id: `category-${name}`,
      value: values[name],
      maxLength: LIMITS[name],
      disabled: isPending,
      onChange: (event: React.ChangeEvent<HTMLInputElement | HTMLTextAreaElement>) => {
        setValues((current) => ({ ...current, [name]: event.target.value }));
        setError(null);
      },
    };
  }

  return (
    <form onSubmit={handleSubmit} noValidate>
      <DialogHeader>
        <DialogTitle>{category ? t("categoryEditTitle") : t("categoryCreateTitle")}</DialogTitle>
        <DialogDescription>{t("categoryFormBody")}</DialogDescription>
      </DialogHeader>

      <div className="mt-6 space-y-5">
        <div className="space-y-2">
          <Label htmlFor="category-name" className="label-sm text-dim">
            {labels.name}
          </Label>
          <Input {...field("name")} required autoFocus />
        </div>
        <div className="space-y-2">
          <Label htmlFor="category-slug" className="label-sm text-dim">
            {labels.slug}
          </Label>
          <Input {...field("slug")} placeholder="vintage-watches" />
        </div>
        <div className="space-y-2">
          <Label htmlFor="category-description" className="label-sm text-dim">
            {labels.description}
          </Label>
          <Textarea {...field("description")} rows={3} />
        </div>
        <div className="space-y-2">
          <Label htmlFor="category-imageUrl" className="label-sm text-dim">
            {labels.imageUrl}
          </Label>
          <Input {...field("imageUrl")} type="url" placeholder="https://" />
        </div>

        {error ? (
          <p role="alert" className="label-sm text-danger-text">
            {error}
          </p>
        ) : null}
      </div>

      <DialogFooter className="mt-6">
        <Button type="button" variant="outline" onClick={onClose} disabled={isPending}>
          {tc("cancel")}
        </Button>
        <Button type="submit" disabled={isPending}>
          {isPending ? <Loader2 className="size-4 animate-spin" /> : null}
          {category ? tc("save") : tc("create")}
        </Button>
      </DialogFooter>
    </form>
  );
}
