"use client";

import { domAnimation, LazyMotion } from "framer-motion";
import type { ReactNode } from "react";

/**
 * EN: The app animates opacity and position, with enter and exit; it never drags or animates layout. So it
 *     loads framer-motion's smaller `domAnimation` feature set and uses the light `m` components. `strict`
 *     makes a full `motion` component, which would pull the whole library back in, an error.
 * VI: App chỉ làm động độ mờ và vị trí, lúc xuất hiện và lúc biến mất; không kéo thả, không làm động bố cục. Nên
 *     nó chỉ tải bộ tính năng nhỏ `domAnimation` của framer-motion và dùng các component nhẹ `m`. `strict` biến
 *     việc dùng component `motion` đầy đủ, thứ sẽ kéo cả thư viện về lại, thành lỗi.
 */
export function MotionProvider({ children }: { children: ReactNode }) {
  return (
    <LazyMotion features={domAnimation} strict>
      {children}
    </LazyMotion>
  );
}
