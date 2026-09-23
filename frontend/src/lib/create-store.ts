/**
 * Minimal observable store used for the few pieces of cross-component client
 * state NexBid needs (session, watchlist, live auction rooms).
 *
 * It is deliberately tiny: `useSyncExternalStore` handles hydration correctly
 * when a server snapshot is supplied, so no state library is required.
 */
export interface Store<T> {
  getSnapshot: () => T;
  getServerSnapshot: () => T;
  setState: (updater: T | ((previous: T) => T)) => void;
  subscribe: (listener: () => void) => () => void;
}

export function createStore<T>(initialState: T): Store<T> {
  let state = initialState;
  const serverState = initialState;
  const listeners = new Set<() => void>();

  return {
    getSnapshot: () => state,
    getServerSnapshot: () => serverState,
    setState(updater) {
      const next =
        typeof updater === "function"
          ? (updater as (previous: T) => T)(state)
          : updater;
      if (Object.is(next, state)) return;
      state = next;
      for (const listener of listeners) listener();
    },
    subscribe(listener) {
      listeners.add(listener);
      return () => {
        listeners.delete(listener);
      };
    },
  };
}
