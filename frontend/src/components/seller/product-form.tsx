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
import { useApiErrorMessage, useCategoryLabels, useEnumLabels } from "@/hooks/use-labels";
import { createProduct, updateProduct } from "@/services/seller-service";
import type { BundleItem, Category, Parcel, Product, ProductCondition } from "@/types";
import { ProductMediaSettings } from "@/components/seller/product-media-settings";

type ParcelKey = keyof Parcel;
/** EN: GHN's limits: 30 kg, 150 cm a side. / VI: Giới hạn của GHN: 30 kg, mỗi cạnh 150 cm. */
const PARCEL_FIELDS: [ParcelKey, number][] = [
  ["weightGrams", 30000],
  ["lengthCm", 150],
  ["widthCm", 150],
  ["heightCm", 150],
];
const DEFAULT_PARCEL: Parcel = { weightGrams: 1000, lengthCm: 20, widthCm: 15, heightCm: 10 };

/**
 * EN: The packed size from the four fields: null when all are empty, undefined when only some are filled or one is out
 *     of range.
 * VI: Kích thước kiện từ bốn ô: null khi bỏ trống cả bốn, undefined khi chỉ điền một phần hoặc có ô ngoài giới hạn.
 */
function parcelOf(fields: Record<ParcelKey, string>): Parcel | null | undefined {
  const filled = PARCEL_FIELDS.filter(([key]) => fields[key] !== "");
  if (filled.length === 0) return null;
  if (filled.length < PARCEL_FIELDS.length) return undefined;
  const values = PARCEL_FIELDS.map(([key, max]) => {
    const value = Number(fields[key]);
    return value >= 1 && value <= max ? value : NaN;
  });
  if (values.some(Number.isNaN)) return undefined;
  const [weightGrams, lengthCm, widthCm, heightCm] = values as [number, number, number, number];
  return { weightGrams, lengthCm, widthCm, heightCm };
}

interface ProductFormProps {
  categories: Category[];
  /** Present when editing; absent when creating. */
  product?: Product;
}

interface FieldErrors {
  parcel?: string;
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

  const [parcel, setParcel] = useState<Record<ParcelKey, string>>(() => ({
    weightGrams: product?.parcel ? String(product.parcel.weightGrams) : "",
    lengthCm: product?.parcel ? String(product.parcel.lengthCm) : "",
    widthCm: product?.parcel ? String(product.parcel.widthCm) : "",
    heightCm: product?.parcel ? String(product.parcel.heightCm) : "",
  }));

  const [items, setItems] = useState<{ name: string; quantity: string }[]>(() =>
    (product?.items ?? []).map((item) => ({ name: item.name, quantity: String(item.quantity) })),
  );
  const [errors, setErrors] = useState<FieldErrors>({});
  const [isPending, startTransition] = useTransition();
  const errorMessage = useApiErrorMessage();

  function validate(): FieldErrors {
    const next: FieldErrors = {};
    if (!name.trim()) next.name = t("errors.nameRequired");
    if (!description.trim()) next.description = t("errors.descriptionRequired");
    if (!categoryId) next.categoryId = t("errors.categoryRequired");
    if (images.length === 0) next.images = t("errors.imagesRequired");
    if (parcelOf(parcel) === undefined) next.parcel = t("errors.parcelInvalid");
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
        parcel: parcelOf(parcel) ?? null,
        items: items
          .filter((item) => item.name.trim())
          .map((item): BundleItem => ({ name: item.name.trim(), quantity: Math.max(1, Number(item.quantity) || 1) })),
      };

      let saved;
      try {
        saved = product
          ? await updateProduct(product.id, input)
          : await createProduct(input);
      } catch (error) {
        toast.error(errorMessage(error));
        return;
      }

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

      <fieldset className="border-line border-t pt-6">
        <legend className="label-sm text-dim pr-3">{t("parcelTitle")}</legend>
        <p className="text-muted-foreground mt-1 text-xs leading-relaxed">{t("parcelHint")}</p>
        <div className="mt-4 grid grid-cols-2 gap-4 sm:grid-cols-4">
          {PARCEL_FIELDS.map(([key, max]) => (
            <FormField key={key} label={t(`parcel.${key}`)}>
              {(field) => (
                <Input
                  {...field}
                  inputMode="numeric"
                  value={parcel[key]}
                  aria-invalid={errors.parcel ? true : undefined}
                  placeholder={String(DEFAULT_PARCEL[key])}
                  onChange={(event) => {
                    const digits = event.target.value.replace(/\D/g, "").slice(0, String(max).length);
                    setParcel((current) => ({ ...current, [key]: digits }));
                    setErrors((current) => ({ ...current, parcel: undefined }));
                  }}
                />
              )}
            </FormField>
          ))}
        </div>
        {errors.parcel ? (
          <p role="alert" className="text-danger-text mt-2 text-xs">
            {errors.parcel}
          </p>
        ) : null}
      </fieldset>

      {product ? (
        <ProductMediaSettings
          productId={product.id}
          videoUrl={product.videoUrl ?? null}
          spinView={product.spinView ?? false}
        />
      ) : (
        <p className="border-line text-dim border-t pt-6 text-xs">{t("mediaAfterSave")}</p>
      )}

      <fieldset className="border-line border-t pt-6" data-testid="bundle-items">
        <legend className="label-sm text-dim pr-3">{t("itemsTitle")}</legend>
        <p className="text-muted-foreground mt-1 text-xs leading-relaxed">{t("itemsHint")}</p>
        <ul className="mt-4 space-y-2">
          {items.map((item, index) => (
            <li key={index} className="flex items-center gap-2">
              <Input
                aria-label={t("itemName")}
                placeholder={t("itemName")}
                value={item.name}
                maxLength={120}
                onChange={(event) =>
                  setItems((current) => current.map((row, i) => (i === index ? { ...row, name: event.target.value } : row)))
                }
              />
              <Input
                aria-label={t("itemQuantity")}
                inputMode="numeric"
                className="w-20"
                value={item.quantity}
                onChange={(event) => {
                  const digits = event.target.value.replace(/\D/g, "").slice(0, 3);
                  setItems((current) => current.map((row, i) => (i === index ? { ...row, quantity: digits } : row)));
                }}
              />
              <Button
                type="button"
                variant="ghost"
                size="sm"
                aria-label={t("itemRemove")}
                onClick={() => setItems((current) => current.filter((_, i) => i !== index))}
              >
                ×
              </Button>
            </li>
          ))}
        </ul>
        {items.length < 30 ? (
          <Button
            type="button"
            variant="outline"
            size="sm"
            className="mt-3"
            onClick={() => setItems((current) => [...current, { name: "", quantity: "1" }])}
          >
            {t("itemAdd")}
          </Button>
        ) : null}
      </fieldset>

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
