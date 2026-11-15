"use client";

import { KeyRound, Loader2, ShieldCheck } from "lucide-react";
import { useTranslations } from "next-intl";
import { useState, useTransition, type FormEvent } from "react";
import { toast } from "sonner";

import { FormField } from "@/components/common/form-field";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Skeleton } from "@/components/ui/skeleton";
import { useAsyncData } from "@/hooks/use-async-data";
import { useApiErrorMessage } from "@/hooks/use-labels";
import {
  disableTwoFactor,
  enableTwoFactor,
  getTwoFactorEnabled,
  startTwoFactor,
} from "@/services/two-factor-service";

type Step =
  | { kind: "idle" }
  | { kind: "setup"; secret: string; otpauthUri: string }
  | { kind: "codes"; codes: string[] }
  | { kind: "disable" };

/**
 * EN: Two-step sign-in on the profile. Turning it on shows the key for the authenticator app (to type, or to open on
 *     a phone), asks for the first code, then shows the recovery codes once. Turning it off needs a code.
 * VI: Đăng nhập hai bước trên trang hồ sơ. Khi bật: hiện khoá cho ứng dụng xác thực (để gõ, hoặc mở trên điện thoại),
 *     hỏi mã đầu tiên, rồi hiện các mã khôi phục đúng một lần. Muốn tắt thì cần một mã.
 */
export function TwoFactorSettings() {
  const t = useTranslations("account");
  const tc = useTranslations("common");
  const errorMessage = useApiErrorMessage();
  const { state, refresh } = useAsyncData("two-factor", getTwoFactorEnabled);
  const [step, setStep] = useState<Step>({ kind: "idle" });
  const [code, setCode] = useState("");
  const [isPending, startTransition] = useTransition();

  function run(action: () => Promise<void>) {
    startTransition(async () => {
      try {
        await action();
      } catch (error) {
        toast.error(errorMessage(error));
      }
    });
  }

  function begin() {
    run(async () => {
      const setup = await startTwoFactor();
      setCode("");
      setStep({ kind: "setup", ...setup });
    });
  }

  function confirm(event: FormEvent) {
    event.preventDefault();
    run(async () => {
      const codes = await enableTwoFactor(code);
      setStep({ kind: "codes", codes });
      toast.success(t("twoFactorOn"));
      refresh();
    });
  }

  function turnOff(event: FormEvent) {
    event.preventDefault();
    run(async () => {
      await disableTwoFactor(code);
      setStep({ kind: "idle" });
      toast.success(t("twoFactorOff"));
      refresh();
    });
  }

  const enabled = state.status === "success" && state.data;

  return (
    <section className="border-line mt-16 border-t pt-10" aria-labelledby="two-factor-heading">
      <h2 id="two-factor-heading" className="display text-2xl">
        {t("twoFactorTitle")}
      </h2>
      <p className="text-muted-foreground mt-2 max-w-xl text-sm leading-relaxed">{t("twoFactorBody")}</p>

      <div className="mt-6 max-w-lg">
        {state.status === "loading" ? (
          <Skeleton className="h-11 w-48" />
        ) : step.kind === "setup" ? (
          <form onSubmit={confirm} noValidate className="space-y-5">
            <ol className="list-decimal space-y-2 pl-5 text-sm leading-relaxed">
              <li>{t("twoFactorStepApp")}</li>
              <li>
                {t("twoFactorStepKey")}{" "}
                <code className="bg-muted mt-1 block px-2 py-1.5 font-mono text-sm tracking-wider break-all" data-testid="two-factor-secret">
                  {step.secret.replace(/(.{4})/g, "$1 ").trim()}
                </code>
                <a href={step.otpauthUri} className="text-signal-text mt-1 inline-block text-xs underline-offset-4 hover:underline">
                  {t("twoFactorOpenApp")}
                </a>
              </li>
              <li>{t("twoFactorStepCode")}</li>
            </ol>
            <FormField label={t("twoFactorCode")} required>
              {(field) => (
                <Input
                  {...field}
                  inputMode="numeric"
                  autoComplete="one-time-code"
                  maxLength={6}
                  value={code}
                  onChange={(event) => setCode(event.target.value)}
                  className="h-11 w-40 tracking-[0.3em]"
                />
              )}
            </FormField>
            <div className="flex gap-2">
              <Button type="submit" disabled={isPending || code.trim().length !== 6}>
                {isPending ? <Loader2 className="size-4 animate-spin" /> : <ShieldCheck className="size-4" />}
                {t("twoFactorEnable")}
              </Button>
              <Button type="button" variant="outline" onClick={() => setStep({ kind: "idle" })} disabled={isPending}>
                {tc("cancel")}
              </Button>
            </div>
          </form>
        ) : step.kind === "codes" ? (
          <div className="space-y-4">
            <p className="text-sm leading-relaxed">{t("twoFactorRecoveryBody")}</p>
            <ul className="bg-muted grid grid-cols-2 gap-x-6 gap-y-1 px-4 py-3 font-mono text-sm" data-testid="recovery-codes">
              {step.codes.map((recovery) => (
                <li key={recovery}>{recovery}</li>
              ))}
            </ul>
            <Button variant="outline" onClick={() => setStep({ kind: "idle" })}>
              {t("twoFactorSavedCodes")}
            </Button>
          </div>
        ) : step.kind === "disable" ? (
          <form onSubmit={turnOff} noValidate className="space-y-5">
            <FormField label={t("twoFactorCodeOrRecovery")} required>
              {(field) => (
                <Input
                  {...field}
                  autoComplete="one-time-code"
                  maxLength={20}
                  value={code}
                  onChange={(event) => setCode(event.target.value)}
                  className="h-11 w-56"
                />
              )}
            </FormField>
            <div className="flex gap-2">
              <Button type="submit" variant="destructive" disabled={isPending || !code.trim()}>
                {isPending ? <Loader2 className="size-4 animate-spin" /> : null}
                {t("twoFactorDisable")}
              </Button>
              <Button type="button" variant="outline" onClick={() => setStep({ kind: "idle" })} disabled={isPending}>
                {tc("cancel")}
              </Button>
            </div>
          </form>
        ) : enabled ? (
          <div className="flex flex-wrap items-center gap-4">
            <p className="text-success inline-flex items-center gap-2 text-sm">
              <ShieldCheck className="size-4" aria-hidden="true" />
              {t("twoFactorIsOn")}
            </p>
            <Button
              variant="outline"
              onClick={() => {
                setCode("");
                setStep({ kind: "disable" });
              }}
            >
              {t("twoFactorDisable")}
            </Button>
          </div>
        ) : (
          <Button onClick={begin} disabled={isPending}>
            {isPending ? <Loader2 className="size-4 animate-spin" /> : <KeyRound className="size-4" />}
            {t("twoFactorStart")}
          </Button>
        )}
      </div>
    </section>
  );
}
