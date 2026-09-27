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
    coverage: {
      provider: "v8",
      // EN: The logic layer. Components and the thin service wrappers around fetch are exercised by the
      //     Playwright suite instead.
      // VI: Tầng logic. Component và các lớp bọc mỏng quanh fetch của service được bộ test Playwright kiểm tra.
      include: ["src/lib/**", "src/services/api/**"],
      exclude: ["src/**/*.test.ts", "src/services/api/dto.ts"],
      reporter: ["text-summary", "html", "json-summary"],
      // EN: A little below today's figures: a real drop fails the build. / VI: Thấp hơn số hiện tại một chút: tụt thật sự thì build hỏng.
      thresholds: { lines: 90, statements: 90, functions: 85, branches: 80 },
    },
  },
});
