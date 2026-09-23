import * as React from "react"
import { cn } from "cn"

function Textarea({ className, ...props }: React.ComponentProps<"textarea">) {
  return (
    <textarea
      data-slot="textarea"
      className={cn(
        "flex field-sizing-content min-h-28 w-full border border-line bg-surface px-3.5 py-3 text-base text-foreground transition-colors outline-none placeholder:text-dim focus-visible:border-signal-text focus-visible:bg-surface-2 disabled:cursor-not-allowed disabled:opacity-40 aria-invalid:border-danger-text md:text-sm",
        className
      )}
      {...props}
    />
  )
}

export { Textarea }
