import { grantRoles, register, resetSignInLimits } from "./support/stack";
import type { E2EData } from "./support/test";

/**
 * EN: Creates this run's accounts and hands them to the tests through E2E_DATA. Lots are made by each test,
 *     so a retry starts clean. A run id in every email keeps runs apart on the same database.
 * VI: Tạo tài khoản cho lần chạy này rồi đưa cho các test qua E2E_DATA. Lô do từng test tự tạo, nên lần chạy
 *     lại bắt đầu sạch. Mã lần chạy trong mọi email giữ các lần chạy tách biệt trên cùng database.
 */
export default async function globalSetup(): Promise<void> {
  resetSignInLimits();
  const run = Date.now().toString(36);
  const email = (who: string) => `e2e-${run}-${who}@nexbid.test`;
  const users = {
    seller: email("seller"),
    admin: email("admin"),
    alex: email("alex"),
    sara: email("sara"),
  };

  await register(users.seller, "E2E Seller");
  await register(users.admin, "E2E Admin");
  await register(users.alex, "Alex E2E");
  await register(users.sara, "Sara E2E");
  grantRoles([
    [users.seller, "SELLER"],
    [users.admin, "ADMIN"],
  ]);

  const data: E2EData = { run, users };
  process.env.E2E_DATA = JSON.stringify(data);
}
