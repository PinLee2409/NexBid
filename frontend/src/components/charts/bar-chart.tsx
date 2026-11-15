"use client";

import { useState } from "react";

import { cn } from "@/lib/utils";

interface Bar {
  key: string;
  label: string;
  value: number;
}

interface BarChartProps {
  bars: Bar[];
  /** EN: How a value reads in the tooltip and on the axis. / VI: Cách một giá trị hiện trong tooltip và trên trục. */
  format: (value: number) => string;
  /** EN: Names the chart for screen readers; the page keeps a table with the same numbers. / VI: Tên biểu đồ cho trình đọc màn hình; trang vẫn giữ bảng cùng số liệu. */
  label: string;
  /** EN: Columns over time, or rows to compare. / VI: Cột theo thời gian, hoặc hàng để so sánh. */
  orientation?: "vertical" | "horizontal";
  className?: string;
}

/**
 * EN: One series of bars in the signal colour (the text variant, readable on both themes): thin marks with rounded data
 *     ends on a zero baseline, a 2px gap between bars, a recessive axis, and a tooltip on hover or focus. Single series,
 *     so no legend: the heading above names it.
 * VI: Một dãy cột màu tín hiệu (bản dùng cho chữ, đọc rõ ở cả hai giao diện): vạch mảnh, đầu dữ liệu bo tròn trên đường
 *     gốc 0, khe 2px giữa các cột, trục mờ, và tooltip khi rê chuột hoặc focus. Chỉ một dãy nên không cần chú thích:
 *     tiêu đề phía trên đã gọi tên nó.
 */
export function BarChart({ bars, format, label, orientation = "vertical", className }: BarChartProps) {
  const [active, setActive] = useState<string | null>(null);
  const max = Math.max(1, ...bars.map((bar) => bar.value));
  const shown = bars.find((bar) => bar.key === active);

  if (orientation === "horizontal") {
    return (
      <figure className={cn("relative", className)} aria-label={label}>
        <ul className="space-y-[2px]">
          {bars.map((bar) => (
            <li
              key={bar.key}
              className="grid grid-cols-[minmax(0,10rem)_1fr_auto] items-center gap-3 py-1 text-sm"
              tabIndex={0}
              onMouseEnter={() => setActive(bar.key)}
              onMouseLeave={() => setActive(null)}
              onFocus={() => setActive(bar.key)}
              onBlur={() => setActive(null)}
            >
              <span className="truncate" title={bar.label}>
                {bar.label}
              </span>
              <span className="border-line-strong relative h-4 border-l">
                <span
                  className={cn(
                    "bg-signal-text absolute inset-y-0 left-0 rounded-r-[4px] transition-opacity",
                    active && active !== bar.key ? "opacity-40" : "opacity-100",
                  )}
                  style={{ width: `${(bar.value / max) * 100}%`, minWidth: bar.value > 0 ? 2 : 0 }}
                />
              </span>
              <span className="mono-figure text-dim w-16 text-right text-xs">{format(bar.value)}</span>
            </li>
          ))}
        </ul>
      </figure>
    );
  }

  return (
    <figure className={cn("relative", className)} aria-label={label}>
      <div className="text-dim mono-figure mb-2 h-4 text-xs" aria-live="polite">
        {shown ? (
          <span>
            <span className="text-foreground">{shown.label}</span> · {format(shown.value)}
          </span>
        ) : (
          <span>{format(max)}</span>
        )}
      </div>
      <div className="border-line-strong flex h-40 items-end gap-[2px] border-b" role="list">
        {bars.map((bar) => (
          <div
            key={bar.key}
            role="listitem"
            aria-label={`${bar.label}: ${format(bar.value)}`}
            tabIndex={0}
            className="group flex h-full min-w-0 flex-1 cursor-default items-end outline-none"
            onMouseEnter={() => setActive(bar.key)}
            onMouseLeave={() => setActive(null)}
            onFocus={() => setActive(bar.key)}
            onBlur={() => setActive(null)}
          >
            <div
              className={cn(
                "bg-signal-text mx-auto w-full max-w-10 rounded-t-[4px] transition-opacity group-focus-visible:ring-2 group-focus-visible:ring-offset-2",
                active && active !== bar.key ? "opacity-40" : "opacity-100",
              )}
              style={{ height: `${(bar.value / max) * 100}%`, minHeight: bar.value > 0 ? 2 : 0 }}
            />
          </div>
        ))}
      </div>
      <div className="mt-2 flex gap-[2px]" aria-hidden="true">
        {bars.map((bar, index) => (
          <span key={bar.key} className="text-dim min-w-0 flex-1 truncate text-center text-[11px]">
            {/* EN: Every other label when crowded. / VI: Cách một nhãn khi chật. */}
            {bars.length > 8 && index % 2 === 1 ? "" : bar.label}
          </span>
        ))}
      </div>
    </figure>
  );
}
