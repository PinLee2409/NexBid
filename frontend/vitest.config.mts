import tsconfigPaths from "vite-tsconfig-paths";
import { defineConfig } from "vitest/config";

/**
 * EN: Unit tests for the frontend's own logic. They sit next to the code as `*.test.ts`; `e2e/` belongs to
 *     Playwright. Node by default; a file that needs the browser's window opts into jsdom itself.
 * VI: Unit test cho phần logic riêng của frontend. Chúng nằm cạnh code dưới dạng `*.test.ts`; `e2e/` thuộc về
 *     Playwright. Mặc định chạy trên Node; file nào cần window của trình duyệt thì tự chọn jsdom.
 */
export default defineConfig({
  plugins: [tsconfigPaths()],
  test: {
    include: ["src/**/*.test.ts"],
    environment: "node",
  },
});
