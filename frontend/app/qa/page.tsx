"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  ArrowLeft, BookPlus, Bot, Check, CheckCircle2, CircleAlert, Clock3, Code2,
  Database, FlaskConical, ImageIcon, LoaderCircle, Network, Play, RefreshCw,
  Search, Send, Sparkles, TerminalSquare, UserCheck, Users, X
} from "lucide-react";
import Link from "next/link";
import { FormEvent, ReactNode, useEffect, useMemo, useState } from "react";
import { api } from "@/lib/api";
import type { CharacterReview } from "@/lib/types";

type StepKey = "extract" | "images" | "kg" | "recommend" | "profile" | "rag";
type RunRecord = { step: StepKey | "review" | "chat" | "import"; label: string; status: "success" | "error"; elapsedMs: number; at: string; message: string };

const stepInfo: Array<{ key: StepKey; order: number; title: string; description: string; cost: string; icon: ReactNode }> = [
  { key: "extract", order: 1, title: "등장인물 후보 추출", description: "원문을 배치 분석해 이름, 별칭과 출처 문단을 만듭니다.", cost: "OpenAI 텍스트 호출", icon: <Users size={19} /> },
  { key: "images", order: 2, title: "페이지 이미지 분석", description: "페이지 원문과 이미지를 함께 분석해 시각적 사실을 추가합니다.", cost: "페이지별 OpenAI 이미지 호출", icon: <ImageIcon size={19} /> },
  { key: "kg", order: 3, title: "사건·KG 생성", description: "사건과 개체 사이의 1단계 관계를 근거와 함께 구성합니다.", cost: "OpenAI 구조화 호출", icon: <Network size={19} /> },
  { key: "recommend", order: 4, title: "역할 추천", description: "등장인물 역할을 추천합니다. 자동 승인하지는 않습니다.", cost: "OpenAI 구조화 호출", icon: <Sparkles size={19} /> },
  { key: "profile", order: 6, title: "최종 프로필 생성", description: "승인한 대화 캐릭터의 결말 직후 프로필과 근거를 만듭니다.", cost: "OpenAI 구조화 호출", icon: <Bot size={19} /> },
  { key: "rag", order: 7, title: "RAG 색인", description: "모든 원문 문단을 임베딩해 pgvector 검색을 준비합니다.", cost: "OpenAI 임베딩 호출", icon: <Database size={19} /> }
];

export default function QaWorkbench() {
  const queryClient = useQueryClient();
  const [bookId, setBookId] = useState<number | null>(null);
  const [bookDirectory, setBookDirectory] = useState("alice-demo");
  const [running, setRunning] = useState<StepKey | "pre" | "final" | "import" | null>(null);
  const [results, setResults] = useState<Partial<Record<StepKey | "review" | "chat" | "import", unknown>>>({});
  const [logs, setLogs] = useState<RunRecord[]>([]);
  const [question, setQuestion] = useState("흰 토끼와 나는 어떤 관계였어?");

  const books = useQuery({ queryKey: ["books"], queryFn: api.books });
  useEffect(() => { if (bookId === null && books.data?.length) setBookId(books.data[0].id); }, [bookId, books.data]);
  const book = useQuery({ queryKey: ["book", bookId], queryFn: () => api.book(bookId!), enabled: bookId !== null });
  const candidates = useQuery({ queryKey: ["candidates", bookId], queryFn: () => api.candidates(bookId!), enabled: bookId !== null, retry: false });
  const reviews = useQuery({ queryKey: ["reviews", bookId], queryFn: () => api.reviews(bookId!), enabled: bookId !== null, retry: false });
  const profile = useQuery({ queryKey: ["profile", bookId], queryFn: () => api.profile(bookId!), enabled: bookId !== null, retry: false });
  const kg = useQuery({ queryKey: ["kg", bookId], queryFn: () => api.kg(bookId!), enabled: bookId !== null, retry: false });
  const activeCharacter = reviews.data?.find((item) => item.chatEnabled);
  const selectedBook = books.data?.find((item) => item.id === bookId);
  const busy = running !== null;

  async function refreshAll() {
    await queryClient.invalidateQueries();
  }

  function appendLog(record: RunRecord) { setLogs((current) => [record, ...current].slice(0, 50)); }

  async function execute<T>(step: StepKey | "review" | "chat" | "import", label: string, operation: () => Promise<T>) {
    const started = performance.now();
    try {
      const value = await operation();
      const elapsedMs = Math.round(performance.now() - started);
      setResults((current) => ({ ...current, [step]: value }));
      appendLog({ step, label, status: "success", elapsedMs, at: new Date().toLocaleTimeString("ko-KR"), message: summarize(value) });
      await refreshAll();
      return value;
    } catch (error) {
      const elapsedMs = Math.round(performance.now() - started);
      appendLog({ step, label, status: "error", elapsedMs, at: new Date().toLocaleTimeString("ko-KR"), message: errorMessage(error) });
      throw error;
    }
  }

  const operations: Record<StepKey, () => Promise<unknown>> = {
    extract: () => api.extractCandidates(bookId!), images: () => api.analyzeImages(bookId!),
    kg: () => api.buildKg(bookId!), recommend: () => api.recommendRoles(bookId!),
    profile: () => api.generateProfile(bookId!), rag: () => api.indexRag(bookId!)
  };

  async function runStep(key: StepKey) {
    if (!bookId || busy) return;
    setRunning(key);
    try { await execute(key, stepInfo.find((step) => step.key === key)!.title, operations[key]); } catch { /* log owns error */ } finally { setRunning(null); }
  }

  async function runPreReview() {
    if (!bookId || busy) return;
    setRunning("pre");
    try {
      for (const key of ["extract", "images", "kg", "recommend"] as StepKey[]) {
        await execute(key, stepInfo.find((step) => step.key === key)!.title, operations[key]);
      }
    } catch { /* stop at failing step */ } finally { setRunning(null); }
  }

  async function runFinal() {
    if (!bookId || busy || !activeCharacter) return;
    setRunning("final");
    try {
      for (const key of ["profile", "rag"] as StepKey[]) await execute(key, stepInfo.find((step) => step.key === key)!.title, operations[key]);
    } catch { /* stop at failing step */ } finally { setRunning(null); }
  }

  async function importBook(event: FormEvent) {
    event.preventDefault(); if (!bookDirectory.trim() || busy) return; setRunning("import");
    try { const value = await execute("import", "도서 가져오기", () => api.importBook(bookDirectory.trim())); setBookId(value.id); } catch { /* log owns error */ } finally { setRunning(null); }
  }

  const chat = useMutation({
    mutationFn: (value: string) => execute("chat", "대화 테스트", () => api.chat(bookId!, value)),
    onSettled: () => setRunning(null)
  });

  function submitChat(event: FormEvent) {
    event.preventDefault(); const value = question.trim(); if (!value || !bookId || busy) return;
    setRunning(null); chat.mutate(value);
  }

  const completed = useMemo(() => new Set<StepKey>([
    ...(candidates.data?.length ? ["extract" as const] : []),
    ...(kg.data?.entities?.length ? ["kg" as const] : []),
    ...(reviews.data?.some((item) => item.recommendedRole) ? ["recommend" as const] : []),
    ...(profile.data ? ["profile" as const] : []),
    ...(["IMAGES_ANALYZED", "MULTIMODAL_MERGED", "KG_BUILT", "CHARACTERS_REVIEWED", "PROFILE_GENERATED", "RAG_INDEXED", "CHAT_READY"].includes(selectedBook?.status ?? "") ? ["images" as const] : []),
    ...(["RAG_INDEXED", "CHAT_READY"].includes(selectedBook?.status ?? "") ? ["rag" as const] : [])
  ]), [candidates.data, kg.data, reviews.data, profile.data, selectedBook?.status]);

  return (
    <main className="min-h-screen px-4 py-5 md:px-8 md:py-8">
      <div className="mx-auto max-w-[1540px]">
        <header className="mb-6 rounded-[2rem] bg-[#111a15] p-6 text-white shadow-card md:p-8">
          <div className="flex flex-col gap-5 lg:flex-row lg:items-center lg:justify-between">
            <div className="flex items-center gap-4"><div className="grid h-12 w-12 place-items-center rounded-2xl bg-copper"><FlaskConical size={23} /></div><div><p className="text-[10px] font-bold tracking-[.28em] text-white/50">PIPELINE WORKBENCH</p><h1 className="mt-1 font-serif text-2xl font-semibold md:text-3xl">캐릭터 대화 QA</h1><p className="mt-1 text-sm text-white/55">각 단계를 실행하고 결과를 검수한 뒤 다음 단계로 전달합니다.</p></div></div>
            <div className="flex flex-wrap items-center gap-3"><Link href="/" className="qa-secondary-button"><ArrowLeft size={16} /> 발표 화면</Link><button onClick={refreshAll} className="qa-secondary-button"><RefreshCw size={16} /> 새로고침</button></div>
          </div>
        </header>

        <div className="grid gap-6 xl:grid-cols-[330px_minmax(0,1fr)]">
          <aside className="space-y-5">
            <section className="panel p-5">
              <QaTitle icon={<BookPlus size={18} />} title="테스트 도서" />
              <form onSubmit={importBook} className="mt-4 space-y-2"><input className="qa-input" value={bookDirectory} onChange={(e) => setBookDirectory(e.target.value)} placeholder="alice-demo" /><button className="qa-primary-button w-full" disabled={busy || !bookDirectory.trim()}>{running === "import" ? <LoaderCircle className="animate-spin" size={16} /> : <BookPlus size={16} />} 입력 디렉터리 가져오기</button></form>
              <label className="mt-5 block text-[11px] font-bold uppercase tracking-wider text-slate-400">현재 도서</label>
              <select className="qa-input mt-2" value={bookId ?? ""} onChange={(e) => { setBookId(Number(e.target.value)); setResults({}); setLogs([]); }}><option value="" disabled>도서 선택</option>{books.data?.map((item) => <option key={item.id} value={item.id}>{item.title}</option>)}</select>
              {selectedBook && <div className="mt-4 rounded-2xl bg-[#f7f4ec] p-4"><p className="font-serif text-lg font-semibold">{selectedBook.title}</p><div className="mt-3 grid grid-cols-2 gap-2 text-xs text-slate-500"><span>상태</span><b className="text-right text-moss">{selectedBook.status}</b><span>페이지</span><b className="text-right text-ink">{book.data?.pages.length ?? "-"}</b><span>문단</span><b className="text-right text-ink">{book.data?.pages.reduce((sum, page) => sum + page.paragraphs.length, 0) ?? "-"}</b><span>이미지</span><b className="text-right text-ink">{book.data?.pages.reduce((sum, page) => sum + page.images.length, 0) ?? "-"}</b></div></div>}
            </section>

            <section className="panel p-5"><QaTitle icon={<TerminalSquare size={18} />} title="실행 로그" count={logs.length} /><div className="mt-4 max-h-[520px] space-y-2 overflow-y-auto">{logs.length ? logs.map((log, index) => <div key={`${log.at}-${index}`} className="rounded-xl border border-slate-100 p-3 text-xs"><div className="flex items-center gap-2"><span className={`h-2 w-2 rounded-full ${log.status === "success" ? "bg-emerald-500" : "bg-rose-500"}`} /><b className="text-ink">{log.label}</b><span className="ml-auto text-slate-400">{formatElapsed(log.elapsedMs)}</span></div><p className={`mt-2 leading-5 ${log.status === "success" ? "text-slate-500" : "text-rose-600"}`}>{log.message}</p><p className="mt-1 text-[10px] text-slate-300">{log.at}</p></div>) : <p className="py-8 text-center text-xs text-slate-400">실행 기록이 없습니다.</p>}</div></section>
          </aside>

          <div className="min-w-0 space-y-6">
            <section className="panel p-5 md:p-7">
              <div className="flex flex-col gap-4 md:flex-row md:items-center md:justify-between"><div><QaTitle icon={<Play size={18} />} title="검수 전 자동 분석" /><p className="mt-2 text-sm text-slate-500">1~4단계를 순서대로 실행합니다. 실패하면 해당 단계에서 멈춥니다.</p></div><button className="qa-primary-button" onClick={runPreReview} disabled={!bookId || busy}>{running === "pre" ? <LoaderCircle className="animate-spin" size={16} /> : <Play size={16} />} 검수 전 단계 일괄 실행</button></div>
              <div className="mt-6 space-y-3">{stepInfo.filter((step) => step.order <= 4).map((step) => <PipelineStep key={step.key} stepKey={step.key} order={step.order} title={step.title} description={step.description} cost={step.cost} icon={step.icon} completed={completed.has(step.key)} running={running === step.key || running === "pre"} disabled={!bookId || busy} result={results[step.key]} onRun={() => runStep(step.key)} />)}</div>
            </section>

            <section className="panel p-5 md:p-7">
              <div className="flex items-start gap-3"><div className="step-number bg-amber-100 text-amber-700">5</div><div><QaTitle icon={<UserCheck size={18} />} title="사람 검수" count={reviews.data?.length} /><p className="mt-2 text-sm text-slate-500">AI 추천을 확인하고 후보를 승인·거절합니다. 대화 캐릭터는 한 명만 지정할 수 있습니다.</p></div></div>
              {reviews.isFetching ? <QaLoading text="검수 목록을 불러오는 중" /> : reviews.data?.length ? <div className="mt-6 space-y-3">{reviews.data.map((candidate) => <ReviewRow key={candidate.candidateId} bookId={bookId!} candidate={candidate} disabled={busy} onComplete={(value) => { setResults((current) => ({ ...current, review: value })); appendLog({ step: "review", label: `${candidate.name} 검수`, status: "success", elapsedMs: 0, at: new Date().toLocaleTimeString("ko-KR"), message: "검수 상태가 저장되었습니다." }); refreshAll(); }} />)}</div> : <QaEmpty text="역할 추천까지 실행하면 검수 후보가 표시됩니다." />}
              {results.review !== undefined && <JsonResult title="최근 검수 응답" value={results.review} />}
            </section>

            <section className="panel p-5 md:p-7">
              <div className="flex flex-col gap-4 md:flex-row md:items-center md:justify-between"><div><QaTitle icon={<CheckCircle2 size={18} />} title="프로필·검색 준비" /><p className="mt-2 text-sm text-slate-500">활성 캐릭터 검수 후 프로필과 RAG 색인을 순서대로 생성합니다.</p>{activeCharacter ? <p className="mt-2 text-xs font-semibold text-emerald-700">대화 캐릭터: {activeCharacter.name}</p> : <p className="mt-2 text-xs font-semibold text-amber-700">chatEnabled 캐릭터 승인이 필요합니다.</p>}</div><button className="qa-primary-button" onClick={runFinal} disabled={!bookId || busy || !activeCharacter}>{running === "final" ? <LoaderCircle className="animate-spin" size={16} /> : <Play size={16} />} 마무리 단계 일괄 실행</button></div>
              <div className="mt-6 space-y-3">{stepInfo.filter((step) => step.order >= 6).map((step) => <PipelineStep key={step.key} stepKey={step.key} order={step.order} title={step.title} description={step.description} cost={step.cost} icon={step.icon} completed={completed.has(step.key)} running={running === step.key || running === "final"} disabled={!bookId || busy || (step.key === "profile" && !activeCharacter)} result={results[step.key]} onRun={() => runStep(step.key)} />)}</div>
            </section>

            <section className="panel p-5 md:p-7">
              <QaTitle icon={<Search size={18} />} title="대화·근거 테스트" />
              <form onSubmit={submitChat} className="mt-5 flex flex-col gap-3 sm:flex-row"><input className="qa-input flex-1" value={question} onChange={(e) => setQuestion(e.target.value)} placeholder="캐릭터에게 할 질문" /><button className="qa-primary-button" disabled={!bookId || !question.trim() || chat.isPending}>{chat.isPending ? <LoaderCircle className="animate-spin" size={16} /> : <Send size={16} />} 질문 실행</button></form>
              {chat.isError && <div className="mt-4"><QaError text={errorMessage(chat.error)} /></div>}
              {chat.data && <div className="mt-5 grid gap-4 lg:grid-cols-2"><div className="rounded-2xl bg-[#f7f4ec] p-5"><div className="flex items-center gap-2 text-xs font-bold text-moss">{chat.data.grounded ? <><Check size={14} /> GROUNDED</> : <><CircleAlert size={14} /> UNKNOWN</>}</div><p className="mt-3 text-lg leading-8 text-ink">{chat.data.answer}</p></div><JsonResult title="대화 전체 응답 및 debug" value={chat.data} open /></div>}
            </section>
          </div>
        </div>
      </div>
    </main>
  );
}

function PipelineStep({ stepKey: _stepKey, order, title, description, cost, icon, completed, running, disabled, result, onRun }: { stepKey: StepKey; order: number; title: string; description: string; cost: string; icon: ReactNode; completed: boolean; running: boolean; disabled: boolean; result: unknown; onRun: () => void }) {
  return <details className="group rounded-2xl border border-slate-200 bg-white" open={running}><summary className="flex cursor-pointer list-none items-center gap-4 p-4"><div className={`step-number ${completed ? "bg-emerald-100 text-emerald-700" : "bg-slate-100 text-slate-500"}`}>{completed ? <Check size={16} /> : order}</div><div className="min-w-0 flex-1"><div className="flex items-center gap-2 font-semibold text-ink"><span className="text-copper">{icon}</span>{title}</div><p className="mt-1 truncate text-xs text-slate-500">{description}</p></div><span className="hidden rounded-full bg-orange-50 px-2.5 py-1 text-[10px] font-semibold text-copper md:inline">{cost}</span>{running && <LoaderCircle className="animate-spin text-copper" size={18} />}</summary><div className="border-t border-slate-100 p-4"><p className="text-sm leading-6 text-slate-600">{description}</p><div className="mt-4 flex flex-wrap items-center gap-3"><button className="qa-primary-button" onClick={onRun} disabled={disabled}>{running ? <LoaderCircle className="animate-spin" size={16} /> : <Play size={16} />} 이 단계 실행</button><span className="text-xs text-slate-400">재실행 시 해당 단계 데이터가 교체될 수 있습니다.</span></div>{result !== undefined && <JsonResult title="최근 API 응답" value={result} />}</div></details>;
}

function ReviewRow({ bookId, candidate, disabled, onComplete }: { bookId: number; candidate: CharacterReview; disabled: boolean; onComplete: (value: CharacterReview[]) => void }) {
  const [role, setRole] = useState(candidate.narrativeRole ?? candidate.recommendedRole ?? "SUPPORTING");
  const [chatEnabled, setChatEnabled] = useState(candidate.chatEnabled);
  const mutation = useMutation({ mutationFn: (decision: "APPROVE" | "REJECT") => api.reviewCharacter(bookId, candidate.candidateId, { decision, narrativeRole: decision === "APPROVE" ? role : null, chatEnabled: decision === "APPROVE" && chatEnabled, mergeTargetCandidateId: null }), onSuccess: onComplete });
  return <div className={`rounded-2xl border p-4 ${candidate.chatEnabled ? "border-emerald-300 bg-emerald-50/30" : "border-slate-200"}`}><div className="flex flex-col gap-4 lg:flex-row lg:items-center"><div className="min-w-0 flex-1"><div className="flex flex-wrap items-center gap-2"><h3 className="font-semibold text-ink">{candidate.name}</h3><StatusChip value={candidate.reviewStatus} /><span className="text-xs text-slate-400">신뢰도 {(candidate.confidence * 100).toFixed(0)}%</span>{candidate.chatEnabled && <span className="rounded-full bg-emerald-600 px-2 py-1 text-[10px] font-bold text-white">대화 캐릭터</span>}</div><p className="mt-2 text-sm leading-6 text-slate-500">{candidate.description}</p>{candidate.recommendationReason && <p className="mt-2 text-xs text-copper">추천: {candidate.recommendedRole} · {candidate.recommendationReason}</p>}</div><div className="flex flex-wrap items-center gap-2"><select className="qa-small-input" value={role} onChange={(e) => setRole(e.target.value)}><option value="MAIN">MAIN</option><option value="SUPPORTING">SUPPORTING</option><option value="MINOR">MINOR</option><option value="UNKNOWN">UNKNOWN</option></select><label className="flex items-center gap-2 rounded-xl border border-slate-200 px-3 py-2 text-xs font-semibold"><input type="checkbox" checked={chatEnabled} onChange={(e) => setChatEnabled(e.target.checked)} /> 대화 허용</label><button className="qa-approve-button" disabled={disabled || mutation.isPending} onClick={() => mutation.mutate("APPROVE")}><Check size={15} /> 승인</button><button className="qa-reject-button" disabled={disabled || mutation.isPending} onClick={() => mutation.mutate("REJECT")}><X size={15} /> 거절</button></div></div>{mutation.isError && <div className="mt-3"><QaError text={errorMessage(mutation.error)} /></div>}</div>;
}

function JsonResult({ title, value, open = false }: { title: string; value: unknown; open?: boolean }) { return <details className="mt-4 rounded-xl bg-[#101713] text-slate-200" open={open}><summary className="flex cursor-pointer list-none items-center gap-2 px-4 py-3 text-xs font-semibold text-emerald-300"><Code2 size={14} />{title}</summary><pre className="max-h-96 overflow-auto border-t border-white/10 p-4 text-[11px] leading-5">{JSON.stringify(value, null, 2)}</pre></details>; }
function QaTitle({ icon, title, count }: { icon: ReactNode; title: string; count?: number }) { return <div className="flex items-center gap-2"><span className="text-copper">{icon}</span><h2 className="font-serif text-xl font-semibold text-ink">{title}</h2>{count !== undefined && <span className="count-badge">{count}</span>}</div>; }
function QaLoading({ text }: { text: string }) { return <div className="mt-5 flex items-center gap-2 rounded-2xl bg-slate-50 p-5 text-sm text-slate-500"><LoaderCircle className="animate-spin" size={17} />{text}</div>; }
function QaEmpty({ text }: { text: string }) { return <div className="mt-5 rounded-2xl border border-dashed border-slate-300 p-8 text-center text-sm text-slate-400">{text}</div>; }
function QaError({ text }: { text: string }) { return <div className="flex items-start gap-2 rounded-xl bg-rose-50 p-3 text-xs leading-5 text-rose-700"><CircleAlert className="mt-0.5 shrink-0" size={15} />{text}</div>; }
function StatusChip({ value }: { value: string }) { return <span className="rounded-full bg-slate-100 px-2 py-1 text-[10px] font-bold text-slate-500">{value}</span>; }
function summarize(value: unknown) { if (Array.isArray(value)) return `${value.length}개 결과 반환`; if (value && typeof value === "object") return "정상 응답 반환"; return String(value ?? "완료"); }
function formatElapsed(ms: number) { return ms < 1000 ? `${ms}ms` : `${(ms / 1000).toFixed(1)}s`; }
function errorMessage(error: unknown) { return error instanceof Error ? error.message : "알 수 없는 오류가 발생했습니다."; }
