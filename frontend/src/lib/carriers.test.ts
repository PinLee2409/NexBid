import { describe, expect, it } from "vitest";

import en from "../../messages/en.json";
import vi from "../../messages/vi.json";

import { carrierStatusKey } from "./carriers";

describe("carrierStatusKey", () => {
  it("labels GHN's status words whatever their case", () => {
    expect(carrierStatusKey("delivered")).toBe("delivered");
    expect(carrierStatusKey(" Ready_To_Pick ")).toBe("ready_to_pick");
  });

  it("folds GHN's return sub-steps into one label", () => {
    expect(carrierStatusKey("return_transporting")).toBe("return");
    expect(carrierStatusKey("returning")).toBe("return");
  });

  it("leaves words it does not know to be shown as GHN wrote them", () => {
    expect(carrierStatusKey("teleported")).toBeNull();
  });

  it("has a label in both languages for every word it knows", () => {
    const words = ["ready_to_pick", "picking", "delivering", "delivered", "delivery_fail", "return", "returned", "lost"];
    for (const word of words) {
      const key = carrierStatusKey(word)!;
      expect(en.account.carrierStatus).toHaveProperty(key);
      expect(vi.account.carrierStatus).toHaveProperty(key);
    }
  });
});
