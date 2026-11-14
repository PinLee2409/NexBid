"use client";

import { useTranslations } from "next-intl";
import { useEffect, useState, useTransition } from "react";
import { toast } from "sonner";

import { AddressForm } from "@/components/account/address-form";
import { BankAccountForm } from "@/components/account/bank-account-form";
import { Label } from "@/components/ui/label";
import { Skeleton } from "@/components/ui/skeleton";
import { Switch } from "@/components/ui/switch";
import { useAsyncData } from "@/hooks/use-async-data";
import { useApiErrorMessage } from "@/hooks/use-labels";
import { getEmailPreferences, getShippingAddress, saveEmailPreferences } from "@/services/account-service";
import { getMyBankAccount } from "@/services/bank-account-service";

/**
 * EN: The profile's delivery address, the bank account refunds are sent to, and email choices. Emails about money and
 *     goods always go out; outbid emails and the daily new-lot email can be turned off, as the link in each also does.
 * VI: Địa chỉ nhận hàng, tài khoản ngân hàng nhận tiền hoàn, và lựa chọn email trong hồ sơ. Email về tiền và hàng luôn
 *     được gửi; email bị vượt giá và email lô mới hằng ngày thì tắt được, link trong từng email cũng làm vậy.
 */
export function DeliveryAndEmailSettings() {
  const t = useTranslations("account");
  const address = useAsyncData("shipping-address", getShippingAddress);
  const preferences = useAsyncData("email-preferences", getEmailPreferences);
  const bank = useAsyncData("my-bank-account", getMyBankAccount);
  const errorMessage = useApiErrorMessage();
  const [isPending, startTransition] = useTransition();
  const [outbid, setOutbid] = useState<boolean | null>(null);
  const [digest, setDigest] = useState<boolean | null>(null);

  // EN: The email footer links to #email; scroll there once the section exists. / VI: Footer email trỏ tới #email; cuộn tới khi phần này đã có.
  useEffect(() => {
    if (window.location.hash === "#email") document.getElementById("email")?.scrollIntoView();
  }, []);

  const outbidOn = outbid ?? (preferences.state.status === "success" ? preferences.state.data.outbid : true);
  const digestOn = digest ?? (preferences.state.status === "success" ? preferences.state.data.digest !== false : true);

  function toggleOutbid(next: boolean) {
    setOutbid(next);
    startTransition(async () => {
      try {
        await saveEmailPreferences({ outbid: next, digest: digestOn });
        toast.success(t("emailSaved"));
      } catch (error) {
        setOutbid(!next);
        toast.error(errorMessage(error));
      }
    });
  }

  function toggleDigest(next: boolean) {
    setDigest(next);
    startTransition(async () => {
      try {
        await saveEmailPreferences({ outbid: outbidOn, digest: next });
        toast.success(t("emailSaved"));
      } catch (error) {
        setDigest(!next);
        toast.error(errorMessage(error));
      }
    });
  }

  return (
    <>
      <section className="border-line mt-16 border-t pt-10" aria-labelledby="address-heading">
        <h2 id="address-heading" className="display text-2xl">
          {t("addressTitle")}
        </h2>
        <p className="text-muted-foreground mt-2 max-w-2xl text-sm leading-relaxed">{t("addressSubtitle")}</p>

        <div className="mt-8 max-w-2xl">
          {address.state.status === "loading" ? (
            <div className="space-y-4">
              <Skeleton className="h-11 w-full" />
              <Skeleton className="h-11 w-full" />
            </div>
          ) : (
            <AddressForm
              key={address.state.status}
              initial={address.state.status === "success" ? address.state.data : null}
              submitLabel={t("addressSave")}
              onSaved={() => {
                toast.success(t("addressSaved"));
                address.refresh();
              }}
            />
          )}
        </div>
      </section>

      <section id="bank-account" className="border-line mt-16 scroll-mt-24 border-t pt-10" aria-labelledby="bank-heading">
        <h2 id="bank-heading" className="display text-2xl">
          {t("refundAccountTitle")}
        </h2>
        <div className="max-w-lg">
          {bank.state.status === "loading" ? (
            <div className="mt-5 space-y-4">
              <Skeleton className="h-11 w-full" />
              <Skeleton className="h-11 w-full" />
            </div>
          ) : (
            <BankAccountForm
              key={bank.state.status === "success" ? (bank.state.data?.accountNumber ?? "none") : "error"}
              initial={bank.state.status === "success" ? bank.state.data : null}
              hint={t("refundAccountHint")}
              onSaved={bank.refresh}
            />
          )}
        </div>
      </section>

      <section id="email" className="border-line mt-16 scroll-mt-24 border-t pt-10" aria-labelledby="email-heading">
        <h2 id="email-heading" className="display text-2xl">
          {t("emailTitle")}
        </h2>
        <p className="text-muted-foreground mt-2 max-w-2xl text-sm leading-relaxed">{t("emailSubtitle")}</p>

        <div className="border-line mt-8 flex max-w-2xl items-start justify-between gap-6 border-y py-5">
          <div>
            <Label htmlFor="email-outbid" className="text-base font-medium">
              {t("emailOutbid")}
            </Label>
            <p className="text-muted-foreground mt-1 text-sm leading-relaxed">{t("emailOutbidHint")}</p>
          </div>
          <Switch
            id="email-outbid"
            checked={outbidOn}
            disabled={isPending || preferences.state.status === "loading"}
            onCheckedChange={toggleOutbid}
          />
        </div>

        <div className="border-line flex max-w-2xl items-start justify-between gap-6 border-b py-5">
          <div>
            <Label htmlFor="email-digest" className="text-base font-medium">
              {t("emailDigest")}
            </Label>
            <p className="text-muted-foreground mt-1 text-sm leading-relaxed">{t("emailDigestHint")}</p>
          </div>
          <Switch
            id="email-digest"
            checked={digestOn}
            disabled={isPending || preferences.state.status === "loading"}
            onCheckedChange={toggleDigest}
          />
        </div>
      </section>
    </>
  );
}
