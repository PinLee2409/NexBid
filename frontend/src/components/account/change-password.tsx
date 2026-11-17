"use client";

import { KeyRound, Loader2 } from "lucide-react";
import { useTranslations } from "next-intl";
import Link from "next/link";
import { useState, useTransition, type FormEvent } from "react";
import { toast } from "sonner";

import { FormField } from "@/components/common/form-field";
import { Button } from "@/components/ui/button";
import { PasswordInput } from "@/components/ui/password-input";
import { useApiErrorMessage } from "@/hooks/use-labels";
import { changePassword } from "@/services/password-service";

/**
 * EN: Changing the password on the profile. Accounts made with Google never knew a password; they use the reset link.
 * VI: Đổi mật khẩu trên trang hồ sơ. Tài khoản tạo bằng Google chưa từng có mật khẩu; họ dùng link đặt lại.
 */
export function ChangePassword() {
  const t = useTranslations("account");
  const errorMessage = useApiErrorMessage();
  const [current, setCurrent] = useState("");
  const [next, setNext] = useState("");
  const [again, setAgain] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [isPending, startTransition] = useTransition();

  function submit(event: FormEvent) {
    event.preventDefault();
    if (next.length < 8) return setError(t("passwordTooShort"));
    if (next !== again) return setError(t("passwordsDiffer"));
    setError(null);
    startTransition(async () => {
      try {
        await changePassword(current, next);
      } catch (cause) {
        setError(errorMessage(cause));
        return;
      }
      setCurrent("");
      setNext("");
      setAgain("");
      toast.success(t("passwordChanged"), { description: t("passwordChangedBody") });
    });
  }

  return (
    <section className="border-line mt-16 border-t pt-10" aria-labelledby="password-heading">
      <h2 id="password-heading" className="display text-2xl">
        {t("passwordTitle")}
      </h2>
      <p className="text-muted-foreground mt-2 max-w-xl text-sm leading-relaxed">
        {t("passwordBody")}{" "}
        <Link href="/forgot-password" className="text-signal-text underline underline-offset-4">
          {t("passwordForgot")}
        </Link>
      </p>
      <form onSubmit={submit} noValidate className="mt-6 max-w-lg space-y-4">
        <FormField label={t("passwordCurrent")} required>
          {(field) => (
            <PasswordInput
              {...field}
              autoComplete="current-password"
              value={current}
              onChange={(event) => setCurrent(event.target.value)}
              className="h-11"
            />
          )}
        </FormField>
        <div className="grid gap-4 sm:grid-cols-2">
          <FormField label={t("passwordNew")} required>
            {(field) => (
              <PasswordInput
                {...field}
                autoComplete="new-password"
                value={next}
                onChange={(event) => setNext(event.target.value)}
                className="h-11"
              />
            )}
          </FormField>
          <FormField label={t("passwordAgain")} required>
            {(field) => (
              <PasswordInput
                {...field}
                autoComplete="new-password"
                value={again}
                onChange={(event) => setAgain(event.target.value)}
                className="h-11"
              />
            )}
          </FormField>
        </div>
        {error ? (
          <p role="alert" className="bg-danger-dim text-danger-text px-3.5 py-3 text-sm">
            {error}
          </p>
        ) : null}
        <Button type="submit" disabled={isPending || !current || !next}>
          {isPending ? <Loader2 className="size-4 animate-spin" /> : <KeyRound className="size-4" />}
          {t("passwordAction")}
        </Button>
      </form>
    </section>
  );
}
