# Gradio 파이프라인 QA

Spring Boot 백엔드의 분석·검수·검색·대화 API를 단계별로 시험하는 개발용 화면이다. 도메인 로직이나 DB 접근은 포함하지 않는다.

## 실행

백엔드를 먼저 `http://localhost:8081`에서 실행한 뒤 저장소 루트에서 다음 명령을 실행한다.

```powershell
C:\Users\fores\AppData\Local\Programs\Python\Python313\python.exe -m venv qa\.venv
qa\.venv\Scripts\python.exe -m pip install -r qa\requirements.txt
qa\.venv\Scripts\python.exe qa\app.py
```

브라우저에서 `http://localhost:7860`을 연다.

기본값은 환경 변수로 변경할 수 있다.

- `BACKEND_URL`: Spring Boot 주소, 기본값 `http://localhost:8081`
- `QA_SERVER_NAME`: Gradio 바인딩 주소, 기본값 `127.0.0.1`
- `QA_SERVER_PORT`: Gradio 포트, 기본값 `7860`
- `QA_REQUEST_TIMEOUT_SECONDS`: 백엔드 요청 제한 시간, 기본값 `900`

AI 분석 버튼은 실제 OpenAI API를 호출할 수 있으므로 비용과 기존 분석 데이터 교체 가능성을 확인한 뒤 실행한다.
