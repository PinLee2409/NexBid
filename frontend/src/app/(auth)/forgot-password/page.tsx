import type { Metadata } from "next";
import { getTranslations } from "next-intl/server";

import { ForgotPasswordForm } from "@/components/auth/password-reset-forms";

export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations("auth");
  return { title: t("forgotTitle"), robots: { index: false } };
}

export default async function Page() {
  return <ForgotPasswordForm />;
}
