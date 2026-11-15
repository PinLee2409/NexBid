"use client";

import { Loader2 } from "lucide-react";
import { useTranslations } from "next-intl";
import { useEffect, useState, useTransition, type FormEvent } from "react";

import { FormField } from "@/components/common/form-field";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { useApiErrorMessage } from "@/hooks/use-labels";
import { saveShippingAddress } from "@/services/account-service";
import { listDistricts, listProvinces, listWards, useGhnEnabled, type Area } from "@/services/shipping-service";
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

type TextKey = "recipient" | "phone" | "line" | "ward" | "district" | "province";
type Errors = Partial<Record<TextKey, string>>;

/**
 * EN: Where the buyer's lots are delivered, and where a seller ships from. Used on the profile, and before paying when no
 *     address exists yet. With GHN connected, province, district and ward come from GHN's lists, so their ids are saved
 *     too and lot pages can quote shipping; if the lists cannot load, plain text fields stay.
 * VI: Nơi giao hàng cho người mua, và nơi người bán gửi hàng đi. Dùng ở trang hồ sơ, và trước khi thanh toán khi chưa có
 *     địa chỉ. Khi đã kết nối GHN, tỉnh, quận và phường lấy từ danh sách của GHN nên lưu được cả mã, để trang lô báo được
 *     phí ship; nếu không tải được danh sách thì vẫn là ô nhập chữ.
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
  const ghnOn = useGhnEnabled();

  const [values, setValues] = useState<ShippingAddress>(initial ?? EMPTY);
  const [errors, setErrors] = useState<Errors>({});
  const [formError, setFormError] = useState<string | null>(null);
  const [isPending, startTransition] = useTransition();

  const [provinces, setProvinces] = useState<Area[] | null>(null);
  const [districts, setDistricts] = useState<Area[] | null>(null);
  const [wards, setWards] = useState<Area[] | null>(null);
  const [listsFailed, setListsFailed] = useState(false);
  const picking = ghnOn && !listsFailed;

  // EN: Each list loads when the one above it is chosen. / VI: Mỗi danh sách tải khi danh sách phía trên đã chọn.
  useEffect(() => {
    if (!ghnOn) return;
    let live = true;
    listProvinces().then(
      (found) => live && setProvinces(found),
      () => live && setListsFailed(true),
    );
    return () => {
      live = false;
    };
  }, [ghnOn]);

  useEffect(() => {
    if (!ghnOn || !values.provinceId) return;
    let live = true;
    listDistricts(values.provinceId).then(
      (found) => live && setDistricts(found),
      () => live && setListsFailed(true),
    );
    return () => {
      live = false;
    };
  }, [ghnOn, values.provinceId]);

  useEffect(() => {
    if (!ghnOn || !values.districtId) return;
    let live = true;
    listWards(values.districtId).then(
      (found) => live && setWards(found),
      () => live && setListsFailed(true),
    );
    return () => {
      live = false;
    };
  }, [ghnOn, values.districtId]);

  function validate(): Errors {
    const next: Errors = {};
    for (const key of ["recipient", "phone", "line", "province"] as const) {
      if (!values[key].trim()) next[key] = tc("required");
    }
    if (picking) {
      if (!values.provinceId) next.province = tc("required");
      if (!values.districtId) next.district = tc("required");
      if (!values.wardCode) next.ward = tc("required");
    }
    if (values.phone.trim() && !PHONE.test(values.phone.trim())) next.phone = t("addressPhoneInvalid");
    return next;
  }

  function handleSubmit(event: FormEvent) {
    event.preventDefault();
    const found = validate();
    setErrors(found);
    setFormError(null);
    if (Object.keys(found).length > 0) return;

    // EN: Ids only travel with names picked from GHN. / VI: Mã chỉ gửi kèm khi tên được chọn từ GHN.
    const address: ShippingAddress = picking
      ? values
      : { ...values, provinceId: null, districtId: null, wardCode: null };
    startTransition(async () => {
      try {
        onSaved(await saveShippingAddress(address));
      } catch (error) {
        setFormError(errorMessage(error));
      }
    });
  }

  function field(key: TextKey, autoComplete: string) {
    return function AddressInput(props: { id: string; "aria-describedby"?: string; "aria-invalid"?: boolean }) {
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

  function picker(
    key: "province" | "district" | "ward",
    areas: Area[] | null,
    selected: string | undefined,
    choose: (area: Area) => void,
  ) {
    return function AreaPicker(props: { id: string; "aria-describedby"?: string; "aria-invalid"?: boolean }) {
      return (
        <Select
          value={selected ?? ""}
          disabled={isPending || areas === null}
          onValueChange={(id) => {
            const area = areas?.find((candidate) => candidate.id === id);
            if (!area) return;
            choose(area);
            setErrors((current) => ({ ...current, [key]: undefined }));
          }}
        >
          <SelectTrigger
            id={props.id}
            aria-describedby={props["aria-describedby"]}
            aria-invalid={props["aria-invalid"]}
            className="w-full"
          >
            <SelectValue placeholder={areas === null ? "…" : t("addressPick")} />
          </SelectTrigger>
          <SelectContent className="max-h-72">
            {(areas ?? []).map((area) => (
              <SelectItem key={area.id} value={area.id}>
                {area.name}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
      );
    };
  }

  return (
    <form onSubmit={handleSubmit} noValidate className="space-y-5">
      <div className="grid gap-5 sm:grid-cols-2">
        <FormField label={t("addressRecipient")} error={errors.recipient} required>
          {field("recipient", "name")}
        </FormField>
        <FormField label={t("addressPhone")} error={errors.phone} required>
          {field("phone", "tel")}
        </FormField>
      </div>
      <FormField label={t("addressLine")} hint={t("addressLineHint")} error={errors.line} required>
        {field("line", "address-line1")}
      </FormField>
      {picking ? (
        <div className="grid gap-5 sm:grid-cols-3">
          <FormField label={t("addressProvince")} error={errors.province} required>
            {picker("province", provinces, values.provinceId ? String(values.provinceId) : undefined, (area) => {
              setDistricts(null);
              setWards(null);
              setValues((current) => ({
                ...current,
                province: area.name,
                provinceId: Number(area.id),
                district: "",
                districtId: null,
                ward: "",
                wardCode: null,
              }));
            })}
          </FormField>
          <FormField label={t("addressDistrict")} error={errors.district} required>
            {picker("district", values.provinceId ? districts : [], values.districtId ? String(values.districtId) : undefined, (area) => {
              setWards(null);
              setValues((current) => ({
                ...current,
                district: area.name,
                districtId: Number(area.id),
                ward: "",
                wardCode: null,
              }));
            })}
          </FormField>
          <FormField label={t("addressWard")} error={errors.ward} required>
            {picker("ward", values.districtId ? wards : [], values.wardCode ?? undefined, (area) => {
              setValues((current) => ({ ...current, ward: area.name, wardCode: area.id }));
            })}
          </FormField>
        </div>
      ) : (
        <div className="grid gap-5 sm:grid-cols-3">
          <FormField label={t("addressWard")} error={errors.ward}>
            {field("ward", "address-level4")}
          </FormField>
          <FormField label={t("addressDistrict")} error={errors.district}>
            {field("district", "address-level3")}
          </FormField>
          <FormField label={t("addressProvince")} error={errors.province} required>
            {field("province", "address-level1")}
          </FormField>
        </div>
      )}
      {picking && initial && !initial.wardCode && initial.province ? (
        <p className="text-muted-foreground text-xs leading-relaxed">
          {t("addressPickAgain", { address: [initial.ward, initial.district, initial.province].filter(Boolean).join(", ") })}
        </p>
      ) : null}

      {formError ? (
        <p role="alert" className="bg-danger-dim text-danger-text px-3.5 py-3 text-sm">
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
