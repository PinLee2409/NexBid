import { accessibilityProblems, open, pagesFor, prepare, ROLES } from "./support/pages";
import { expect, test } from "./support/test";

/**
 * EN: axe on every page, for every role, in both themes. Contrast is checked per theme, since each has its own
 *     palette.
 * VI: Chạy axe trên mọi trang, với mọi vai trò, ở cả hai giao diện. Độ tương phản được kiểm theo từng giao diện,
 *     vì mỗi giao diện có bảng màu riêng.
 */
for (const theme of ["dark", "light"] as const) {
  for (const role of ROLES) {
    test(`${role} pages meet WCAG 2.1 AA in the ${theme} theme`, async ({ openContext }) => {
      test.setTimeout(120_000);
      const context = await openContext({ colorScheme: theme });
      await prepare(context, role, theme);
      const page = await context.newPage();

      const problems: string[] = [];
      for (const path of await pagesFor(role)) {
        await open(page, path);
        problems.push(...(await accessibilityProblems(page, path)));
      }
      expect(problems).toEqual([]);
    });
  }
}
