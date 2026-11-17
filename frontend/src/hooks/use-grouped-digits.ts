"use client";

import type { ChangeEvent } from "react";

import { useMoney } from "@/hooks/use-money";

/**
 * EN: A money field that shows its digits grouped the reader's way while they type — `12.600.000` in Vietnamese,
 *     `12,600,000` in English — but hands back only the digits. The caret stays after the same digit it was after, so
 *     typing or deleting in the middle does not throw it to the end.
 * VI: Ô nhập tiền hiển thị chữ số theo nhóm hàng nghìn kiểu của người đọc ngay khi gõ — `12.600.000` khi tiếng Việt,
 *     `12,600,000` khi tiếng Anh — nhưng chỉ trả về các chữ số. Con trỏ vẫn đứng sau đúng chữ số nó đứng trước đó, nên gõ
 *     hay xoá ở giữa không làm nó nhảy về cuối.
 */
export function useGroupedDigits(digits: string, onDigits: (digits: string) => void) {
  const money = useMoney();
  const group = (raw: string) => (raw === "" ? "" : money.amount(Number(raw)));

  function onChange(event: ChangeEvent<HTMLInputElement>) {
    const input = event.target;
    const typed = input.value;
    const caret = input.selectionStart ?? typed.length;
    const digitsBeforeCaret = typed.slice(0, caret).replace(/\D/g, "").length;
    // EN: 15 digits is far beyond any price and still exact as a number. / VI: 15 chữ số vượt xa mọi mức giá mà vẫn chính xác.
    const next = typed.replace(/\D/g, "").replace(/^0+(?=\d)/, "").slice(0, 15);
    onDigits(next);

    const shown = group(next);
    let position = 0;
    for (let seen = 0; position < shown.length && seen < digitsBeforeCaret; position++) {
      if (/\d/.test(shown[position]!)) seen++;
    }
    requestAnimationFrame(() => {
      if (document.activeElement === input) input.setSelectionRange(position, position);
    });
  }

  return { value: group(digits.replace(/\D/g, "")), onChange };
}
