"use client";

import { Landmark, Loader2 } from "lucide-react";
import { useTranslations } from "next-intl";
import { useState, useTransition, type FormEvent } from "react";
import { toast } from "sonner";

import { FormField } from "@/components/common/form-field";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { useApiErrorMessage } from "@/hooks/use-labels";
import { saveMyBankAccount, type BankAccount } from "@/services/bank-account-service";

export const EMPTY_BANK_ACCOUNT: BankAccount = { bankName: "", accountNumber: "", accountHolder: "" };

/**
 * EN: The same checks the server makes, so a mistake is shown next to its field. Empty when all is well.
 * VI: Cùng các kiểm tra như server, để lỗi hiện ngay cạnh ô nhập. Rỗng khi mọi thứ hợp lệ.
 */
export function useBankAccountErrors() {
  const t = useTranslations("seller");
  const tc = useTranslations("common");
  return (values: BankAccount): Partial<Record<keyof BankAccount, string>> => {
    const found: Partial<Record<keyof BankAccount, string>> = {};
    if (!values.bankName.trim()) found.bankName = tc("required");
    if (!/^[0-9 ]{6,30}$/.test(values.accountNumber.trim())) found.accountNumber = t("bankNumberInvalid");
    if (!values.accountHolder.trim()) found.accountHolder = tc("required");
    return found;
  };
}

/**
 * EN: The three bank fields, for a form of its own or inside another one (the dispute dialog).
 * VI: Ba ô thông tin ngân hàng, dùng trong form riêng hoặc bên trong form khác (hộp khiếu nại).
 */
export function BankAccountFields({
  values,
  errors,
  disabled,
  onChange,
}: {
  values: BankAccount;
  errors: Partial<Record<keyof BankAccount, string>>;
  disabled?: boolean;
  onChange: (key: keyof BankAccount, value: string) => void;
}) {
  const t = useTranslations("seller");

  function input(key: keyof BankAccount, autoComplete: string) {
    return function BankInput(props: { id: string; "aria-describedby"?: string; "aria-invalid"?: boolean }) {
      return (
        <Input
          {...props}
          autoComplete={autoComplete}
          inputMode={key === "accountNumber" ? "numeric" : undefined}
          value={values[key]}
          disabled={disabled}
          onChange={(event) => onChange(key, event.target.value)}
        />
      );
    };
  }

  return (
    <>
      <FormField label={t("bankName")} error={errors.bankName} required>
        {input("bankName", "organization")}
      </FormField>
      <FormField label={t("bankNumber")} error={errors.accountNumber} required>
        {input("accountNumber", "off")}
      </FormField>
      <FormField label={t("bankHolder")} hint={t("bankHolderHint")} error={errors.accountHolder} required>
        {input("accountHolder", "name")}
      </FormField>
    </>
  );
}

/**
 * EN: Where NexBid sends this person money: a seller's payouts, a buyer's refunds. Shows the saved account, or the form.
 * VI: Nơi NexBid chuyển tiền cho người này: tiền bán hàng của người bán, tiền hoàn của người mua. Hiện tài khoản đã lưu,
 *     hoặc form nhập.
 */
export function BankAccountForm({
  initial,
  hint,
  onSaved,
}: {
  initial: BankAccount | null;
  hint: string;
  onSaved: () => void;
}) {
  const t = useTranslations("seller");
  const tc = useTranslations("common");
  const errorMessage = useApiErrorMessage();
  const check = useBankAccountErrors();
  const [editing, setEditing] = useState(initial === null);
  const [values, setValues] = useState<BankAccount>(initial ?? EMPTY_BANK_ACCOUNT);
  const [errors, setErrors] = useState<Partial<Record<keyof BankAccount, string>>>({});
  const [isPending, startTransition] = useTransition();

  if (!editing && initial) {
    return (
      <div className="mt-5 space-y-1.5">
        <p className="font-medium">{initial.bankName}</p>
        <p className="mono-figure">{initial.accountNumber}</p>
        <p className="label-sm text-dim">{initial.accountHolder}</p>
        <Button size="sm" variant="outline" className="mt-4" onClick={() => setEditing(true)}>
          <Landmark className="size-4" />
          {t("bankEdit")}
        </Button>
      </div>
    );
  }

  function submit(event: FormEvent) {
    event.preventDefault();
    const found = check(values);
    setErrors(found);
    if (Object.keys(found).length > 0) return;

    startTransition(async () => {
      try {
        await saveMyBankAccount(values);
      } catch (error) {
        toast.error(errorMessage(error));
        return;
      }
      toast.success(t("bankSaved"));
      setEditing(false);
      onSaved();
    });
  }

  return (
    <form onSubmit={submit} noValidate className="mt-5 space-y-4">
      <p className="text-muted-foreground text-sm">{hint}</p>
      <BankAccountFields
        values={values}
        errors={errors}
        disabled={isPending}
        onChange={(key, value) => {
          setValues((current) => ({ ...current, [key]: value }));
          setErrors((current) => ({ ...current, [key]: undefined }));
        }}
      />
      <div className="flex gap-2">
        <Button type="submit" disabled={isPending}>
          {isPending ? <Loader2 className="size-4 animate-spin" /> : null}
          {t("bankSave")}
        </Button>
        {initial ? (
          <Button type="button" variant="outline" onClick={() => setEditing(false)} disabled={isPending}>
            {tc("cancel")}
          </Button>
        ) : null}
      </div>
    </form>
  );
}
