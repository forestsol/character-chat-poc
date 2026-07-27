# Character Chat PoC

책의 페이지 텍스트와 이미지를 분석해 등장인물, 사건, 관계, 캐릭터 프로필을 만들고,
선택한 등장인물과 대화해 보는 PoC입니다.

분석 결과와 지식 그래프는 PostgreSQL에 저장하며, 문단 임베딩 검색에는 pgvector를
사용합니다. 답변은 검색된 원문 근거, 등장인물 관계, 검수된 프로필을 함께 참고해
생성합니다.

## 구성

- `backend/`: Java 17, Spring Boot, MyBatis 기반 API와 분석 파이프라인
- `frontend/`: Next.js 기반 캐릭터 대화 화면
- `qa/`: 분석 파이프라인을 단계별로 실행하고 결과를 검수하는 Gradio 도구
- `docs/`: 범위, 입력 형식, 분석 과정, 데이터 모델과 설계 결정
- `test-books/`: 사용자가 준비한 도서 입력 위치(실제 데이터는 Git에 포함하지 않음)

## 준비 사항

- Java 17
- Docker Desktop
- Node.js와 npm
- Python 3.10 이상(Gradio QA 도구를 사용할 때만 필요)
- OpenAI API 키(실제 AI 분석과 대화를 실행할 때 필요)

## 환경변수

저장소 루트의 `.env.example`을 참고해 필요한 값을 설정합니다.

```powershell
Copy-Item .env.example .env
```

Docker Compose는 루트의 `.env`를 자동으로 읽습니다. 백엔드와 프론트엔드를 터미널에서
직접 실행할 때는 해당 터미널 또는 Windows 사용자 환경변수에도 필요한 값을 설정해야
합니다. `.env`에는 API 키가 들어갈 수 있으므로 Git에 커밋하지 않습니다.

실제 OpenAI 연동을 사용하려면 다음 두 값이 필요합니다.

```powershell
$env:AI_PROVIDER = "openai"
$env:OPENAI_API_KEY = "발급받은 API 키"
```

`AI_PROVIDER`의 기본값은 `fake`입니다. 이 모드는 외부 API를 호출하지 않으며,
실제 도서 분석과 캐릭터 답변 품질을 확인하려면 `openai`로 설정해야 합니다.

## 도서 입력 준비

저작권과 저장소 용량 문제로 실제 도서 파일은 저장소에 포함하지 않습니다. 사용할
도서를 다음과 같이 직접 배치합니다.

```text
test-books/
└─ my-book/
   ├─ book.json
   ├─ pages/
   │  ├─ page-001.txt
   │  └─ page-002.txt
   └─ images/
      └─ page-001-01.png
```

`book.json`의 구조, 페이지 번호와 파일명 규칙은
[도서 입력 형식](docs/02-book-input-spec.md)을 참고하세요. 도서 원문과 이미지는
사용 권한을 확인한 뒤 사용해야 합니다.

## 실행

### 1. PostgreSQL과 pgvector

저장소 루트에서 실행합니다.

```powershell
docker compose up -d
docker compose ps
```

기본 접속 포트는 `5433`입니다. 데이터베이스 스키마는 백엔드 시작 시 Flyway가
자동으로 반영합니다.

### 2. 백엔드

새 PowerShell에서 실행합니다.

```powershell
cd backend
.\gradlew.bat bootRun
```

- API: `http://localhost:8081`
- Swagger UI: `http://localhost:8081/swagger-ui.html`
- 상태 확인: `http://localhost:8081/actuator/health`

### 3. 프론트엔드

새 PowerShell에서 실행합니다.

```powershell
cd frontend
npm ci
npm run dev
```

브라우저에서 `http://localhost:3000`으로 접속합니다.

### 4. 분석·검수 QA 도구

QA 도구는 도서 가져오기부터 프로필, 임베딩 생성까지 파이프라인을 단계별로 확인할 때
사용합니다. 백엔드를 먼저 실행한 다음, 저장소 루트의 새 PowerShell에서 실행합니다.

```powershell
python -m venv qa\.venv
qa\.venv\Scripts\python.exe -m pip install -r qa\requirements.txt
qa\.venv\Scripts\python.exe qa\app.py
```

브라우저에서 `http://localhost:7860`으로 접속합니다.

## 테스트와 빌드

```powershell
cd backend
.\gradlew.bat test
```

```powershell
cd frontend
npm ci
npm run typecheck
npm run build
```

외부 OpenAI API를 호출하는 통합 테스트는 기본 테스트에서 제외됩니다. 일부 실연동
테스트는 별도로 준비한 도서 데이터와 `RUN_OPENAI_LIVE_TESTS=true`가 필요하며 API
사용 비용이 발생할 수 있습니다.

## 종료

각 애플리케이션 터미널에서 `Ctrl+C`를 누릅니다. 데이터베이스 컨테이너는 다음 명령으로
종료합니다.

```powershell
docker compose down
```

위 명령은 데이터 볼륨을 유지합니다. 볼륨까지 삭제하면 기존 분석 데이터가 사라지므로
필요한 경우에만 별도로 처리하세요.

## 관련 문서

- [프로젝트 개요](docs/00-project-overview.md)
- [PoC 범위](docs/01-scope.md)
- [도서 입력 형식](docs/02-book-input-spec.md)
- [분석 파이프라인](docs/03-analysis-pipeline.md)
- [데이터 모델 초안](docs/04-data-model-draft.md)
- [설계 결정](docs/05-decisions.md)

