"use client";

import { Loader2, ShieldOff, UserRound, Users } from "lucide-react";
import { useFormatter, useTranslations } from "next-intl";
import { useState, useTransition } from "react";
import { toast } from "sonner";

import { EmptyState } from "@/components/common/empty-state";
import { SearchBar } from "@/components/common/search-bar";
import { PageHeader } from "@/components/layout/page-header";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { useAsyncData } from "@/hooks/use-async-data";
import { useEnumLabels } from "@/hooks/use-labels";
import { cn } from "@/lib/utils";
import { listUsers, setUserBlocked } from "@/services/admin-service";
import type { User } from "@/types";

export default function AdminUsersPage() {
  const t = useTranslations("admin");
  const [search, setSearch] = useState("");

  const { state, refresh } = useAsyncData(`admin-users-${search}`, () =>
    listUsers(search),
  );

  return (
    <>
      <PageHeader title={t("usersTitle")} description={t("usersSubtitle")} />

      <div className="mt-8 max-w-sm">
        <SearchBar
          defaultValue={search}
          onSearch={setSearch}
          placeholder={t("usersTitle")}
        />
      </div>

      <div className="mt-8">
        {state.status === "loading" ? (
          <div>
            {Array.from({ length: 5 }).map((_, index) => (
              <Skeleton
                key={index}
                className="border-line h-16 w-full rounded-none border-b"
              />
            ))}
          </div>
        ) : state.status === "success" && state.data.length === 0 ? (
          <EmptyState
            icon={Users}
            title={t("usersEmptyTitle")}
            description={t("usersEmptyBody")}
          />
        ) : state.status === "success" ? (
          <ul className="border-line border-t">
            {state.data.map((user) => (
              <UserRow key={user.id} user={user} onChanged={refresh} />
            ))}
          </ul>
        ) : null}
      </div>
    </>
  );
}

function UserRow({ user, onChanged }: { user: User; onChanged: () => void }) {
  const t = useTranslations("admin");
  const labels = useEnumLabels();
  const format = useFormatter();
  const [isPending, startTransition] = useTransition();

  const blocked = user.status === "BLOCKED";

  function toggleBlocked() {
    startTransition(async () => {
      await setUserBlocked(user.id, !blocked);
      onChanged();
      toast.success(blocked ? t("userUnblocked") : t("userBlocked"), {
        description: user.email,
      });
    });
  }

  return (
    <li className="border-line flex flex-wrap items-center gap-x-5 gap-y-3 border-b py-4">
      <span
        className="bg-surface-2 text-dim label-sm flex size-10 shrink-0 items-center justify-center"
        aria-hidden="true"
      >
        {user.fullName.slice(0, 2).toUpperCase()}
      </span>

      <div className="min-w-[12rem] flex-1">
        <p className="label">{user.fullName}</p>
        <p className="text-muted-foreground mt-1 text-sm">{user.email}</p>
      </div>

      <ul className="hidden shrink-0 gap-2 sm:flex">
        {user.roles.map((role) => (
          <li key={role} className="label-sm border-line text-dim border px-2 py-1">
            {labels.role(role)}
          </li>
        ))}
      </ul>

      <p className="mono-figure text-dim hidden shrink-0 text-xs lg:block">
        {format.dateTime(new Date(user.createdAt), { dateStyle: "medium" })}
      </p>

      <p
        className={cn(
          "label-sm w-20 shrink-0",
          blocked ? "text-danger-text" : "text-success",
        )}
      >
        {labels.userStatus(user.status)}
      </p>

      <Button
        variant={blocked ? "outline" : "destructive"}
        onClick={toggleBlocked}
        disabled={isPending}
        className="shrink-0"
      >
        {isPending ? (
          <Loader2 className="size-3.5 animate-spin" />
        ) : blocked ? (
          <UserRound className="size-3.5" />
        ) : (
          <ShieldOff className="size-3.5" />
        )}
        {blocked ? t("unblockUser") : t("blockUser")}
      </Button>
    </li>
  );
}
