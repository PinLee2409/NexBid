import * as React from "react"
import { cva, type VariantProps } from "class-variance-authority"
import { cn } from "cn"
import { Slot } from "radix-ui"

/*
 * shadcn's behaviour, NexBid's appearance. Buttons here are square, set in
 * wide micro-caps, and carry no shadow — the same rule-and-type language as
 * the rest of the system. `default` is the signal fill and should stay rare:
 * one committing action per screen.
 */
const buttonVariants = cva(
  "group/button label inline-flex shrink-0 items-center justify-center border border-transparent whitespace-nowrap transition-colors outline-none select-none focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-signal-text disabled:pointer-events-none disabled:opacity-40 aria-invalid:border-danger-text [&_svg]:pointer-events-none [&_svg]:shrink-0 [&_svg:not([class*='size-'])]:size-4",
  {
    variants: {
      variant: {
        default:
          "bg-signal text-signal-ink hover:bg-foreground hover:text-background",
        outline:
          "border-line text-foreground hover:border-foreground aria-expanded:border-foreground",
        secondary:
          "bg-surface-2 text-foreground hover:bg-surface aria-expanded:bg-surface",
        ghost:
          "text-muted-foreground hover:text-foreground aria-expanded:text-foreground",
        // Quiet until you reach for it: a wall of filled red in a list reads
        // as alarm, and red here is reserved for things that actually cost.
        destructive:
          "border-danger-text/40 text-danger-text hover:bg-danger hover:border-danger hover:text-danger-ink",
        link: "text-signal-text underline-offset-4 hover:underline",
      },
      size: {
        default: "h-10 gap-2 px-4",
        xs: "h-7 gap-1.5 px-2 text-[10px] [&_svg:not([class*='size-'])]:size-3",
        sm: "h-8 gap-1.5 px-3 text-[10px] [&_svg:not([class*='size-'])]:size-3.5",
        lg: "h-14 gap-3 px-8",
        icon: "size-10",
        "icon-xs": "size-7 [&_svg:not([class*='size-'])]:size-3",
        "icon-sm": "size-8 [&_svg:not([class*='size-'])]:size-3.5",
        "icon-lg": "size-12",
      },
    },
    defaultVariants: {
      variant: "default",
      size: "default",
    },
  }
)

function Button({
  className,
  variant = "default",
  size = "default",
  asChild = false,
  ...props
}: React.ComponentProps<"button"> &
  VariantProps<typeof buttonVariants> & {
    asChild?: boolean
  }) {
  const Comp = asChild ? Slot.Root : "button"

  return (
    <Comp
      data-slot="button"
      data-variant={variant}
      data-size={size}
      className={cn(buttonVariants({ variant, size, className }))}
      {...props}
    />
  )
}

export { Button, buttonVariants }
