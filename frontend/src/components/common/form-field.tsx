"use client";

import { useId, type ReactNode } from "react";

import { Label } from "@/components/ui/label";
import { cn } from "@/lib/utils";

interface FormFieldProps {
  label: string;
  /** Inline validation message. Its presence marks the field invalid. */
  error?: string | null;
  hint?: string;
  required?: boolean;
  className?: string;
  /**
   * Receives the wiring the control needs: the id, and the aria attributes
   * that connect it to its error or hint.
   */
  children: (props: {
    id: string;
    "aria-invalid": boolean;
    "aria-describedby": string | undefined;
  }) => ReactNode;
}

/**
 * One field, one label, one message slot. Every form in the product uses this
 * so validation copy always lands in the same place and is announced the same
 * way to screen readers.
 */
export function FormField({
  label,
  error,
  hint,
  required = false,
  className,
  children,
}: FormFieldProps) {
  const id = useId();
  const errorId = `${id}-error`;
  const hintId = `${id}-hint`;

  const describedBy = error ? errorId : hint ? hintId : undefined;

  return (
    <div className={cn("space-y-2", className)}>
      <Label htmlFor={id} className="label-sm text-dim">
        {label}
        {required ? (
          <span className="text-danger-text" aria-hidden="true">
            *
          </span>
        ) : null}
      </Label>

      {children({
        id,
        "aria-invalid": Boolean(error),
        "aria-describedby": describedBy,
      })}

      {error ? (
        <p id={errorId} role="alert" className="label-sm text-danger-text">
          {error}
        </p>
      ) : hint ? (
        <p id={hintId} className="text-dim text-xs leading-relaxed">
          {hint}
        </p>
      ) : null}
    </div>
  );
}
