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
import { signUp } from "@/services/session-service";

interface FieldErrors {
  fullName?: string;
  email?: string;
  password?: string;
  confirmPassword?: string;
}

const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
const MIN_PASSWORD_LENGTH = 8;

export function RegisterForm() {
  const t = useTranslations("auth");
  const tc = useTranslations("common");
  const router = useRouter();

  const [values, setValues] = useState({
    fullName: "",
    email: "",
    password: "",
    confirmPassword: "",
  });
  const [errors, setErrors] = useState<FieldErrors>({});
  const [formError, setFormError] = useState<string | null>(null);
  const [isPending, startTransition] = useTransition();

  function setField(field: keyof typeof values, value: string) {
    setValues((current) => ({ ...current, [field]: value }));
    setErrors((current) => ({ ...current, [field]: undefined }));
  }

  /** Mirrors the server rules in spec §7.1. */
  function validate(): FieldErrors {
    const next: FieldErrors = {};

    if (!values.fullName.trim()) next.fullName = t("errors.nameRequired");

    if (!values.email.trim()) next.email = t("errors.emailRequired");
    else if (!EMAIL_PATTERN.test(values.email.trim()))
      next.email = t("errors.emailInvalid");

    if (!values.password) next.password = t("errors.passwordRequired");
    else if (values.password.length < MIN_PASSWORD_LENGTH)
      next.password = t("errors.passwordShort");

    if (values.confirmPassword !== values.password)
      next.confirmPassword = t("errors.confirmMismatch");

    return next;
  }

  function handleSubmit(event: FormEvent) {
    event.preventDefault();

    const nextErrors = validate();
    setErrors(nextErrors);
    setFormError(null);

    if (Object.keys(nextErrors).length > 0) return;

    startTransition(async () => {
      const result = await signUp({
        fullName: values.fullName,
        email: values.email,
        password: values.password,
      });

      if (!result.ok) {
        setFormError(result.message);
        return;
      }

      toast.success(t("accountCreated"), {
        description: t("accountCreatedBody", { name: result.user.fullName }),
      });
      router.push("/");
    });
  }

  return (
    <div>
      <h1 className="display text-[clamp(2rem,5vw,3.5rem)]">{t("registerTitle")}</h1>
      <p className="text-muted-foreground mt-2 text-sm leading-relaxed">
        {t("registerSubtitle")}
      </p>

      <form onSubmit={handleSubmit} noValidate className="mt-8 space-y-4">
        <FormField label={t("fullName")} error={errors.fullName} required>
          {(field) => (
            <Input
              {...field}
              autoComplete="name"
              placeholder={t("fullNamePlaceholder")}
              value={values.fullName}
              onChange={(event) => setField("fullName", event.target.value)}
              className="h-11"
            />
          )}
        </FormField>

        <FormField label={t("email")} error={errors.email} required>
          {(field) => (
            <Input
              {...field}
              type="email"
              autoComplete="email"
              placeholder={t("emailPlaceholder")}
              value={values.email}
              onChange={(event) => setField("email", event.target.value)}
              className="h-11"
            />
          )}
        </FormField>

        <FormField
          label={t("password")}
          error={errors.password}
          hint={t("passwordHint")}
          required
        >
          {(field) => (
            <Input
              {...field}
              type="password"
              autoComplete="new-password"
              value={values.password}
              onChange={(event) => setField("password", event.target.value)}
              className="h-11"
            />
          )}
        </FormField>

        <FormField
          label={t("confirmPassword")}
          error={errors.confirmPassword}
          required
        >
          {(field) => (
            <Input
              {...field}
              type="password"
              autoComplete="new-password"
              value={values.confirmPassword}
              onChange={(event) =>
                setField("confirmPassword", event.target.value)
              }
              className="h-11"
            />
          )}
        </FormField>

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
          {t("signUpAction")}
        </Button>
      </form>

      <p className="text-muted-foreground mt-6 text-center text-sm">
        {t("haveAccount")}{" "}
        <Link
          href="/login"
          className="text-foreground font-medium underline underline-offset-4"
        >
          {tc("signIn")}
        </Link>
      </p>
    </div>
  );
}
