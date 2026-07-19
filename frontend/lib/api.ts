import type { BookDetail, BookSummary, CharacterProfile, CharacterReview, ChatResponse, EntityCandidate, KnowledgeGraph } from "./types";

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
  book: (bookId: number) => request<BookDetail>(`/books/${bookId}`),
  importBook: (bookDirectory: string) => request<BookDetail>("/books/import", {
    method: "POST", body: JSON.stringify({ bookDirectory })
  }),
  candidates: (bookId: number) => request<EntityCandidate[]>(`/books/${bookId}/entity-candidates`),
  extractCandidates: (bookId: number) => request<EntityCandidate[]>(`/books/${bookId}/entity-candidates/extract`, { method: "POST" }),
  analyzeImages: (bookId: number) => request<unknown[]>(`/books/${bookId}/image-analysis/analyze`, { method: "POST" }),
  imageFacts: (bookId: number) => request<unknown[]>(`/books/${bookId}/image-analysis/facts`),
  reviews: (bookId: number) => request<CharacterReview[]>(`/books/${bookId}/character-reviews`),
  recommendRoles: (bookId: number) => request<CharacterReview[]>(`/books/${bookId}/character-reviews/recommend`, { method: "POST" }),
  reviewCharacter: (bookId: number, candidateId: number, body: {
    decision: "APPROVE" | "REJECT" | "MERGE"; narrativeRole: string | null;
    chatEnabled: boolean; mergeTargetCandidateId: number | null;
  }) => request<CharacterReview[]>(`/books/${bookId}/character-reviews/${candidateId}`, {
    method: "PUT", body: JSON.stringify(body)
  }),
  profile: (bookId: number) => request<CharacterProfile>(`/books/${bookId}/character-profile`),
  generateProfile: (bookId: number) => request<CharacterProfile>(`/books/${bookId}/character-profile/generate`, { method: "POST" }),
  kg: (bookId: number) => request<KnowledgeGraph>(`/books/${bookId}/kg`),
  buildKg: (bookId: number) => request<KnowledgeGraph>(`/books/${bookId}/kg/build`, { method: "POST" }),
  indexRag: (bookId: number) => request<unknown>(`/books/${bookId}/rag/index`, { method: "POST" }),
  searchRag: (bookId: number, query: string) => request<unknown>(`/books/${bookId}/rag/search?query=${encodeURIComponent(query)}`),
  chat: (bookId: number, question: string) => request<ChatResponse>(`/books/${bookId}/chat`, {
    method: "POST", body: JSON.stringify({ question })
  })
};
