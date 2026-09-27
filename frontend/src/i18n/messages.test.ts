import { readFileSync } from "node:fs";
import path from "node:path";

import { describe, expect, it } from "vitest";

import { ERROR_CODES } from "@/types";

import en from "../../messages/en.json";
import vi from "../../messages/vi.json";

/**
 * EN: The message catalogues stay complete and in step: same keys in both languages, the same placeholders
 *     in each translation, and a message for every error the backend can send.
 * VI: Hai bộ thông điệp luôn đủ và khớp nhau: cùng các khoá ở cả hai ngôn ngữ, cùng các placeholder trong mỗi
 *     bản dịch, và có thông điệp cho mọi lỗi mà backend có thể gửi.
 */

type Tree = { [key: string]: string | Tree };

function flatten(tree: Tree, prefix = ""): Record<string, string> {
  return Object.fromEntries(
    Object.entries(tree).flatMap(([key, value]) =>
      typeof value === "string" ? [[prefix + key, value]] : Object.entries(flatten(value, `${prefix}${key}.`)),
    ),
  );
}

/** EN: The index of the brace closing the one at `open`. / VI: Vị trí dấu ngoặc đóng tương ứng với dấu mở tại `open`. */
function closing(text: string, open: number): number {
  let depth = 0;
  for (let i = open; i < text.length; i++) {
    if (text[i] === "{") depth++;
    if (text[i] === "}" && --depth === 0) return i;
  }
  throw new Error(`Unbalanced braces in ${JSON.stringify(text)}`);
}

/**
 * EN: The ICU arguments a message uses — `{name}`, `{count, plural, …}` — including those inside plural and
 *     select branches, but not the branches' own text.
 * VI: Các đối số ICU mà một thông điệp dùng — `{name}`, `{count, plural, …}` — kể cả trong các nhánh plural và
 *     select, nhưng không tính chữ của chính các nhánh.
 */
function argumentsOf(message: string, names = new Set<string>()): Set<string> {
  for (let i = 0; i < message.length; i++) {
    if (message[i] !== "{") continue;
    const end = closing(message, i);
    const [name, type, ...rest] = message.slice(i + 1, end).split(",");
    if (/^\s*[A-Za-z_]\w*\s*$/.test(name)) names.add(name.trim());
    if (type && /^\s*(plural|select|selectordinal)\s*$/.test(type)) {
      const branches = rest.join(",");
      for (let j = branches.indexOf("{"); j >= 0; j = branches.indexOf("{", closing(branches, j) + 1)) {
        argumentsOf(branches.slice(j + 1, closing(branches, j)), names);
      }
    }
    i = end;
  }
  return names;
}

const english = flatten(en as Tree);
const vietnamese = flatten(vi as Tree);

describe("the message catalogues", () => {
  it("have the same keys in English and Vietnamese", () => {
    expect(Object.keys(vietnamese).sort()).toEqual(Object.keys(english).sort());
  });

  it("have no empty messages", () => {
    const empty = [...Object.entries(english), ...Object.entries(vietnamese)].filter(([, text]) => !text.trim());
    expect(empty).toEqual([]);
  });

  it("use the same placeholders in both languages", () => {
    const mismatched = Object.keys(english)
      .filter((key) => key in vietnamese)
      .filter((key) => {
        const a = [...argumentsOf(english[key])].sort().join(",");
        const b = [...argumentsOf(vietnamese[key])].sort().join(",");
        return a !== b;
      });
    expect(mismatched).toEqual([]);
  });

  it("explain every error the backend can send, in both languages", () => {
    const missing = ERROR_CODES.flatMap((code) =>
      [
        english[`apiErrors.${code}`] ? null : `en: ${code}`,
        vietnamese[`apiErrors.${code}`] ? null : `vi: ${code}`,
      ].filter(Boolean),
    );
    expect(missing).toEqual([]);
  });
});

describe("the error codes", () => {
  it("are the backend's own list, in its order", () => {
    const source = readFileSync(
      path.resolve(__dirname, "../../../backend/src/main/java/com/nexbid/common/exception/ErrorCode.java"),
      "utf8",
    );
    const backend = [...source.matchAll(/^\s{4}([A-Z_]+)\(HttpStatus\./gm)].map((match) => match[1]);
    expect([...ERROR_CODES]).toEqual(backend);
  });
});

describe("reading placeholders", () => {
  it("finds plain, plural and nested arguments but not branch text", () => {
    expect([...argumentsOf("Hi {name}")]).toEqual(["name"]);
    expect([...argumentsOf("{count, plural, one {# bid by {who}} other {# bids}}")].sort()).toEqual(["count", "who"]);
    expect([...argumentsOf("{status, select, ACTIVE {Live} other {Closed}}")]).toEqual(["status"]);
  });
});
