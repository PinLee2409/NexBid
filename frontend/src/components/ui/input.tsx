import * as React from "react"
import { cn } from "cn"

function Input({ className, type, ...props }: React.ComponentProps<"input">) {
  return (
    <input
      type={type}
      data-slot="input"
      className={cn(
        "h-11 w-full min-w-0 border border-line bg-surface px-3.5 py-2 text-base text-foreground transition-colors outline-none file:inline-flex file:h-6 file:border-0 file:bg-transparent file:text-sm file:font-medium file:text-foreground placeholder:text-dim focus-visible:border-signal-text focus-visible:bg-surface-2 disabled:pointer-events-none disabled:cursor-not-allowed disabled:opacity-40 aria-invalid:border-danger-text md:text-sm",
        className
      )}
      {...props}
    />
  )
}

export { Input }
