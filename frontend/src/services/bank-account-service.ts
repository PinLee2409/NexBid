"use client";

import { api } from "./api/http";

/**
 * EN: Where NexBid sends someone money: payouts for a seller, refunds for a buyer. One per account.
 * VI: Nơi NexBid chuyển tiền cho một người: tiền bán hàng của người bán, tiền hoàn của người mua. Mỗi tài khoản một cái.
 */
export interface BankAccount {
  bankName: string;
  accountNumber: string;
  accountHolder: string;
}

/** `GET /api/users/me/bank-account` — null until the first one is saved. */
export async function getMyBankAccount(): Promise<BankAccount | null> {
  return (await api<BankAccount | null>("/api/users/me/bank-account")) ?? null;
}

/** `PUT /api/users/me/bank-account` — the server keeps digits only and the holder in capitals. */
export async function saveMyBankAccount(account: BankAccount): Promise<BankAccount> {
  return api<BankAccount>("/api/users/me/bank-account", { method: "PUT", body: account });
}
