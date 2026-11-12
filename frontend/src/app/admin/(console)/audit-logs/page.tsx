"use client";

import { ListChecks } from "lucide-react";
import { useFormatter, useTranslations } from "next-intl";

import { EmptyState } from "@/components/common/empty-state";
import { PageHeader } from "@/components/layout/page-header";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { Skeleton } from "@/components/ui/skeleton";
import { useAsyncData } from "@/hooks/use-async-data";
import { useEnumLabels } from "@/hooks/use-labels";
import { listAuditLogs } from "@/services/admin-service";

export default function AuditLogsPage() {
  const t = useTranslations("admin");
  const tc = useTranslations("common");
  const labels = useEnumLabels();
  const format = useFormatter();

  const { state } = useAsyncData("audit-logs", listAuditLogs);

  return (
    <>
      <PageHeader title={t("auditTitle")} description={t("auditSubtitle")} />

      <div className="mt-10">
        {state.status === "loading" ? (
          <div>
            {Array.from({ length: 6 }).map((_, index) => (
              <Skeleton
                key={index}
                className="border-line h-12 w-full rounded-none border-b"
              />
            ))}
          </div>
        ) : state.status === "success" && state.data.length === 0 ? (
          <EmptyState
            icon={ListChecks}
            title={t("auditEmptyTitle")}
            description={t("auditEmptyBody")}
          />
        ) : state.status === "success" ? (
          <div className="border-line overflow-x-auto border-t">
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>{t("auditAction")}</TableHead>
                  <TableHead>{t("auditActor")}</TableHead>
                  <TableHead>{t("auditEntity")}</TableHead>
                  <TableHead>{t("auditChange")}</TableHead>
                  <TableHead>{t("auditIp")}</TableHead>
                  <TableHead className="text-right">{tc("date")}</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {state.data.map((log) => (
                  <TableRow key={log.id}>
                    <TableCell className="label">
                      {labels.auditAction(log.action)}
                    </TableCell>
                    <TableCell className="text-muted-foreground">
                      {log.actorDisplayName === "system"
                        ? t("system")
                        : log.actorDisplayName}
                    </TableCell>
                    <TableCell className="text-muted-foreground">
                      <span className="mono-figure text-xs">
                        {log.entityType}#{log.entityId.slice(-8)}
                      </span>
                    </TableCell>
                    <TableCell className="text-muted-foreground">
                      {log.oldValue || log.newValue ? (
                        <span className="mono-figure text-xs">
                          {log.oldValue ?? "—"} → {log.newValue ?? "—"}
                        </span>
                      ) : (
                        "—"
                      )}
                    </TableCell>
                    <TableCell className="text-muted-foreground mono-figure text-xs">
                      {log.ipAddress}
                    </TableCell>
                    <TableCell className="text-dim mono-figure text-right text-xs">
                      {format.dateTime(new Date(log.createdAt), {
                        dateStyle: "medium",
                        timeStyle: "short",
                      })}
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </div>
        ) : null}
      </div>
    </>
  );
}
