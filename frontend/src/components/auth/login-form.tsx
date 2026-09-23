"use client";

import { Loader2 } from "lucide-react";
import { useTranslations } from "next-intl";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useState, useTransition, type FormEvent } from "react";
import { toast } from "sonner";

import { FormField } from "@/components/common/form-field";
import { Button } from "@/components/ui/button";
import { Checkbox } from "@/components/ui/checkbox";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { signIn } from "@/services/session-service";

interface FieldErrors {
  email?: string;
  password?: string;
}

const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

export function LoginForm() {
  const t = useTranslations("auth");
  const tc = useTranslations("common");
  const router = useRouter();

  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [remember, setRemember] = useState(true);
  const [errors, setErrors] = useState<FieldErrors>({});
  const [formError, setFormError] = useState<string | null>(null);
  const [isPending, startTransition] = useTransition();

  function validate(): FieldErrors {
    const next: FieldErrors = {};

    if (!email.trim()) next.email = t("errors.emailRequired");
    else if (!EMAIL_PATTERN.test(email.trim()))
      next.email = t("errors.emailInvalid");

    if (!password) next.password = t("errors.passwordRequired");

    return next;
  }

  function handleSubmit(event: FormEvent) {
    event.preventDefault();

    const nextErrors = validate();
    setErrors(nextErrors);
    setFormError(null);

    if (Object.keys(nextErrors).length > 0) return;

    startTransition(async () => {
      const result = await signIn({ email, password });

      if (!result.ok) {
        setFormError(result.message);
        return;
      }

      toast.success(t("signedIn"), {
        description: t("welcomeBack", { name: result.user.fullName }),
      });
      router.push("/");
    });
  }

  return (
    <div>
      <h1 className="display text-[clamp(2rem,5vw,3.5rem)]">{t("loginTitle")}</h1>
      <p className="text-muted-foreground mt-2 text-sm leading-relaxed">
        {t("loginSubtitle")}
      </p>

      <form onSubmit={handleSubmit} noValidate className="mt-8 space-y-4">
        <FormField label={t("email")} error={errors.email} required>
          {(field) => (
            <Input
              {...field}
              type="email"
              autoComplete="email"
              placeholder={t("emailPlaceholder")}
              value={email}
              onChange={(event) => {
                setEmail(event.target.value);
                setErrors((current) => ({ ...current, email: undefined }));
              }}
              className="h-11"
            />
          )}
        </FormField>

        <FormField label={t("password")} error={errors.password} required>
          {(field) => (
            <Input
              {...field}
              type="password"
              autoComplete="current-password"
              value={password}
              onChange={(event) => {
                setPassword(event.target.value);
                setErrors((current) => ({ ...current, password: undefined }));
              }}
              className="h-11"
            />
          )}
        </FormField>

        <div className="flex items-center justify-between">
          <div className="flex items-center gap-2">
            <Checkbox
              id="remember-me"
              checked={remember}
              onCheckedChange={(checked) => setRemember(checked === true)}
            />
            <Label
              htmlFor="remember-me"
              className="cursor-pointer text-sm font-normal"
            >
              {t("rememberMe")}
            </Label>
          </div>

          {/* Recovery is out of scope for this build. */}
          <span className="text-muted-foreground cursor-not-allowed text-sm">
            {t("forgotPassword")}
          </span>
        </div>

        {formError ? (
          <p
            role="alert"
            className="bg-danger-dim text-danger-text px-3.5 py-3 text-sm"
          >
            {formError}
          </p>
        ) : null}

        <Button type="submit" className="h-11 w-full" disabled={isPending}>
          {isPending ? <Loader2 className="size-4 animate-spin" /> : null}
          {t("signInAction")}
        </Button>
      </form>

      <p className="border-line text-dim mt-8 border-t pt-4 text-xs leading-relaxed">
        <span className="label-sm text-foreground">{t("demoTitle")}</span>{" "}
        {t("demoBody", { email: "pin@nexbid.com" })}
      </p>

      <p className="text-muted-foreground mt-6 text-center text-sm">
        {t("noAccount")}{" "}
        <Link
          href="/register"
          className="text-foreground font-medium underline underline-offset-4"
        >
          {tc("signUp")}
        </Link>
      </p>
    </div>
  );
}
