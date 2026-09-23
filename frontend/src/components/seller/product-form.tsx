"use client";

import { Loader2 } from "lucide-react";
import { useTranslations } from "next-intl";
import { useRouter } from "next/navigation";
import { useState, useTransition, type FormEvent } from "react";
import { toast } from "sonner";

import { FormField } from "@/components/common/form-field";
import { ImageUploader } from "@/components/seller/image-uploader";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { Textarea } from "@/components/ui/textarea";
import { PRODUCT_CONDITIONS } from "@/constants/auction";
import { useCategoryLabels, useEnumLabels } from "@/hooks/use-labels";
import { createProduct, updateProduct } from "@/services/seller-service";
import type { Category, Product, ProductCondition } from "@/types";

interface ProductFormProps {
  categories: Category[];
  /** Present when editing; absent when creating. */
  product?: Product;
}

interface FieldErrors {
  name?: string;
  description?: string;
  categoryId?: string;
  images?: string;
}

export function ProductForm({ categories, product }: ProductFormProps) {
  const t = useTranslations("seller");
  const tc = useTranslations("common");
  const labels = useEnumLabels();
  const categoryLabels = useCategoryLabels();
  const router = useRouter();

  const [name, setName] = useState(product?.name ?? "");
  const [description, setDescription] = useState(product?.description ?? "");
  const [categoryId, setCategoryId] = useState(product?.categoryId ?? "");
  const [condition, setCondition] = useState<ProductCondition>(
    product?.condition ?? "LIKE_NEW",
  );
  const [images, setImages] = useState<string[]>(
    product?.images.map((image) => image.url) ?? [],
  );

  const [errors, setErrors] = useState<FieldErrors>({});
  const [isPending, startTransition] = useTransition();

  function validate(): FieldErrors {
    const next: FieldErrors = {};
    if (!name.trim()) next.name = t("errors.nameRequired");
    if (!description.trim()) next.description = t("errors.descriptionRequired");
    if (!categoryId) next.categoryId = t("errors.categoryRequired");
    if (images.length === 0) next.images = t("errors.imagesRequired");
    return next;
  }

  function handleSubmit(event: FormEvent) {
    event.preventDefault();

    const nextErrors = validate();
    setErrors(nextErrors);
    if (Object.keys(nextErrors).length > 0) return;

    startTransition(async () => {
      const input = {
        name,
        description,
        categoryId,
        condition,
        imageUrls: images,
      };

      const saved = product
        ? await updateProduct(product.id, input)
        : await createProduct(input);

      toast.success(t("productSaved"), {
        description: t("productSavedBody", { name: saved?.name ?? name }),
      });
      router.push("/seller/products");
    });
  }

  return (
    <form onSubmit={handleSubmit} noValidate className="mt-8 max-w-2xl space-y-6">
      <FormField label={t("productName")} error={errors.name} required>
        {(field) => (
          <Input
            {...field}
            value={name}
            onChange={(event) => {
              setName(event.target.value);
              setErrors((current) => ({ ...current, name: undefined }));
            }}
            placeholder={t("productNamePlaceholder")}
            className="h-11"
          />
        )}
      </FormField>

      <div className="grid gap-5 sm:grid-cols-2">
        <div className="space-y-1.5">
          <Label htmlFor="product-category" className="label-sm text-dim">
            {tc("category")}
            <span className="text-danger-text" aria-hidden="true">
              *
            </span>
          </Label>
          <Select
            value={categoryId}
            onValueChange={(value) => {
              setCategoryId(value);
              setErrors((current) => ({ ...current, categoryId: undefined }));
            }}
          >
            <SelectTrigger
              id="product-category"
              className="h-11 w-full"
              aria-invalid={Boolean(errors.categoryId)}
            >
              <SelectValue placeholder={tc("category")} />
            </SelectTrigger>
            <SelectContent>
              {categories.map((category) => (
                <SelectItem key={category.id} value={category.id}>
                  {categoryLabels.name(category)}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
          {errors.categoryId ? (
            <p role="alert" className="text-danger-text text-xs">
              {errors.categoryId}
            </p>
          ) : null}
        </div>

        <div className="space-y-1.5">
          <Label htmlFor="product-condition" className="label-sm text-dim">
            {tc("condition")}
          </Label>
          <Select
            value={condition}
            onValueChange={(value) => setCondition(value as ProductCondition)}
          >
            <SelectTrigger id="product-condition" className="h-11 w-full">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              {PRODUCT_CONDITIONS.map((option) => (
                <SelectItem key={option} value={option}>
                  {labels.condition(option)}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </div>
      </div>

      <FormField
        label={t("productDescription")}
        error={errors.description}
        required
      >
        {(field) => (
          <Textarea
            {...field}
            value={description}
            onChange={(event) => {
              setDescription(event.target.value);
              setErrors((current) => ({ ...current, description: undefined }));
            }}
            placeholder={t("productDescriptionPlaceholder")}
            rows={6}
          />
        )}
      </FormField>

      <div className="space-y-1.5">
        <Label htmlFor="product-images" className="label-sm text-dim">
          {t("productImages")}
          <span className="text-danger-text" aria-hidden="true">
            *
          </span>
        </Label>
        <ImageUploader
          id="product-images"
          value={images}
          error={errors.images}
          onChange={(next) => {
            setImages(next);
            setErrors((current) => ({ ...current, images: undefined }));
          }}
        />
        {errors.images ? (
          <p role="alert" className="text-danger-text text-xs">
            {errors.images}
          </p>
        ) : null}
      </div>

      <div className="border-line flex gap-3 border-t pt-8">
        <Button type="submit" disabled={isPending}>
          {isPending ? <Loader2 className="size-4 animate-spin" /> : null}
          {tc("save")}
        </Button>
        <Button
          type="button"
          variant="outline"
          disabled={isPending}
          onClick={() => router.push("/seller/products")}
        >
          {tc("cancel")}
        </Button>
      </div>
    </form>
  );
}
