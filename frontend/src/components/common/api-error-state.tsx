"use client";

import type { LucideIcon } from "lucide-react";
import { useTranslations } from "next-intl";

import { EmptyState } from "@/components/common/empty-state";
import { useApiErrorMessage } from "@/hooks/use-labels";

/**
 * EN: A list that failed to load. Says why in the reader's language, from the error's code rather than the
 *     server's English message.
 * VI: Một danh sách tải không được. Nói lý do theo ngôn ngữ người đọc, dựa vào mã lỗi thay vì thông báo tiếng Anh
 *     của server.
 */
export function ApiErrorState({ icon, error }: { icon: LucideIcon; error: unknown }) {
  const tc = useTranslations("common");
  const errorMessage = useApiErrorMessage();

  return <EmptyState tone="error" icon={icon} title={tc("tryAgain")} description={errorMessage(error)} />;
}
