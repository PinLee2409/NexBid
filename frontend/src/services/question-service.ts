import type { IsoDateString } from "@/types";

import { api } from "./api/http";

/**
 * EN: Public questions on a lot. Reading works on the server (the lot page) and in the browser; asking and answering
 *     need a session.
 * VI: Câu hỏi công khai trên một lô. Đọc được cả ở server (trang lô) lẫn trình duyệt; hỏi và trả lời cần đăng nhập.
 */
export interface LotQuestion {
  id: string;
  auctionId: string;
  /** EN: Masked like a bidder, "lin***". / VI: Được che như người trả giá, "lin***". */
  asker: string;
  body: string;
  answer: string | null;
  createdAt: IsoDateString;
  answeredAt: IsoDateString | null;
}

function toQuestion(question: LotQuestion): LotQuestion {
  return { ...question, answer: question.answer ?? null, answeredAt: question.answeredAt ?? null };
}

/** `GET /api/auctions/{id}/questions` — newest first. Empty when the lot cannot be read. */
export async function listQuestions(auctionId: string): Promise<LotQuestion[]> {
  try {
    return (await api<LotQuestion[]>(`/api/auctions/${auctionId}/questions`)).map(toQuestion);
  } catch {
    return [];
  }
}

/** `POST /api/auctions/{id}/questions` */
export async function askQuestion(auctionId: string, body: string): Promise<LotQuestion> {
  return toQuestion(
    await api<LotQuestion>(`/api/auctions/${auctionId}/questions`, { method: "POST", body: { body: body.trim() } }),
  );
}

/** `POST /api/seller/questions/{id}/answer` — the lot's seller, once. */
export async function answerQuestion(questionId: string, answer: string): Promise<LotQuestion> {
  return toQuestion(
    await api<LotQuestion>(`/api/seller/questions/${questionId}/answer`, {
      method: "POST",
      body: { answer: answer.trim() },
    }),
  );
}
