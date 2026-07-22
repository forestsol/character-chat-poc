"use client";

import * as Tabs from "@radix-ui/react-tabs";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  BookOpen, Bot, CheckCircle2, ChevronDown, CircleAlert, Database, LoaderCircle,
  FlaskConical, MessageCircle, Network, RefreshCw, RotateCcw, Search, Send, Sparkles, UserRound
} from "lucide-react";
import { FormEvent, ReactNode, useEffect, useMemo, useState } from "react";
import { api } from "@/lib/api";
import type { CharacterProfile, ChatMessage, ChatResponse, DirectRelation, RagRange } from "@/lib/types";

const profileFields: Array<[keyof CharacterProfile, string]> = [
  ["roleDescription", "이야기 속 역할"], ["appearance", "외형"], ["personality", "성격"],
  ["values", "가치관"], ["goals", "목표"], ["speechStyle", "말투"],
  ["majorExperiences", "주요 경험"], ["attitudesTowardOthers", "타인에 대한 태도"], ["knownFacts", "알고 있는 사실"]
];

export default function Home() {
  const queryClient = useQueryClient();
  const [bookId, setBookId] = useState<number | null>(null);
  const [question, setQuestion] = useState("");
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [lastResponse, setLastResponse] = useState<ChatResponse | null>(null);
  const books = useQuery({ queryKey: ["books"], queryFn: api.books });

  useEffect(() => {
    if (bookId === null && books.data?.length) setBookId(books.data[0].id);
  }, [bookId, books.data]);

  const reviews = useQuery({
    queryKey: ["reviews", bookId], queryFn: () => api.reviews(bookId!), enabled: bookId !== null
  });
  const profile = useQuery({
    queryKey: ["profile", bookId], queryFn: () => api.profile(bookId!), enabled: bookId !== null, retry: false
  });
  const kg = useQuery({
    queryKey: ["kg", bookId], queryFn: () => api.kg(bookId!), enabled: bookId !== null, retry: false
  });
  const chat = useMutation({
    mutationFn: ({ value, history }: { value: string; history: ChatMessage[] }) => api.chat(bookId!, value, history),
    onSuccess: (response, variables) => {
      setMessages((current) => [...current,
        { role: "USER", content: variables.value },
        { role: "ASSISTANT", content: response.answer, responseType: response.responseType }
      ]);
      setLastResponse(response);
      setQuestion("");
      queryClient.invalidateQueries({ queryKey: ["books"] });
    }
  });

  const selectedBook = books.data?.find((book) => book.id === bookId);
  const activeCharacter = reviews.data?.find((character) => character.chatEnabled);
  const entityNames = useMemo(() => new Map(kg.data?.entities.map((entity) => [entity.id, entity.name]) ?? []), [kg.data]);

  function submit(event: FormEvent) {
    event.preventDefault();
    const value = question.trim();
    if (value && bookId !== null && !chat.isPending) chat.mutate({ value, history: messages.slice(-6) });
  }

  function resetConversation() {
    setMessages([]);
    setLastResponse(null);
    setQuestion("");
    chat.reset();
  }

  return (
    <main className="min-h-screen px-4 py-5 md:px-8 md:py-8">
      <div className="mx-auto max-w-[1480px]">
        <header className="mb-6 flex flex-col gap-5 rounded-[2rem] border border-white/70 bg-white/70 px-6 py-5 shadow-card backdrop-blur md:flex-row md:items-center md:justify-between md:px-8">
          <div className="flex items-center gap-4">
            <div className="grid h-12 w-12 place-items-center rounded-2xl bg-ink text-paper"><BookOpen size={23} /></div>
            <div><p className="eyebrow">CHARACTER ARCHIVE</p><h1 className="font-serif text-2xl font-semibold text-ink md:text-3xl">책 속 인물과 대화하기</h1></div>
          </div>
          <div className="flex flex-wrap items-center gap-3">
            <a href="http://localhost:7860" target="_blank" rel="noreferrer" className="flex h-11 items-center gap-2 rounded-xl bg-ink px-4 text-sm font-semibold text-white"><FlaskConical size={16} /> Gradio QA</a>
            {books.isFetching && <LoaderCircle className="animate-spin text-moss" size={18} />}
            <div className="relative">
              <select className="select-field" value={bookId ?? ""} onChange={(e) => { setBookId(Number(e.target.value)); resetConversation(); }} disabled={!books.data?.length}>
                {!books.data?.length && <option value="">등록된 책 없음</option>}
                {books.data?.map((book) => <option key={book.id} value={book.id}>{book.title}</option>)}
              </select>
              <ChevronDown className="pointer-events-none absolute right-3 top-1/2 -translate-y-1/2 text-moss" size={16} />
            </div>
            <button className="icon-button" onClick={() => queryClient.invalidateQueries()} aria-label="새로고침"><RefreshCw size={18} /></button>
          </div>
        </header>

        {books.isError ? <FullError message={errorMessage(books.error)} /> : !books.isLoading && !books.data?.length ? <EmptyBooks /> : (
          <div className="grid gap-6 xl:grid-cols-[320px_minmax(0,1fr)]">
            <aside className="space-y-5">
              <section className="panel overflow-hidden">
                <div className="book-cover">
                  <span className="book-mark"><Sparkles size={18} /></span>
                  <div><p className="text-xs font-semibold uppercase tracking-[0.24em] text-white/70">Selected story</p><h2 className="mt-3 font-serif text-3xl font-semibold leading-tight">{selectedBook?.title ?? "책을 불러오는 중"}</h2><p className="mt-3 text-sm text-white/70">{selectedBook?.author || "저자 미상"}</p></div>
                </div>
                <div className="space-y-4 p-5">
                  <InfoRow label="처리 상태" value={<StatusPill status={selectedBook?.status ?? "LOADING"} />} />
                  <InfoRow label="Book key" value={<span className="font-mono text-xs">{selectedBook?.bookKey}</span>} />
                  <InfoRow label="프로필" value={`${profile.data ? "준비됨" : "확인 중"}`} />
                </div>
              </section>

              <section className="panel p-5">
                <SectionTitle icon={<UserRound size={18} />} title="등장인물 검수" count={reviews.data?.length} />
                {reviews.isLoading ? <MiniLoading /> : reviews.isError ? <InlineError message={errorMessage(reviews.error)} /> : (
                  <div className="mt-4 space-y-3">
                    {reviews.data?.map((character) => (
                      <div key={character.candidateId} className={`character-row ${character.chatEnabled ? "active-character" : ""}`}>
                        <div className="flex min-w-0 items-center gap-3"><div className="avatar">{character.name.slice(0, 1)}</div><div className="min-w-0"><p className="truncate font-semibold text-ink">{character.name}</p><p className="text-xs text-slate-500">{roleLabel(character.narrativeRole ?? character.recommendedRole)}</p></div></div>
                        <div className="flex items-center gap-2">{character.chatEnabled && <span className="chat-badge"><MessageCircle size={11} /> 대화</span>}<ReviewDot status={character.reviewStatus} /></div>
                      </div>
                    ))}
                  </div>
                )}
              </section>
            </aside>

            <section className="panel min-w-0 overflow-hidden">
              <Tabs.Root defaultValue="chat">
                <Tabs.List className="tab-list" aria-label="프로젝트 결과">
                  <Tab value="chat" icon={<MessageCircle size={17} />}>대화</Tab>
                  <Tab value="profile" icon={<UserRound size={17} />}>캐릭터 프로필</Tab>
                  <Tab value="analysis" icon={<Network size={17} />}>분석 근거</Tab>
                </Tabs.List>

                <Tabs.Content value="chat" className="tab-content focus:outline-none">
                  <div className="grid gap-6 2xl:grid-cols-[minmax(0,1.15fr)_minmax(360px,.85fr)]">
                    <div className="flex min-h-[650px] flex-col rounded-3xl bg-[#f8f6ef] p-5 md:p-7">
                      <div className="flex items-center gap-3 border-b border-black/5 pb-5">
                        <div className="avatar avatar-large">{activeCharacter?.name?.slice(0, 1) ?? "?"}</div>
                        <div><p className="font-serif text-xl font-semibold text-ink">{activeCharacter?.name ?? "대화 캐릭터 미선택"}</p><p className="text-sm text-slate-500">{profile.data?.storyPoint === "AFTER_FINAL_EVENT" ? "이야기가 끝난 직후" : "프로필을 확인하는 중"}</p></div>
                        <span className="ml-auto hidden items-center gap-1.5 text-xs font-semibold text-moss sm:flex"><span className="h-2 w-2 rounded-full bg-emerald-500" /> 근거 기반 응답</span>
                        <button type="button" onClick={resetConversation} disabled={chat.isPending || messages.length === 0}
                          className="flex items-center gap-1.5 rounded-xl border border-black/10 bg-white px-3 py-2 text-xs font-semibold text-slate-600 disabled:opacity-40">
                          <RotateCcw size={14} /> 새 대화
                        </button>
                      </div>
                      <div className="flex flex-1 flex-col justify-end gap-4 overflow-y-auto py-7">
                        {messages.length === 0 && !chat.isPending && !chat.isError && <Welcome name={activeCharacter?.name} />}
                        {messages.map((message, index) => message.role === "USER" ?
                          <div key={index} className="ml-auto max-w-[82%] rounded-[1.4rem_1.4rem_.35rem_1.4rem] bg-ink px-5 py-3.5 text-sm leading-6 text-white">{message.content}</div> :
                          <div key={index} className="max-w-[88%] rounded-[1.4rem_1.4rem_1.4rem_.35rem] bg-white px-5 py-4 shadow-sm">
                            <div className="mb-2 flex items-center gap-2 text-xs font-semibold text-moss">
                              {message.responseType === "ANSWER" ? <><CheckCircle2 size={14} /> 근거 확인됨</> : message.responseType === "CLARIFICATION" ? <><MessageCircle size={14} /> 대상 확인</> : message.responseType === "SOCIAL" ? <><MessageCircle size={14} /> 일상 대화</> : <><CircleAlert size={14} /> 확인 가능한 근거 없음</>}
                            </div><p className="leading-7 text-ink">{message.content}</p>
                          </div>)}
                        {chat.isPending && chat.variables && <div className="ml-auto max-w-[82%] rounded-[1.4rem_1.4rem_.35rem_1.4rem] bg-ink px-5 py-3.5 text-sm leading-6 text-white">{chat.variables.value}</div>}
                        {chat.isPending && <div className="flex max-w-[82%] items-center gap-3 rounded-[1.4rem_1.4rem_1.4rem_.35rem] bg-white px-5 py-4 text-sm text-slate-500 shadow-sm"><LoaderCircle className="animate-spin text-copper" size={18} /> 원문과 관계를 살펴보는 중이에요…</div>}
                        {chat.isError && <InlineError message={errorMessage(chat.error)} />}
                      </div>
                      <form onSubmit={submit} className="question-box">
                        <textarea value={question} onChange={(e) => setQuestion(e.target.value)} maxLength={1000} rows={2} placeholder={`${activeCharacter?.name ?? "캐릭터"}에게 질문해 보세요`} disabled={!activeCharacter || chat.isPending} onKeyDown={(e) => { if (e.key === "Enter" && !e.shiftKey) { e.preventDefault(); e.currentTarget.form?.requestSubmit(); } }} />
                        <button type="submit" disabled={!question.trim() || !activeCharacter || chat.isPending} aria-label="질문 보내기"><Send size={18} /></button>
                      </form>
                    </div>
                    <EvidencePanel ranges={lastResponse?.debug.ragRanges ?? []} relations={lastResponse?.debug.directRelations ?? []}
                      profileEvidence={lastResponse?.debug.profileEvidence ?? []} usedParagraphs={lastResponse?.debug.usedParagraphIds ?? []}
                      usedRelations={lastResponse?.debug.usedRelationIds ?? []} usedProfileEvidence={lastResponse?.debug.usedProfileEvidenceIds ?? []} />
                  </div>
                </Tabs.Content>

                <Tabs.Content value="profile" className="tab-content focus:outline-none">
                  <SectionTitle icon={<Bot size={19} />} title="최종 캐릭터 프로필" />
                  {profile.isLoading ? <LargeLoading /> : profile.isError ? <FullError message={errorMessage(profile.error)} compact /> : profile.data && (
                    <div className="mt-6"><div className="profile-hero"><div className="avatar avatar-xl">{activeCharacter?.name?.slice(0, 1)}</div><div><p className="eyebrow text-copper">AFTER THE FINAL EVENT</p><h2 className="mt-1 font-serif text-3xl font-semibold">{activeCharacter?.name}</h2><p className="mt-2 max-w-2xl text-sm leading-6 text-slate-600">{profile.data.roleDescription}</p></div></div>
                    <div className="mt-6 grid gap-4 md:grid-cols-2 xl:grid-cols-3">{profileFields.map(([key, label]) => <article className="profile-card" key={key}><p>{label}</p><div>{String(profile.data[key])}</div></article>)}</div>
                    <details className="mt-5 rounded-2xl border border-slate-200 bg-slate-50 p-4"><summary className="cursor-pointer text-sm font-semibold text-ink">시스템 프롬프트 초안과 프로필 근거 {profile.data.evidence.length}개</summary><p className="mt-4 whitespace-pre-wrap border-t border-slate-200 pt-4 text-sm leading-6 text-slate-600">{profile.data.systemPrompt}</p></details>
                  </div>)}
                </Tabs.Content>

                <Tabs.Content value="analysis" className="tab-content focus:outline-none">
                  <SectionTitle icon={<Database size={19} />} title="Knowledge Graph" count={kg.data?.relations.length} />
                  {kg.isLoading ? <LargeLoading /> : kg.isError ? <FullError message={errorMessage(kg.error)} compact /> : kg.data && (
                    <div className="mt-6 grid gap-6 xl:grid-cols-2"><div><h3 className="subheading">직접·간접 관계</h3><div className="mt-3 space-y-3">{kg.data.relations.map((relation) => <RelationCard key={relation.id} relation={{ id: relation.id, sourceName: entityNames.get(relation.sourceEntityId) ?? `#${relation.sourceEntityId}`, sourceType: "", relationType: relation.relationType, targetName: entityNames.get(relation.targetEntityId) ?? `#${relation.targetEntityId}`, targetType: "", description: relation.description, confidence: relation.confidence, reviewStatus: "" }} />)}</div></div><div><h3 className="subheading">사건 타임라인</h3><div className="mt-3 space-y-3">{kg.data.events.map((event) => <article key={event.id} className="event-card"><span>{event.sequenceOrder}</span><div><p className="font-semibold text-ink">{event.name}</p><p className="mt-1 text-sm leading-6 text-slate-600">{event.description}</p></div></article>)}</div></div></div>
                  )}
                </Tabs.Content>
              </Tabs.Root>
            </section>
          </div>
        )}
      </div>
    </main>
  );
}

function Tab({ value, icon, children }: { value: string; icon: ReactNode; children: ReactNode }) { return <Tabs.Trigger className="tab-trigger" value={value}>{icon}{children}</Tabs.Trigger>; }
function SectionTitle({ icon, title, count }: { icon: ReactNode; title: string; count?: number }) { return <div className="flex items-center gap-2 text-ink"><span className="text-copper">{icon}</span><h2 className="font-serif text-xl font-semibold">{title}</h2>{count !== undefined && <span className="count-badge">{count}</span>}</div>; }
function InfoRow({ label, value }: { label: string; value: ReactNode }) { return <div className="flex items-center justify-between gap-3 text-sm"><span className="text-slate-500">{label}</span><span className="text-right font-medium text-ink">{value}</span></div>; }
function StatusPill({ status }: { status: string }) { return <span className="status-pill">{status.replaceAll("_", " ")}</span>; }
function ReviewDot({ status }: { status: string }) { const good = status === "APPROVED"; return <span title={status} className={`h-2.5 w-2.5 rounded-full ${good ? "bg-emerald-500" : status === "REJECTED" ? "bg-rose-400" : "bg-amber-400"}`} />; }
function Welcome({ name }: { name?: string }) { return <div className="mx-auto max-w-md text-center"><div className="mx-auto grid h-12 w-12 place-items-center rounded-full bg-white text-copper shadow-sm"><Sparkles size={21} /></div><h3 className="mt-4 font-serif text-2xl font-semibold text-ink">{name ? `${name}와 대화할 준비가 됐어요` : "대화 캐릭터를 준비해 주세요"}</h3><p className="mt-2 text-sm leading-6 text-slate-500">답변과 함께 실제 원문 문단과 지식 관계를 확인할 수 있습니다.</p></div>; }

function EvidencePanel({ ranges, relations, profileEvidence, usedParagraphs, usedRelations, usedProfileEvidence }: { ranges: RagRange[]; relations: DirectRelation[]; profileEvidence: CharacterProfile["evidence"]; usedParagraphs: number[]; usedRelations: number[]; usedProfileEvidence: number[] }) {
  return <div className="min-w-0"><div className="mb-4 flex items-center gap-2"><Search size={18} className="text-copper" /><h2 className="font-serif text-xl font-semibold text-ink">응답 근거</h2></div><Tabs.Root defaultValue="text"><Tabs.List className="evidence-tabs"><Tabs.Trigger value="text">원문 {ranges.reduce((sum, range) => sum + range.paragraphs.length, 0)}</Tabs.Trigger><Tabs.Trigger value="kg">KG 관계 {relations.length}</Tabs.Trigger><Tabs.Trigger value="profile">프로필 {profileEvidence.length}</Tabs.Trigger></Tabs.List><Tabs.Content value="text" className="mt-4 max-h-[570px] space-y-3 overflow-y-auto pr-1 focus:outline-none">{ranges.length ? ranges.flatMap((range) => range.paragraphs).map((p) => <article key={p.paragraphId} className={`evidence-card ${usedParagraphs.includes(p.paragraphId) ? "used-evidence" : ""}`}><div className="mb-2 flex items-center justify-between text-[11px] font-semibold uppercase tracking-wider text-slate-400"><span>p.{p.pageNumber} · order {p.sourceOrder}</span>{usedParagraphs.includes(p.paragraphId) && <span className="text-copper">답변에 사용</span>}</div><p className="text-sm leading-6 text-slate-700">{p.content}</p></article>) : <EvidenceEmpty />}</Tabs.Content><Tabs.Content value="kg" className="mt-4 max-h-[570px] space-y-3 overflow-y-auto pr-1 focus:outline-none">{relations.length ? relations.map((r) => <RelationCard key={r.id} relation={r} used={usedRelations.includes(r.id)} />) : <EvidenceEmpty />}</Tabs.Content><Tabs.Content value="profile" className="mt-4 max-h-[570px] space-y-3 overflow-y-auto pr-1 focus:outline-none">{profileEvidence.length ? profileEvidence.map((evidence) => <article key={evidence.id} className={`evidence-card ${usedProfileEvidence.includes(evidence.id) ? "used-evidence" : ""}`}><div className="mb-2 flex items-center justify-between text-[11px] font-semibold uppercase tracking-wider text-slate-400"><span>{evidence.profileField} · {evidence.inferenceType}</span>{usedProfileEvidence.includes(evidence.id) && <span className="text-copper">답변에 사용</span>}</div><p className="text-sm leading-6 text-slate-700">{evidence.description}</p></article>) : <EvidenceEmpty />}</Tabs.Content></Tabs.Root></div>;
}
function RelationCard({ relation, used }: { relation: DirectRelation; used?: boolean }) { return <article className={`evidence-card ${used ? "used-evidence" : ""}`}><div className="flex flex-wrap items-center gap-2 text-sm font-semibold"><span>{relation.sourceName}</span><span className="relation-chip">{relation.relationType}</span><span>{relation.targetName}</span>{used && <CheckCircle2 className="ml-auto text-copper" size={15} />}</div><p className="mt-2 text-sm leading-6 text-slate-600">{relation.description}</p></article>; }
function EvidenceEmpty() { return <div className="rounded-2xl border border-dashed border-slate-300 px-5 py-12 text-center text-sm text-slate-500">질문을 보내면 검색된 근거가 여기에 표시됩니다.</div>; }
function MiniLoading() { return <div className="mt-4 flex items-center gap-2 text-sm text-slate-500"><LoaderCircle className="animate-spin" size={16} /> 불러오는 중</div>; }
function LargeLoading() { return <div className="grid min-h-80 place-items-center"><div className="flex items-center gap-3 text-slate-500"><LoaderCircle className="animate-spin text-copper" /> 데이터를 불러오는 중입니다.</div></div>; }
function InlineError({ message }: { message: string }) { return <div className="flex items-start gap-2 rounded-2xl bg-rose-50 p-4 text-sm text-rose-700"><CircleAlert className="mt-0.5 shrink-0" size={16} />{message}</div>; }
function FullError({ message, compact = false }: { message: string; compact?: boolean }) { return <div className={`grid place-items-center rounded-3xl border border-rose-100 bg-rose-50 text-center ${compact ? "min-h-80" : "min-h-[50vh]"}`}><div><CircleAlert className="mx-auto text-rose-500" /><p className="mt-3 font-semibold text-rose-800">데이터를 불러오지 못했습니다.</p><p className="mt-1 text-sm text-rose-600">{message}</p></div></div>; }
function EmptyBooks() { return <div className="grid min-h-[55vh] place-items-center rounded-[2rem] border border-dashed border-moss/30 bg-white/50 text-center"><div><BookOpen className="mx-auto text-moss" size={36} /><h2 className="mt-4 font-serif text-2xl font-semibold">먼저 책을 가져와 주세요</h2><p className="mt-2 text-sm text-slate-500">백엔드의 도서 가져오기 API를 실행하면 이 화면에 자동으로 나타납니다.</p></div></div>; }
function roleLabel(role?: string | null) { return ({ MAIN: "주요 인물", SUPPORTING: "조연", MINOR: "단역", UNKNOWN: "미정" } as Record<string, string>)[role ?? "UNKNOWN"] ?? role; }
function errorMessage(error: unknown) { return error instanceof Error ? error.message : "알 수 없는 오류가 발생했습니다."; }
