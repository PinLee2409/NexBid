"use client";

import { useSyncExternalStore } from "react";

import { createStore } from "@/lib/create-store";

/**
 * EN: The lots picked for side-by-side comparison, at most three, kept in this browser only.
 * VI: Các lô được chọn để so sánh cạnh nhau, tối đa ba, chỉ lưu trong trình duyệt này.
 */
export const COMPARE_LIMIT = 3;
const KEY = "nexbid.compare";

const store = createStore<string[]>([]);
let loaded = false;

function load() {
  if (loaded || typeof window === "undefined") return;
  loaded = true;
  try {
    const parsed: unknown = JSON.parse(
      window.localStorage.getItem(KEY) ?? "[]",
    );
    if (Array.isArray(parsed)) {
      store.setState(
        parsed
          .filter((id): id is string => typeof id === "string")
          .slice(0, COMPARE_LIMIT),
      );
    }
  } catch {
    // EN: Storage blocked or corrupt: start empty. / VI: Bộ nhớ bị chặn hoặc hỏng: bắt đầu trống.
  }
}

function save(ids: string[]) {
  store.setState(ids);
  try {
    window.localStorage.setItem(KEY, JSON.stringify(ids));
  } catch {
    // EN: Still works for this visit. / VI: Vẫn dùng được trong lần truy cập này.
  }
}

export function useCompareList(): string[] {
  load();
  return useSyncExternalStore(
    store.subscribe,
    store.getSnapshot,
    store.getServerSnapshot,
  );
}

/** EN: Adds or removes a lot; false when the list is already full. / VI: Thêm hoặc bỏ một lô; false khi danh sách đã đầy. */
export function toggleCompare(id: string): boolean {
  load();
  const current = store.getSnapshot();
  if (current.includes(id)) {
    save(current.filter((each) => each !== id));
    return true;
  }
  if (current.length >= COMPARE_LIMIT) return false;
  save([...current, id]);
  return true;
}

export function clearCompare() {
  save([]);
}
