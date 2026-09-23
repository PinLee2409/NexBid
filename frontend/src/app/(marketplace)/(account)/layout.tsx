import { AccountNav } from "@/components/layout/account-nav";
import { Container } from "@/components/layout/container";

/**
 * Buyer account shell. Keeps the marketplace header and footer — the account
 * area is part of the same store, not a separate application.
 */
export default function AccountLayout({ children }: LayoutProps<"/">) {
  return (
    <Container className="py-8 lg:py-12">
      <div className="grid gap-8 lg:grid-cols-[212px_minmax(0,1fr)] lg:gap-12">
        <aside className="lg:sticky lg:top-24 lg:self-start">
          <AccountNav />
        </aside>
        <div className="min-w-0">{children}</div>
      </div>
    </Container>
  );
}
