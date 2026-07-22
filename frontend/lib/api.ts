import type { BookSummary, CharacterProfile, CharacterReview, ChatMessage, ChatResponse, KnowledgeGraph } from "./types";

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(`/backend${path}`, {
    ...init,
    headers: { "Content-Type": "application/json", ...init?.headers }
  });
  if (!response.ok) {
    const body = await response.json().catch(() => null) as { message?: string } | null;
    throw new Error(body?.message ?? `요청에 실패했습니다. (${response.status})`);
  }
  return response.json() as Promise<T>;
}

export const api = {
  books: () => request<BookSummary[]>("/books"),
  reviews: (bookId: number) => request<CharacterReview[]>(`/books/${bookId}/character-reviews`),
  profile: (bookId: number) => request<CharacterProfile>(`/books/${bookId}/character-profile`),
  kg: (bookId: number) => request<KnowledgeGraph>(`/books/${bookId}/kg`),
  chat: (bookId: number, question: string, history: ChatMessage[] = []) => request<ChatResponse>(`/books/${bookId}/chat`, {
    method: "POST", body: JSON.stringify({ question, history: history.map(({ role, content }) => ({ role, content })) })
  })
};
