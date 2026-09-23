import { redirect } from "next/navigation";

/** `/seller` has no landing screen of its own — the dashboard is the entry. */
export default function SellerIndexPage() {
  redirect("/seller/dashboard");
}
