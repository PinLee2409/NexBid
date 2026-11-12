"use client";

import { Loader2 } from "lucide-react";
import { useTranslations } from "next-intl";
import { useState, useTransition, type FormEvent } from "react";

import { FormField } from "@/components/common/form-field";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { useApiErrorMessage } from "@/hooks/use-labels";
import { saveShippingAddress } from "@/services/account-service";
import type { ShippingAddress } from "@/types";

const EMPTY: ShippingAddress = {
  recipient: "",
  phone: "",
  line: "",
  ward: "",
  district: "",
  province: "",
};

/** EN: The same rules the server applies. / VI: Cùng quy tắc mà server áp dụng. */
const PHONE = /^\+?[0-9 .-]{8,20}$/;

type Errors = Partial<Record<keyof ShippingAddress, string>>;

/**
 * EN: Where the buyer's lots are delivered. Used on the profile, and before paying when no address exists yet.
 * VI: Nơi giao hàng cho người mua. Dùng ở trang hồ sơ, và trước khi thanh toán khi chưa có địa chỉ.
 */
export function AddressForm({
  initial,
  submitLabel,
  onSaved,
}: {
  initial: ShippingAddress | null;
  submitLabel: string;
  onSaved: (address: ShippingAddress) => void;
}) {
  const t = useTranslations("account");
  const tc = useTranslations("common");
  const errorMessage = useApiErrorMessage();

  const [values, setValues] = useState<ShippingAddress>(initial ?? EMPTY);
  const [errors, setErrors] = useState<Errors>({});
  const [formError, setFormError] = useState<string | null>(null);
  const [isPending, startTransition] = useTransition();

  function validate(): Errors {
    const next: Errors = {};
    for (const key of ["recipient", "phone", "line", "province"] as const) {
      if (!values[key].trim()) next[key] = tc("required");
    }
    if (values.phone.trim() && !PHONE.test(values.phone.trim()))
      next.phone = t("addressPhoneInvalid");
    return next;
  }

  function handleSubmit(event: FormEvent) {
    event.preventDefault();
    const found = validate();
    setErrors(found);
    setFormError(null);
    if (Object.keys(found).length > 0) return;

    startTransition(async () => {
      try {
        onSaved(await saveShippingAddress(values));
      } catch (error) {
        setFormError(errorMessage(error));
      }
    });
  }

  function field(key: keyof ShippingAddress, autoComplete: string) {
    return function AddressInput(props: {
      id: string;
      "aria-describedby"?: string;
      "aria-invalid"?: boolean;
    }) {
      return (
        <Input
          {...props}
          autoComplete={autoComplete}
          value={values[key]}
          disabled={isPending}
          onChange={(event) => {
            setValues((current) => ({ ...current, [key]: event.target.value }));
            setErrors((current) => ({ ...current, [key]: undefined }));
          }}
        />
      );
    };
  }

  return (
    <form onSubmit={handleSubmit} noValidate className="space-y-5">
      <div className="grid gap-5 sm:grid-cols-2">
        <FormField
          label={t("addressRecipient")}
          error={errors.recipient}
          required
        >
          {field("recipient", "name")}
        </FormField>
        <FormField label={t("addressPhone")} error={errors.phone} required>
          {field("phone", "tel")}
        </FormField>
      </div>
      <FormField
        label={t("addressLine")}
        hint={t("addressLineHint")}
        error={errors.line}
        required
      >
        {field("line", "address-line1")}
      </FormField>
      <div className="grid gap-5 sm:grid-cols-3">
        <FormField label={t("addressWard")} error={errors.ward}>
          {field("ward", "address-level4")}
        </FormField>
        <FormField label={t("addressDistrict")} error={errors.district}>
          {field("district", "address-level3")}
        </FormField>
        <FormField
          label={t("addressProvince")}
          error={errors.province}
          required
        >
          {field("province", "address-level1")}
        </FormField>
      </div>

      {formError ? (
        <p
          role="alert"
          className="bg-danger-dim text-danger-text px-3.5 py-3 text-sm"
        >
          {formError}
        </p>
      ) : null}

      <Button type="submit" disabled={isPending}>
        {isPending ? <Loader2 className="size-4 animate-spin" /> : null}
        {submitLabel}
      </Button>
    </form>
  );
}
