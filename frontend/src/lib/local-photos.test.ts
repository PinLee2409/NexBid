import { describe, expect, it } from "vitest";

import { isLocalImage } from "./images";
import { forgetLocalFile, isLocalFileUrl, localFileFor, rememberLocalFile } from "./local-files";

describe("photos still on the seller's device", () => {
  it("are told apart from uploaded ones by their URL", () => {
    expect(isLocalFileUrl("blob:http://localhost:3000/1f2e")).toBe(true);
    expect(isLocalFileUrl("/media/products/p1.jpg")).toBe(false);
    // EN: The image optimizer cannot fetch these, so they are shown as they are. / VI: Bộ tối ưu ảnh không tải được chúng, nên hiển thị nguyên trạng.
    expect(isLocalImage("blob:http://localhost:3000/1f2e")).toBe(true);
    expect(isLocalImage("data:image/png;base64,AAAA")).toBe(true);
    expect(isLocalImage("/media/products/p1.jpg")).toBe(false);
  });

  it("remember the file behind each preview until it is saved or dropped", () => {
    const file = new File(["x"], "watch.jpg", { type: "image/jpeg" });
    rememberLocalFile("blob:one", file);
    expect(localFileFor("blob:one")).toBe(file);

    forgetLocalFile("blob:one");
    expect(localFileFor("blob:one")).toBeUndefined();
  });
});
