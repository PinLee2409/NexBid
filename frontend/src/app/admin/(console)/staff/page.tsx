"use client";

import { UsersRound } from "lucide-react";
import { useTranslations } from "next-intl";
import { useState, useTransition } from "react";
import { toast } from "sonner";

import { ApiErrorState } from "@/components/common/api-error-state";
import { AuctionListRowSkeleton } from "@/components/common/loading-skeleton";
import { PageHeader } from "@/components/layout/page-header";
import { Button } from "@/components/ui/button";
import { Checkbox } from "@/components/ui/checkbox";
import { Input } from "@/components/ui/input";
import { useAsyncData } from "@/hooks/use-async-data";
import { useApiErrorMessage, useEnumLabels } from "@/hooks/use-labels";
import {
  addStaff,
  listStaff,
  setStaffRoles,
  type StaffMember,
} from "@/services/market-service";
import { useSession } from "@/services/session-service";
import type { UserRole } from "@/types";

const GIVEN: UserRole[] = ["MODERATOR", "FINANCE", "SUPPORT"];

/**
 * EN: Who works on NexBid and with which staff roles. ADMIN only; an admin's own roles and ADMIN itself are not changed
 *     here. A change takes effect at the person's next token refresh, within minutes.
 * VI: Ai làm việc trên NexBid và với vai trò nhân viên nào. Chỉ ADMIN; vai trò của chính admin và quyền ADMIN không đổi
 *     ở đây. Thay đổi có hiệu lực ở lần làm mới token kế tiếp của người đó, trong vài phút.
 */
export default function AdminStaffPage() {
  const t = useTranslations("staff");
  const labels = useEnumLabels();
  const errorMessage = useApiErrorMessage();
  const { user } = useSession();
  const { state, refresh } = useAsyncData("admin-staff", listStaff);
  const [email, setEmail] = useState("");
  const [roles, setRoles] = useState<UserRole[]>(["SUPPORT"]);
  const [isPending, startTransition] = useTransition();

  function run(task: () => Promise<unknown>) {
    startTransition(async () => {
      try {
        await task();
        toast.success(t("saved"));
        refresh();
      } catch (error) {
        toast.error(errorMessage(error));
      }
    });
  }

  function toggle(member: StaffMember, role: UserRole, on: boolean) {
    const next = GIVEN.filter((each) =>
      each === role ? on : member.roles.includes(each),
    );
    run(() => setStaffRoles(member.id, next));
  }

  return (
    <>
      <PageHeader title={t("title")} description={t("subtitle")} />

      <form
        className="border-line mt-8 flex flex-wrap items-end gap-4 border p-5"
        onSubmit={(event) => {
          event.preventDefault();
          run(async () => {
            await addStaff(email, roles);
            setEmail("");
          });
        }}
      >
        <div className="min-w-64 flex-1 space-y-1.5">
          <label htmlFor="staff-email" className="label-sm text-dim">
            {t("email")}
          </label>
          <Input
            id="staff-email"
            type="email"
            value={email}
            onChange={(event) => setEmail(event.target.value)}
            placeholder="name@example.com"
            className="h-10"
          />
        </div>
        <fieldset className="flex flex-wrap gap-4">
          <legend className="sr-only">{t("roles")}</legend>
          {GIVEN.map((role) => (
            <label key={role} className="flex items-center gap-2 text-sm">
              <Checkbox
                checked={roles.includes(role)}
                onCheckedChange={(on) =>
                  setRoles((current) =>
                    on
                      ? [...current, role]
                      : current.filter((each) => each !== role),
                  )
                }
              />
              {labels.role(role)}
            </label>
          ))}
        </fieldset>
        <Button
          type="submit"
          disabled={isPending || !email.trim() || roles.length === 0}
        >
          {t("add")}
        </Button>
      </form>
      <p className="text-dim mt-2 text-xs">{t("hint")}</p>

      <div className="mt-8 overflow-x-auto" data-testid="staff" tabIndex={0} role="region" aria-label={t("title")}>
        {state.status === "loading" ? (
          <AuctionListRowSkeleton />
        ) : state.status === "error" ? (
          <ApiErrorState icon={UsersRound} error={state.error} />
        ) : (
          <table className="w-full min-w-[640px] text-sm">
            <thead>
              <tr className="border-line text-dim border-b text-left">
                <th className="label-sm py-3 pr-4 font-normal">
                  {t("person")}
                </th>
                <th className="label-sm py-3 pr-4 font-normal">
                  {labels.role("ADMIN")}
                </th>
                {GIVEN.map((role) => (
                  <th key={role} className="label-sm py-3 pr-4 font-normal">
                    {labels.role(role)}
                  </th>
                ))}
              </tr>
            </thead>
            <tbody>
              {state.data.map((member) => {
                const locked = member.id === user?.id;
                return (
                  <tr key={member.id} className="border-line border-b">
                    <td className="py-3 pr-4">
                      <p className="font-medium">{member.fullName}</p>
                      <p className="text-dim text-xs">{member.email}</p>
                    </td>
                    <td className="py-3 pr-4">
                      {member.roles.includes("ADMIN") ? "✓" : ""}
                    </td>
                    {GIVEN.map((role) => (
                      <td key={role} className="py-3 pr-4">
                        <Checkbox
                          aria-label={t("toggle", {
                            role: labels.role(role),
                            name: member.fullName,
                          })}
                          checked={member.roles.includes(role)}
                          disabled={locked || isPending}
                          onCheckedChange={(on) =>
                            toggle(member, role, on === true)
                          }
                        />
                      </td>
                    ))}
                  </tr>
                );
              })}
            </tbody>
          </table>
        )}
      </div>
    </>
  );
}
