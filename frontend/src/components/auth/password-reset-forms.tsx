"use client";

import { Loader2 } from "lucide-react";
import { useTranslations } from "next-intl";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useState, useTransition, type FormEvent } from "react";
import { toast } from "sonner";

import { FormField } from "@/components/common/form-field";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { PasswordInput } from "@/components/ui/password-input";
import { useApiErrorMessage } from "@/hooks/use-labels";
import { requestPasswordReset, resetPassword } from "@/services/password-service";

/**
 * EN: "Forgot password": always the same answer, so the form never tells whether an email has an account.
 * VI: "Quên mật khẩu": luôn trả lời giống nhau, để form không bao giờ cho biết email có tài khoản hay không.
 */
export function ForgotPasswordForm() {
  const t = useTranslations("auth");
  const errorMessage = useApiErrorMessage();
  const [email, setEmail] = useState("");
  const [sent, setSent] = useState(false);
  const [isPending, startTransition] = useTransition();

  function submit(event: FormEvent) {
    event.preventDefault();
    if (!email.trim()) return;
    startTransition(async () => {
      try {
        await requestPasswordReset(email.trim());
        setSent(true);
      } catch (error) {
        toast.error(errorMessage(error));
      }
    });
  }

  return (
    <div>
      <h1 className="display text-[clamp(2rem,5vw,3.5rem)]">{t("forgotTitle")}</h1>
      <p className="text-muted-foreground mt-2 text-sm leading-relaxed">{t("forgotSubtitle")}</p>
      {sent ? (
        <p role="status" className="bg-surface mt-8 px-4 py-3 text-sm leading-relaxed">
          {t("forgotSent")}
        </p>
      ) : (
        <form onSubmit={submit} noValidate className="mt-8 space-y-4">
          <FormField label={t("email")} required>
            {(field) => (
              <Input
                {...field}
                type="email"
                autoComplete="email"
                value={email}
                onChange={(event) => setEmail(event.target.value)}
                className="h-11"
              />
            )}
          </FormField>
          <Button type="submit" className="h-11 w-full" disabled={isPending || !email.trim()}>
            {isPending ? <Loader2 className="size-4 animate-spin" /> : null}
            {t("forgotAction")}
          </Button>
        </form>
      )}
      <p className="text-muted-foreground mt-8 text-center text-sm">
        <Link href="/login" className="text-foreground font-medium underline underline-offset-4">
          {t("backToSignIn")}
        </Link>
      </p>
    </div>
  );
}

/**
 * EN: The page the emailed link opens: a new password, twice. Every session ends, so the person signs in again.
 * VI: Trang mà link trong email mở ra: nhập mật khẩu mới hai lần. Mọi phiên kết thúc, nên người dùng đăng nhập lại.
 */
export function ResetPasswordForm({ token }: { token: string }) {
  const t = useTranslations("auth");
  const errorMessage = useApiErrorMessage();
  const router = useRouter();
  const [password, setPassword] = useState("");
  const [again, setAgain] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [isPending, startTransition] = useTransition();

  function submit(event: FormEvent) {
    event.preventDefault();
    if (password.length < 8) return setError(t("errors.passwordShort"));
    if (password !== again) return setError(t("errors.confirmMismatch"));
    setError(null);
    startTransition(async () => {
      try {
        await resetPassword(token, password);
      } catch (cause) {
        setError(errorMessage(cause));
        return;
      }
      toast.success(t("resetDone"));
      router.push("/login");
    });
  }

  return (
    <div>
      <h1 className="display text-[clamp(2rem,5vw,3.5rem)]">{t("resetTitle")}</h1>
      <p className="text-muted-foreground mt-2 text-sm leading-relaxed">{t("resetSubtitle")}</p>
      {!token ? (
        <p role="alert" className="bg-danger-dim text-danger-text mt-8 px-3.5 py-3 text-sm">
          {t("resetNoToken")}
        </p>
      ) : (
        <form onSubmit={submit} noValidate className="mt-8 space-y-4">
          <FormField label={t("newPassword")} required>
            {(field) => (
              <PasswordInput
                {...field}
                autoComplete="new-password"
                value={password}
                onChange={(event) => setPassword(event.target.value)}
                className="h-11"
              />
            )}
          </FormField>
          <FormField label={t("newPasswordAgain")} required>
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
          {error ? (
            <p role="alert" className="bg-danger-dim text-danger-text px-3.5 py-3 text-sm">
              {error}
            </p>
          ) : null}
          <Button type="submit" className="h-11 w-full" disabled={isPending}>
            {isPending ? <Loader2 className="size-4 animate-spin" /> : null}
            {t("resetAction")}
          </Button>
        </form>
      )}
      <p className="text-muted-foreground mt-8 text-center text-sm">
        <Link href="/forgot-password" className="text-foreground font-medium underline underline-offset-4">
          {t("resetAskAgain")}
        </Link>
      </p>
    </div>
  );
}
