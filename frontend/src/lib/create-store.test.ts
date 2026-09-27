import { describe, expect, it, vi } from "vitest";

import { createStore } from "./create-store";

describe("the client store", () => {
  it("tells its listeners about a new state, from a value or from an updater", () => {
    const store = createStore({ count: 0 });
    const listener = vi.fn();
    store.subscribe(listener);

    store.setState({ count: 1 });
    store.setState((previous) => ({ count: previous.count + 1 }));

    expect(store.getSnapshot()).toEqual({ count: 2 });
    expect(listener).toHaveBeenCalledTimes(2);
  });

  it("stays quiet when the state does not change", () => {
    const state = { count: 0 };
    const store = createStore(state);
    const listener = vi.fn();
    store.subscribe(listener);

    store.setState(state);
    store.setState((previous) => previous);

    expect(listener).not.toHaveBeenCalled();
  });

  it("stops telling a listener once it unsubscribes", () => {
    const store = createStore(0);
    const listener = vi.fn();
    const unsubscribe = store.subscribe(listener);

    unsubscribe();
    store.setState(1);

    expect(listener).not.toHaveBeenCalled();
  });

  it("keeps rendering on the server from the first state, so hydration matches", () => {
    const store = createStore("first");
    store.setState("later");

    expect(store.getServerSnapshot()).toBe("first");
    expect(store.getSnapshot()).toBe("later");
  });
});
