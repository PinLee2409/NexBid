"use client";

import { useSyncExternalStore } from "react";

import { createStore } from "@/lib/create-store";
import { exchangeRates } from "@/services/market-service";

/**
 * EN: The reference currency a reader wants prices shown in beside the đồng, kept in this browser only, and the rates
 *     to convert with — fetched once per visit. Display only: every payment stays in VND.
 * VI: Loại tiền tham khảo người đọc muốn thấy giá bên cạnh tiền đồng, chỉ lưu trong trình duyệt này, và tỷ giá để quy
 *     đổi — lấy một lần mỗi lượt truy cập. Chỉ để hiển thị: mọi khoản thanh toán vẫn bằng VND.
 */
export const FX_CHOICES = ["USD", "EUR", "off"] as const;
export type FxChoice = (typeof FX_CHOICES)[number];

const KEY = "nexbid.fx";

interface FxState {
  choice: FxChoice;
  /** EN: Đồng per unit; null until loaded. / VI: Số đồng cho một đơn vị; null cho tới khi tải xong. */
  rates: Record<string, number> | null;
  updatedAt: string | null;
}

const store = createStore<FxState>({ choice: "USD", rates: null, updatedAt: null });
let started = false;

function start() {
  if (started || typeof window === "undefined") return;
  started = true;
  try {
    const saved = window.localStorage.getItem(KEY);
    if (saved && (FX_CHOICES as readonly string[]).includes(saved)) {
      store.setState((state) => ({ ...state, choice: saved as FxChoice }));
    }
  } catch {
    // EN: Storage blocked: keep the default. / VI: Bộ nhớ bị chặn: giữ mặc định.
  }
  exchangeRates()
    .then((answer) => {
      const rates = Object.fromEntries(answer.rates.map((rate) => [rate.currency, Number(rate.vndPer)]));
      store.setState((state) => ({ ...state, rates, updatedAt: answer.updatedAt }));
    })
    .catch(() => {
      // EN: No rates, no hints; prices in đồng are unaffected. / VI: Không có tỷ giá thì không có gợi ý; giá bằng đồng không bị ảnh hưởng.
      started = false;
    });
}

export function useFx(): FxState {
  start();
  return useSyncExternalStore(store.subscribe, store.getSnapshot, store.getServerSnapshot);
}

export function setFxChoice(choice: FxChoice) {
  store.setState((state) => ({ ...state, choice }));
  try {
    window.localStorage.setItem(KEY, choice);
  } catch {
    // EN: Still applies for this visit. / VI: Vẫn áp dụng trong lần truy cập này.
  }
}

/** EN: The amount in the chosen currency, or null when off or not loaded. / VI: Số tiền theo loại đã chọn, hoặc null khi tắt hay chưa tải. */
export function convert(state: FxState, vnd: number): { currency: string; value: number } | null {
  if (state.choice === "off" || !state.rates) return null;
  const per = state.rates[state.choice];
  if (!per || per <= 0) return null;
  return { currency: state.choice, value: vnd / per };
}
