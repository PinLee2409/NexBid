"use client";

import { useCallback, useEffect, useRef, useState } from "react";

export type AsyncState<T> =
  | { status: "loading"; data: null; error: null }
  | { status: "success"; data: T; error: null }
  | { status: "error"; data: null; error: Error };

export interface AsyncResult<T> {
  state: AsyncState<T>;
  /** Re-runs the loader, e.g. after a mutation. */
  refresh: () => void;
}

const LOADING = { status: "loading", data: null, error: null } as const;

/**
 * Loads data from a mock service with explicit loading / success / error
 * states, so every screen can render the three UX states the spec requires.
 *
 * `key` identifies the request: change it (a tab, a filter, an id) and the
 * loader runs again. The loader itself is read from a ref, so an inline
 * closure does not re-trigger the fetch on every render.
 */
export function useAsyncData<T>(
  key: string,
  loader: () => Promise<T>,
): AsyncResult<T> {
  const loaderRef = useRef(loader);
  useEffect(() => {
    loaderRef.current = loader;
  });

  const [state, setState] = useState<AsyncState<T>>(LOADING);
  const [nonce, setNonce] = useState(0);
  const [activeKey, setActiveKey] = useState(key);

  // A new key means a different request: show loading immediately rather than
  // leaving the previous result on screen. Adjusting during render is the
  // pattern React recommends over a state-resetting effect.
  if (activeKey !== key) {
    setActiveKey(key);
    setState(LOADING);
  }

  useEffect(() => {
    let cancelled = false;

    loaderRef
      .current()
      .then((data) => {
        if (!cancelled) setState({ status: "success", data, error: null });
      })
      .catch((cause: unknown) => {
        if (cancelled) return;
        setState({
          status: "error",
          data: null,
          error: cause instanceof Error ? cause : new Error(String(cause)),
        });
      });

    return () => {
      cancelled = true;
    };
  }, [key, nonce]);

  const refresh = useCallback(() => {
    setState(LOADING);
    setNonce((value) => value + 1);
  }, []);

  return { state, refresh };
}
