"use client";

import { Gavel, Heart, Loader2, Trophy, Wallet } from "lucide-react";
import { useFormatter, useTranslations } from "next-intl";
import { useState, useTransition, type FormEvent } from "react";
import { toast } from "sonner";

import { FormField } from "@/components/common/form-field";
import { StatTileSkeleton } from "@/components/common/loading-skeleton";
import { StatTile } from "@/components/common/stat-tile";
import { PageHeader } from "@/components/layout/page-header";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Skeleton } from "@/components/ui/skeleton";
import { useAsyncData } from "@/hooks/use-async-data";
import { useEnumLabels } from "@/hooks/use-labels";
import { formatCompactCurrency } from "@/lib/format";
import { cn } from "@/lib/utils";
import { getAccountStats, updateProfile } from "@/services/account-service";
import { useSession } from "@/services/session-service";

export default function ProfilePage() {
  const t = useTranslations("account");
  const tc = useTranslations("common");
  const labels = useEnumLabels();
  const format = useFormatter();

  const { user } = useSession();
  const { state } = useAsyncData("account-stats", getAccountStats);

  const [fullName, setFullName] = useState(user?.fullName ?? "");
  const [displayName, setDisplayName] = useState(user?.displayName ?? "");
  const [error, setError] = useState<string | null>(null);
  const [isPending, startTransition] = useTransition();

  function handleSubmit(event: FormEvent) {
    event.preventDefault();

    if (!fullName.trim()) {
      setError(tc("required"));
      return;
    }

    setError(null);
    startTransition(async () => {
      await updateProfile({ fullName, displayName });
      toast.success(t("profileSaved"));
    });
  }

  const stats = state.status === "success" ? state.data : null;

  return (
    <>
      <PageHeader title={t("profileTitle")} description={t("profileSubtitle")} />

      <section className="mt-10" aria-labelledby="activity-heading">
        <h2 id="activity-heading" className="label-sm text-dim">
          {t("activity")}
        </h2>

        <div className="mt-6 grid gap-x-8 gap-y-8 sm:grid-cols-2 lg:grid-cols-4">
          {stats === null
            ? Array.from({ length: 4 }).map((_, index) => (
                <StatTileSkeleton key={index} />
              ))
            : [
                { icon: Gavel, label: t("statActiveBids"), value: String(stats.activeBids) },
                { icon: Trophy, label: t("statWon"), value: String(stats.won) },
                { icon: Heart, label: t("statWatching"), value: String(stats.watching) },
                {
                  icon: Wallet,
                  label: t("statSpent"),
                  value: formatCompactCurrency(stats.totalSpent),
                },
              ].map((tile) => (
                <StatTile key={tile.label} {...tile} />
              ))}
        </div>
      </section>

      <section className="border-line mt-16 border-t pt-10" aria-labelledby="details-heading">
        <h2 id="details-heading" className="display text-2xl">
          {t("personalDetails")}
        </h2>

        {!user ? (
          <div className="mt-4 space-y-4">
            <Skeleton className="h-11 w-full" />
            <Skeleton className="h-11 w-full" />
          </div>
        ) : (
          <form onSubmit={handleSubmit} noValidate className="mt-8 max-w-lg space-y-6">
            <FormField label={tc("seller")} error={error} required>
              {(field) => (
                <Input
                  {...field}
                  value={fullName}
                  onChange={(event) => {
                    setFullName(event.target.value);
                    setError(null);
                  }}
                  className=""
                />
              )}
            </FormField>

            <FormField
              label={t("publicHandle")}
              hint={t("publicHandleHint")}
            >
              {(field) => (
                <Input
                  {...field}
                  value={displayName}
                  onChange={(event) => setDisplayName(event.target.value)}
                  className=""
                />
              )}
            </FormField>

            <div className="grid gap-5 sm:grid-cols-2">
              <div>
                <p className="label-sm text-dim">{t("memberSince")}</p>
                <p className="mono-figure mt-1.5 text-sm">
                  {format.dateTime(new Date(user.createdAt), {
                    dateStyle: "long",
                  })}
                </p>
              </div>
              <div>
                <p className="label-sm text-dim">{t("accountStatus")}</p>
                <p
                  className={cn(
                    "label mt-1.5",
                    user.status === "BLOCKED" ? "text-danger-text" : "text-success",
                  )}
                >
                  {labels.userStatus(user.status)}
                </p>
              </div>
            </div>

            <div>
              <p className="label-sm text-dim">{t("roles")}</p>
              <ul className="mt-2 flex flex-wrap gap-2">
                {user.roles.map((role) => (
                  <li
                    key={role}
                    className="label-sm border-line text-muted-foreground border px-2.5 py-1.5"
                  >
                    {labels.role(role)}
                  </li>
                ))}
              </ul>
            </div>

            <Button type="submit" disabled={isPending}>
              {isPending ? <Loader2 className="size-4 animate-spin" /> : null}
              {t("saveChanges")}
            </Button>
          </form>
        )}
      </section>
    </>
  );
}
