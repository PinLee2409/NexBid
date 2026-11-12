"use client";

import { usePathname, useRouter } from "next/navigation";
import { useEffect, type ReactNode } from "react";

import { Skeleton } from "@/components/ui/skeleton";
import { loginHref } from "@/lib/redirect";
import { hasRole, useSession } from "@/services/session-service";
import type { UserRole } from "@/types";

interface RoleGateProps {
  role: UserRole;
  /** EN: Where a signed-out reader signs in. / VI: Nơi người chưa đăng nhập đi đăng nhập. */
  signInPage?: "/login" | "/admin/login";
  /** EN: Shown to a signed-in reader without the role. / VI: Hiện cho người đã đăng nhập nhưng không có vai trò. */
  denied: ReactNode;
  children: ReactNode;
}

/**
 * EN: Keeps a workspace to the people it is for. The session lives in the browser, so this runs on the client:
 *     until it has been read nothing is shown, then a signed-out reader is sent to sign in and brought back,
 *     and a reader without the role sees `denied`. The backend enforces the role either way; this is about
 *     not showing a console that can only answer with errors.
 * VI: Giữ khu vực làm việc cho đúng người. Phiên nằm trong trình duyệt nên việc này chạy ở client: chưa đọc
 *     xong phiên thì chưa hiện gì, sau đó người chưa đăng nhập được đưa đi đăng nhập rồi quay lại, còn người
 *     không có vai trò thì thấy `denied`. Backend vẫn luôn kiểm tra vai trò; ở đây chỉ để không hiện một
 *     bảng điều khiển toàn lỗi.
 */
export function RoleGate({ role, denied, children, signInPage = "/login" }: RoleGateProps) {
  const { user, hydrated } = useSession();
  const router = useRouter();
  const pathname = usePathname();

  const signedOut = hydrated && !user;

  useEffect(() => {
    if (signedOut) router.replace(loginHref(`${pathname}${window.location.search}`, signInPage));
  }, [signedOut, pathname, router, signInPage]);

  if (!hydrated || !user) return <GateSkeleton />;
  if (!hasRole(user, role)) return denied;
  return children;
}

function GateSkeleton() {
  return (
    <div className="mx-auto w-full max-w-[1100px] px-4 py-10 sm:px-6 lg:px-10" aria-busy="true">
      <Skeleton className="h-10 w-64 rounded-none" />
      <Skeleton className="mt-4 h-4 w-96 max-w-full rounded-none" />
      <Skeleton className="mt-10 h-48 w-full rounded-none" />
    </div>
  );
}
