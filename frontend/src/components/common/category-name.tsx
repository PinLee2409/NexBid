import { useTranslations } from "next-intl";

import type { Category } from "@/types";

interface CategoryNameProps {
  category: Pick<Category, "slug" | "name">;
}

/**
 * Category names come from the catalogue but their copy is translated by slug.
 * Deliberately not a client component, so server-rendered pages can use it too.
 * A slug the messages do not cover falls back to whatever the API sent.
 */
export function CategoryName({ category }: CategoryNameProps) {
  const t = useTranslations("categories");
  const key = `${category.slug}.name`;

  return <>{t.has(key) ? t(key) : category.name}</>;
}
