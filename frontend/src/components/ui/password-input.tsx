"use client";

import { Eye, EyeOff } from "lucide-react";
import { useTranslations } from "next-intl";
import { useState, type ComponentProps } from "react";

import { Input } from "@/components/ui/input";
import { cn } from "@/lib/utils";

/**
 * EN: A password field with an eye button to show what was typed. It starts hidden, and the button says what it will do
 *     next ("Show password" / "Hide password") so a screen reader announces it.
 * VI: Ô mật khẩu có nút con mắt để hiện những gì đã gõ. Ban đầu ẩn, và nút nói việc nó sẽ làm ("Hiện mật khẩu" / "Ẩn mật
 *     khẩu") để trình đọc màn hình đọc được.
 */
export function PasswordInput({ className, ...props }: Omit<ComponentProps<"input">, "type">) {
  const t = useTranslations("common");
  const [visible, setVisible] = useState(false);

  return (
    <div className="relative">
      <Input {...props} type={visible ? "text" : "password"} className={cn("pr-11", className)} />
      <button
        type="button"
        onClick={() => setVisible((current) => !current)}
        aria-label={visible ? t("hidePassword") : t("showPassword")}
        aria-pressed={visible}
        disabled={props.disabled}
        className="text-dim hover:text-foreground focus-visible:text-foreground absolute inset-y-0 right-0 flex w-11 items-center justify-center transition-colors outline-none disabled:opacity-40"
      >
        {visible ? <EyeOff className="size-4" aria-hidden="true" /> : <Eye className="size-4" aria-hidden="true" />}
      </button>
    </div>
  );
}
