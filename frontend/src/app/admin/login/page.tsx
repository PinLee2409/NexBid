import type { Metadata } from "next";
import { getTranslations } from "next-intl/server";

import { LoginForm } from "@/components/auth/login-form";

export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations("auth");
  return { title: t("adminLoginTitle"), description: t("adminLoginSubtitle"), robots: { index: false } };
}

export default function AdminLoginPage() {
  return <LoginForm audience="admin" />;
}
