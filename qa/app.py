from __future__ import annotations

import json
import os
import time
from typing import Any, Callable

import gradio as gr
import httpx


DEFAULT_BACKEND_URL = os.getenv("BACKEND_URL", "http://localhost:8081")
REQUEST_TIMEOUT = float(os.getenv("QA_REQUEST_TIMEOUT_SECONDS", "900"))
QA_CSS = """
.gradio-container { max-width: 1440px !important; }
.qa-header { padding: 18px 22px; border: 1px solid var(--border-color-primary); border-radius: 14px; }
.cost-note { color: #b45309; font-size: 12px; }
.status-box { min-height: 44px; }
"""


class BackendApi:
    def __init__(self, base_url: str):
        self.base_url = base_url.rstrip("/")

    def request(self, method: str, path: str, payload: dict[str, Any] | None = None) -> Any:
        try:
            with httpx.Client(timeout=REQUEST_TIMEOUT) as client:
                response = client.request(method, f"{self.base_url}/api{path}", json=payload)
        except httpx.HTTPError as exc:
            raise RuntimeError(f"백엔드 연결 실패: {exc}") from exc
        if response.is_error:
            try:
                message = response.json().get("message") or response.text
            except (ValueError, AttributeError):
                message = response.text
            raise RuntimeError(f"HTTP {response.status_code}: {message}")
        if not response.content:
            return None
        return response.json()

    def get(self, path: str) -> Any:
        return self.request("GET", path)

    def post(self, path: str, payload: dict[str, Any] | None = None) -> Any:
        return self.request("POST", path, payload)

    def put(self, path: str, payload: dict[str, Any]) -> Any:
        return self.request("PUT", path, payload)


def _api(base_url: str) -> BackendApi:
    if not base_url or not base_url.strip():
        raise gr.Error("백엔드 URL을 입력하세요.")
    return BackendApi(base_url.strip())


def _book_id(value: Any) -> int:
    if value in (None, ""):
        raise gr.Error("테스트할 도서를 선택하세요.")
    return int(value)


def _elapsed(started: float) -> str:
    return f"{time.perf_counter() - started:.2f}s"


def _ok(label: str, elapsed: str, result: Any) -> str:
    count = len(result) if isinstance(result, list) else None
    suffix = f" · {count}개 결과" if count is not None else ""
    return f"✅ **{label} 완료** · {elapsed}{suffix}"


def _run(label: str, operation: Callable[[], Any]) -> tuple[str, Any, str]:
    started = time.perf_counter()
    try:
        result = operation()
        elapsed = _elapsed(started)
        return _ok(label, elapsed, result), result, elapsed
    except Exception as exc:
        elapsed = _elapsed(started)
        return f"❌ **{label} 실패** · {elapsed}\n\n{exc}", {"error": str(exc)}, elapsed


def load_books(base_url: str, selected: Any = None):
    try:
        books = _api(base_url).get("/books")
        choices = [(f"{book['title']} · {book['status']} · ID {book['id']}", book["id"]) for book in books]
        ids = {book["id"] for book in books}
        value = int(selected) if selected not in (None, "") and int(selected) in ids else (books[0]["id"] if books else None)
        status = f"✅ 백엔드 연결 정상 · 도서 {len(books)}권"
        return gr.Dropdown(choices=choices, value=value), status, books
    except Exception as exc:
        return gr.Dropdown(choices=[], value=None), f"❌ {exc}", {"error": str(exc)}


def import_book(base_url: str, directory: str):
    if not directory or not directory.strip():
        raise gr.Error("입력 디렉터리 이름을 입력하세요.")
    status, result, elapsed = _run("도서 가져오기", lambda: _api(base_url).post("/books/import", {"bookDirectory": directory.strip()}))
    dropdown, connection, books = load_books(base_url, result.get("id") if isinstance(result, dict) else None)
    return status, result, elapsed, dropdown, connection, books


def load_book(base_url: str, book_id: Any):
    if book_id in (None, ""):
        return "도서를 선택하세요.", None
    try:
        book = _api(base_url).get(f"/books/{int(book_id)}")
        pages = book.get("pages", [])
        paragraph_count = sum(len(page.get("paragraphs", [])) for page in pages)
        image_count = sum(len(page.get("images", [])) for page in pages)
        summary = (
            f"### {book['title']}\n"
            f"- 상태: `{book['status']}`\n"
            f"- Book key: `{book['bookKey']}`\n"
            f"- 페이지/문단/이미지: **{len(pages)} / {paragraph_count} / {image_count}**"
        )
        return summary, book
    except Exception as exc:
        return f"❌ {exc}", {"error": str(exc)}


STEP_ENDPOINTS = {
    "등장인물 후보 추출": "/books/{book_id}/entity-candidates/extract",
    "페이지 이미지 분석": "/books/{book_id}/image-analysis/analyze",
    "사건·KG 생성": "/books/{book_id}/kg/build",
    "등장인물 역할 추천": "/books/{book_id}/character-reviews/recommend",
    "최종 캐릭터 프로필": "/books/{book_id}/character-profile/generate",
    "RAG 임베딩 색인": "/books/{book_id}/rag/index",
}


def run_step(base_url: str, book_id: Any, label: str):
    resolved_id = _book_id(book_id)
    endpoint = STEP_ENDPOINTS[label].format(book_id=resolved_id)
    return _run(label, lambda: _api(base_url).post(endpoint))


def run_pre_review(base_url: str, book_id: Any, progress=gr.Progress()):
    resolved_id = _book_id(book_id)
    labels = ["등장인물 후보 추출", "페이지 이미지 분석", "사건·KG 생성", "등장인물 역할 추천"]
    started = time.perf_counter()
    results: dict[str, Any] = {}
    for index, label in enumerate(labels):
        progress(index / len(labels), desc=f"{index + 1}/{len(labels)} {label}")
        try:
            endpoint = STEP_ENDPOINTS[label].format(book_id=resolved_id)
            results[label] = _api(base_url).post(endpoint)
        except Exception as exc:
            elapsed = _elapsed(started)
            results[label] = {"error": str(exc)}
            return f"❌ **{label}에서 중단** · 전체 {elapsed}\n\n{exc}", results, elapsed
    progress(1, desc="검수 전 분석 완료")
    elapsed = _elapsed(started)
    return f"✅ **검수 전 4단계 완료** · 전체 {elapsed}\n\n아래 사람 검수에서 후보를 승인하거나 거절하세요.", results, elapsed


def load_reviews(base_url: str, book_id: Any):
    if book_id in (None, ""):
        empty = gr.Dropdown(choices=[], value=None)
        return empty, empty, [], "", "SUPPORTING", False, None, "도서를 선택하세요."
    try:
        reviews = _api(base_url).get(f"/books/{int(book_id)}/character-reviews")
        choices = [(f"{item['name']} · {item['reviewStatus']} · 추천 {item.get('recommendedRole') or '-'}", item["candidateId"]) for item in reviews]
        first = reviews[0] if reviews else None
        candidate_value = first["candidateId"] if first else None
        target_choices = [(item["name"], item["candidateId"]) for item in reviews]
        details, role, chat = _review_details(first)
        return (
            gr.Dropdown(choices=choices, value=candidate_value),
            gr.Dropdown(choices=target_choices, value=None),
            reviews, details, role, chat, reviews,
            f"✅ 검수 후보 {len(reviews)}명 조회",
        )
    except Exception as exc:
        empty = gr.Dropdown(choices=[], value=None)
        return empty, empty, [], "", "SUPPORTING", False, {"error": str(exc)}, f"❌ {exc}"


def _review_details(item: dict[str, Any] | None) -> tuple[str, str, bool]:
    if not item:
        return "검수할 후보가 없습니다.", "SUPPORTING", False
    details = (
        f"### {item['name']}\n"
        f"{item.get('description') or '설명 없음'}\n\n"
        f"- 상태: `{item['reviewStatus']}` · 신뢰도: **{item['confidence']:.1%}**\n"
        f"- 출처: `{item.get('originSource') or '-'}`\n"
        f"- 추천 역할: `{item.get('recommendedRole') or '-'}`\n"
        f"- 추천 이유: {item.get('recommendationReason') or '-'}"
    )
    role = item.get("narrativeRole") or item.get("recommendedRole") or "SUPPORTING"
    return details, role, bool(item.get("chatEnabled"))


def select_candidate(candidate_id: Any, reviews: list[dict[str, Any]] | None):
    item = next((review for review in (reviews or []) if review["candidateId"] == int(candidate_id)), None) if candidate_id else None
    details, role, chat = _review_details(item)
    return details, role, chat


def review_candidate(base_url: str, book_id: Any, candidate_id: Any, decision: str, role: str, chat_enabled: bool, merge_target: Any):
    resolved_id = _book_id(book_id)
    if candidate_id in (None, ""):
        raise gr.Error("검수할 후보를 선택하세요.")
    if decision == "MERGE" and merge_target in (None, ""):
        raise gr.Error("병합 대상 후보를 선택하세요.")
    payload = {
        "decision": decision,
        "narrativeRole": role if decision == "APPROVE" else None,
        "chatEnabled": bool(chat_enabled) if decision == "APPROVE" else False,
        "mergeTargetCandidateId": int(merge_target) if decision == "MERGE" else None,
    }
    label = {"APPROVE": "후보 승인", "REJECT": "후보 거절", "MERGE": "후보 병합"}[decision]
    status, result, elapsed = _run(label, lambda: _api(base_url).put(f"/books/{resolved_id}/character-reviews/{int(candidate_id)}", payload))
    loaded = load_reviews(base_url, resolved_id)
    return status, result, elapsed, *loaded


def run_final(base_url: str, book_id: Any, progress=gr.Progress()):
    resolved_id = _book_id(book_id)
    started = time.perf_counter()
    results: dict[str, Any] = {}
    for index, label in enumerate(["최종 캐릭터 프로필", "RAG 임베딩 색인"]):
        progress(index / 2, desc=label)
        try:
            results[label] = _api(base_url).post(STEP_ENDPOINTS[label].format(book_id=resolved_id))
        except Exception as exc:
            elapsed = _elapsed(started)
            results[label] = {"error": str(exc)}
            return f"❌ **{label}에서 중단** · {elapsed}\n\n{exc}", results, elapsed
    progress(1, desc="대화 준비 완료")
    elapsed = _elapsed(started)
    return f"✅ **프로필과 RAG 준비 완료** · {elapsed}", results, elapsed


def test_rag(base_url: str, book_id: Any, question: str):
    resolved_id = _book_id(book_id)
    if not question or not question.strip():
        raise gr.Error("검색 질문을 입력하세요.")
    started = time.perf_counter()
    try:
        with httpx.Client(timeout=REQUEST_TIMEOUT) as client:
            response = client.get(
                f"{base_url.rstrip('/')}/api/books/{resolved_id}/rag/search",
                params={"query": question.strip()},
            )
        if response.is_error:
            raise RuntimeError(f"HTTP {response.status_code}: {response.text}")
        result = response.json()
        return _ok("RAG 검색", _elapsed(started), result), result, _elapsed(started)
    except Exception as exc:
        return f"❌ **RAG 검색 실패** · {_elapsed(started)}\n\n{exc}", {"error": str(exc)}, _elapsed(started)


def test_chat(base_url: str, book_id: Any, question: str):
    resolved_id = _book_id(book_id)
    if not question or not question.strip():
        raise gr.Error("캐릭터에게 할 질문을 입력하세요.")
    started = time.perf_counter()
    try:
        result = _api(base_url).post(f"/books/{resolved_id}/chat", {"question": question.strip()})
        elapsed = _elapsed(started)
        grounded = "✅ GROUNDED" if result.get("grounded") else "⚠️ UNKNOWN"
        debug = result.get("debug") or {}
        return (
            f"### {result.get('character', {}).get('name', '캐릭터')}\n\n{result.get('answer', '')}",
            grounded,
            debug.get("ragRanges", []),
            debug.get("directRelations", []),
            result,
            _ok("캐릭터 대화", elapsed, result),
            elapsed,
        )
    except Exception as exc:
        elapsed = _elapsed(started)
        error = {"error": str(exc)}
        return f"### 오류\n\n{exc}", "❌ ERROR", [], [], error, f"❌ **대화 실패** · {elapsed}\n\n{exc}", elapsed


def build_ui() -> gr.Blocks:
    with gr.Blocks(title="Character Chat QA") as demo:
        gr.Markdown("# 🔬 캐릭터 대화 파이프라인 QA", elem_classes=["qa-header"])
        gr.Markdown("각 단계를 독립 실행하고 원본 응답을 확인한 뒤, 사람 검수를 거쳐 다음 단계로 전달합니다. **실행 버튼을 누른 AI 단계에는 OpenAI 비용이 발생합니다.**")

        reviews_state = gr.State([])

        with gr.Row():
            backend_url = gr.Textbox(value=DEFAULT_BACKEND_URL, label="Spring Boot URL", scale=3)
            refresh_books = gr.Button("🔄 연결·도서 새로고침", scale=1)
            connection_status = gr.Markdown("연결 확인 전", elem_classes=["status-box"])

        with gr.Accordion("0. 테스트 도서 준비", open=True):
            with gr.Row():
                book_directory = gr.Textbox(value="alice-demo", label="test-books 입력 디렉터리")
                import_button = gr.Button("📚 도서 가져오기", variant="primary")
            book_dropdown = gr.Dropdown(label="테스트 도서", choices=[])
            book_summary = gr.Markdown("도서를 선택하세요.")
            with gr.Row():
                import_status = gr.Markdown(elem_classes=["status-box"])
                import_elapsed = gr.Textbox(label="소요 시간", interactive=False)
            with gr.Accordion("도서 API 원본 응답", open=False):
                book_json = gr.JSON()
            books_json = gr.JSON(label="전체 도서 목록", visible=False)

        with gr.Accordion("검수 전 1~4단계 일괄 실행", open=True):
            gr.Markdown("등장인물 추출 → 이미지 분석 → KG 생성 → 역할 추천을 순서대로 실행합니다. 실패한 단계에서 중단합니다.", elem_classes=["cost-note"])
            pre_run_button = gr.Button("▶ 검수 전 단계 일괄 실행", variant="primary")
            pre_status = gr.Markdown(elem_classes=["status-box"])
            pre_elapsed = gr.Textbox(label="전체 소요 시간", interactive=False)
            pre_json = gr.JSON(label="단계별 원본 응답")

        step_components: dict[str, tuple[gr.Button, gr.Markdown, gr.JSON, gr.Textbox]] = {}
        for index, (label, description) in enumerate([
            ("등장인물 후보 추출", "원문 배치에서 이름·별칭·출처를 추출합니다."),
            ("페이지 이미지 분석", "각 페이지 이미지와 원문을 함께 분석합니다."),
            ("사건·KG 생성", "사건과 개체 사이의 직접 관계를 만듭니다."),
            ("등장인물 역할 추천", "후보 역할을 추천하지만 자동 승인하지 않습니다."),
        ], start=1):
            with gr.Accordion(f"{index}. {label}", open=False):
                gr.Markdown(f"{description}\n\n<span class='cost-note'>OpenAI API 호출 단계 · 재실행 시 기존 단계 데이터가 교체될 수 있습니다.</span>")
                button = gr.Button(f"▶ {label} 실행", variant="primary")
                status = gr.Markdown(elem_classes=["status-box"])
                elapsed = gr.Textbox(label="소요 시간", interactive=False)
                result = gr.JSON(label="원본 API 응답")
                step_components[label] = (button, status, result, elapsed)

        with gr.Accordion("5. 사람 검수", open=True):
            gr.Markdown("AI 추천을 참고해 후보를 승인·거절·병합합니다. `대화 허용` 캐릭터는 책당 한 명만 선택할 수 있습니다.")
            refresh_reviews = gr.Button("🔄 검수 후보 불러오기")
            review_load_status = gr.Markdown(elem_classes=["status-box"])
            candidate_dropdown = gr.Dropdown(label="검수 후보")
            candidate_details = gr.Markdown("후보를 선택하세요.")
            with gr.Row():
                narrative_role = gr.Dropdown(["MAIN", "SUPPORTING", "MINOR", "UNKNOWN"], value="SUPPORTING", label="최종 역할")
                chat_enabled = gr.Checkbox(label="대화 허용", value=False)
                merge_target = gr.Dropdown(label="병합 대상 후보")
            with gr.Row():
                approve_button = gr.Button("✅ 승인", variant="primary")
                reject_button = gr.Button("❌ 거절", variant="stop")
                merge_button = gr.Button("🔗 선택 대상에 병합")
            review_status = gr.Markdown(elem_classes=["status-box"])
            review_elapsed = gr.Textbox(label="소요 시간", interactive=False)
            review_action_json = gr.JSON(label="최근 검수 API 응답")
            review_json = gr.JSON(label="검수 목록 원본 응답")

        for offset, (label, description) in enumerate([
            ("최종 캐릭터 프로필", "승인한 대화 캐릭터의 결말 직후 프로필과 근거를 생성합니다."),
            ("RAG 임베딩 색인", "원문 문단 임베딩을 pgvector에 저장합니다."),
        ], start=6):
            with gr.Accordion(f"{offset}. {label}", open=False):
                gr.Markdown(f"{description}\n\n<span class='cost-note'>OpenAI API 호출 단계</span>")
                button = gr.Button(f"▶ {label} 실행", variant="primary")
                status = gr.Markdown(elem_classes=["status-box"])
                elapsed = gr.Textbox(label="소요 시간", interactive=False)
                result = gr.JSON(label="원본 API 응답")
                step_components[label] = (button, status, result, elapsed)

        with gr.Accordion("프로필 + RAG 마무리 일괄 실행", open=True):
            final_button = gr.Button("▶ 대화 준비 마무리", variant="primary")
            final_status = gr.Markdown(elem_classes=["status-box"])
            final_elapsed = gr.Textbox(label="전체 소요 시간", interactive=False)
            final_json = gr.JSON(label="단계별 원본 응답")

        with gr.Accordion("8. RAG 검색 단독 테스트", open=False):
            rag_question = gr.Textbox(value="앨리스가 흰 토끼를 따라간 이유는 무엇이야?", label="검색 질문")
            rag_button = gr.Button("🔎 RAG 검색", variant="primary")
            rag_status = gr.Markdown(elem_classes=["status-box"])
            rag_elapsed = gr.Textbox(label="소요 시간", interactive=False)
            rag_json = gr.JSON(label="검색 범위와 원문")

        with gr.Accordion("9. 캐릭터 대화·근거 테스트", open=True):
            chat_question = gr.Textbox(value="흰 토끼와 나는 어떤 관계였어?", label="캐릭터에게 할 질문", lines=2)
            chat_button = gr.Button("💬 질문 실행", variant="primary")
            with gr.Row():
                chat_answer = gr.Markdown("답변이 여기에 표시됩니다.")
                grounded = gr.Textbox(label="근거 판정", interactive=False)
            chat_status = gr.Markdown(elem_classes=["status-box"])
            chat_elapsed = gr.Textbox(label="소요 시간", interactive=False)
            with gr.Tabs():
                with gr.Tab("RAG 원문"):
                    chat_rag = gr.JSON()
                with gr.Tab("직접 KG 관계"):
                    chat_relations = gr.JSON()
                with gr.Tab("전체 ChatResponse"):
                    chat_raw = gr.JSON()

        load_outputs = [book_dropdown, connection_status, books_json]
        demo.load(load_books, inputs=[backend_url], outputs=load_outputs)
        refresh_books.click(load_books, inputs=[backend_url, book_dropdown], outputs=load_outputs)
        backend_url.submit(load_books, inputs=[backend_url, book_dropdown], outputs=load_outputs)
        book_dropdown.change(load_book, inputs=[backend_url, book_dropdown], outputs=[book_summary, book_json])
        import_button.click(
            import_book,
            inputs=[backend_url, book_directory],
            outputs=[import_status, book_json, import_elapsed, book_dropdown, connection_status, books_json],
        ).then(load_book, inputs=[backend_url, book_dropdown], outputs=[book_summary, book_json])

        pre_run_button.click(run_pre_review, inputs=[backend_url, book_dropdown], outputs=[pre_status, pre_json, pre_elapsed]).then(
            load_reviews,
            inputs=[backend_url, book_dropdown],
            outputs=[candidate_dropdown, merge_target, reviews_state, candidate_details, narrative_role, chat_enabled, review_json, review_load_status],
        )
        for label, (button, status, result, elapsed) in step_components.items():
            button.click(
                lambda base_url, book_id, current_label=label: run_step(base_url, book_id, current_label),
                inputs=[backend_url, book_dropdown], outputs=[status, result, elapsed],
            )

        refresh_reviews.click(
            load_reviews, inputs=[backend_url, book_dropdown],
            outputs=[candidate_dropdown, merge_target, reviews_state, candidate_details, narrative_role, chat_enabled, review_json, review_load_status],
        )
        candidate_dropdown.change(select_candidate, inputs=[candidate_dropdown, reviews_state], outputs=[candidate_details, narrative_role, chat_enabled])

        review_common_inputs = [backend_url, book_dropdown, candidate_dropdown]
        review_outputs = [review_status, review_action_json, review_elapsed, candidate_dropdown, merge_target, reviews_state, candidate_details, narrative_role, chat_enabled, review_json, review_load_status]
        approve_button.click(
            lambda base, book, candidate, role, chat, target: review_candidate(base, book, candidate, "APPROVE", role, chat, target),
            inputs=review_common_inputs + [narrative_role, chat_enabled, merge_target], outputs=review_outputs,
        )
        reject_button.click(
            lambda base, book, candidate, role, chat, target: review_candidate(base, book, candidate, "REJECT", role, chat, target),
            inputs=review_common_inputs + [narrative_role, chat_enabled, merge_target], outputs=review_outputs,
        )
        merge_button.click(
            lambda base, book, candidate, role, chat, target: review_candidate(base, book, candidate, "MERGE", role, chat, target),
            inputs=review_common_inputs + [narrative_role, chat_enabled, merge_target], outputs=review_outputs,
        )

        final_button.click(run_final, inputs=[backend_url, book_dropdown], outputs=[final_status, final_json, final_elapsed])
        rag_button.click(test_rag, inputs=[backend_url, book_dropdown, rag_question], outputs=[rag_status, rag_json, rag_elapsed])
        chat_button.click(
            test_chat, inputs=[backend_url, book_dropdown, chat_question],
            outputs=[chat_answer, grounded, chat_rag, chat_relations, chat_raw, chat_status, chat_elapsed],
        )
    return demo


if __name__ == "__main__":
    build_ui().queue(default_concurrency_limit=1).launch(
        server_name=os.getenv("QA_SERVER_NAME", "127.0.0.1"),
        server_port=int(os.getenv("QA_SERVER_PORT", "7860")),
        show_error=True,
        css=QA_CSS,
    )
