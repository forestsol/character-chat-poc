# Codex 작업 목록

## 작업 운영 원칙

- 한 번에 하나의 Task만 수행한다.
- 각 Task 시작 전에 Codex가 구현 계획과 수정 파일 목록을 제시한다.
- 계획 검토 후에만 구현을 요청한다.
- 관련 없는 기능을 함께 구현하지 않는다.
- 구현 후 빌드와 테스트를 실행한다.
- 성공하지 않은 테스트를 성공으로 보고하지 않는다.
- 각 Task 완료 후 변경 파일, 테스트 결과, 남은 위험 요소를 기록한다.
- 설계가 변경되면 먼저 docs를 갱신한다.

상태:

```text
TODO
IN_PROGRESS
DONE
BLOCKED
```

---

## Task 0. 프로젝트 기준 문서 작성

상태: DONE

산출물:

- 00-project-overview.md
- 01-scope.md
- 02-book-input-spec.md
- 03-analysis-pipeline.md
- 04-data-model-draft.md
- 05-decisions.md
- 06-codex-tasks.md

---

## Task 1. Spring Boot 백엔드 기본 프로젝트 생성 및 실행 확인

상태: DONE

목표:

- Java 17 및 Gradle 환경 확인
- Spring Boot 3.5.x 단일 backend Gradle 프로젝트 생성
- 기본 패키지 구조 생성
- MyBatis, JDBC, PostgreSQL JDBC Driver, Flyway, Actuator, springdoc-openapi 기본 설정
- PostgreSQL과 pgvector의 로컬 Docker Compose 실행 환경 구성
- 애플리케이션과 데이터베이스 연결 및 실행 확인
- health check API 또는 기본 테스트 확인
- 아직 책 데이터 모델, LLM, RAG, KG 도메인 코드는 구현하지 않음

시작 전 결정:

- 초기 패키지명
- Spring Boot 3.5.x의 정확한 patch 버전

완료 기록:

- 패키지명: `com.example.characterchat`
- Spring Boot: `3.5.16`
- Java: Temurin `17.0.19`
- Gradle Wrapper: `8.14.3`
- PostgreSQL 호스트 포트: 기존 5432 포트와의 충돌을 피해 기본값 `5433` 사용
- Gradle 테스트, Flyway의 vector 확장 활성화, Actuator health 및 OpenAPI 문서 응답 확인 완료

---

## Task 2. 표준 책 입력과 원본 데이터 적재

상태: DONE

목표:

- LocalBookInputProvider
- book.json 파싱
- 페이지별 텍스트 및 이미지 경로 읽기
- 빈 줄 기준 문단 분리
- pageNumber, paragraphIndex, sourceOrder 생성
- Book, BookPage, BookParagraph, BookImage 저장
- 중복 bookKey 방지
- 입력 검증
- 조회 API
- 테스트

완료 기록:

- `book`, `book_page`, `book_paragraph`, `book_image` 테이블과 MyBatis 매핑 구현
- 로컬 입력 루트 내부 경로 검증, JSON 및 파일 검증, UTF-8 본문 읽기 구현
- 빈 줄 기준 문단 분리와 paragraphIndex, sourceOrder, imageOrder 생성 구현
- 가져오기, 목록, 상세 조회 API와 400, 404, 409 오류 응답 구현
- `alice-demo` 실제 적재 결과: 페이지 10개, 문단 38개, 이미지 10개
- 전체 Gradle 테스트 9개 통과

---

## Task 3. 외부 LLM 및 멀티모달 API 공통 연동

상태: DONE

목표:

- 텍스트 생성 인터페이스
- 구조화된 JSON 응답 인터페이스
- 이미지와 텍스트를 함께 전달하는 멀티모달 인터페이스
- 환경변수 기반 API 키 관리
- 타임아웃과 오류 처리
- 테스트용 가짜 구현체 또는 Mock

시작 전 결정:

- AI 제공자: OpenAI
- 초기 모델: `gpt-5.6-luna`이며 `OPENAI_MODEL` 환경변수로 교체 가능
- JSON 스키마 응답: OpenAI 공식 Java SDK의 Responses API 구조화 출력 사용
- 이미지 전달: 로컬 파일을 data URL로 변환해 Responses API 입력으로 전달

완료 기록:

- 제공자 독립 `AiClient`에 텍스트, 구조화 응답, 멀티모달 메서드 정의
- OpenAI 공식 Java SDK 4.43.0과 Responses API 기반 구현 추가
- `OPENAI_API_KEY` 환경변수 기반 키 관리와 모델, 타임아웃, 재시도 설정 추가
- 기본 실행은 비용 없는 Fake 구현을 사용하고, OpenAI 구현은 `AI_PROVIDER=openai`일 때만 활성화
- Fake 계약 테스트와 환경변수로 명시적으로 활성화하는 실제 API 테스트 분리
- `gpt-5.6-luna` 실제 호출로 텍스트, 구조화 출력, 로컬 이미지 분석 검증 완료

---

## Task 4. 텍스트 기반 개체 후보 추출

상태: DONE

목표:

- CHARACTER, PLACE, OBJECT, ORGANIZATION 후보 추출
- 이름, 별칭, 간단한 설명, 근거 문단 저장
- 기존 후보와 동일 개체 여부 판단
- 후보 목록 조회
- 구조화된 JSON 응답 검증
- 테스트

주의:

- 관계와 사건은 아직 추출하지 않는다.
- 대화 가능 여부를 자동 결정하지 않는다.

완료 기록:

- `entity_candidate`, `entity_mention` 테이블과 MyBatis 매핑 구현
- 설정 가능한 문단 배치 분석과 이전 후보 컨텍스트 전달 구현
- OpenAI 구조화 응답의 유형, 신뢰도, 근거 순서와 원문 지칭 표현 검증
- 정규 이름 및 별칭 비교를 통한 배치 간 동일 개체 병합
- 모든 배치 성공 후 기존 후보를 트랜잭션으로 교체하고 책 상태를 `TEXT_ENTITIES_EXTRACTED`로 전환
- 후보 추출 및 목록 조회 API 구현
- Fake 기반 API 통합 테스트와 `alice-demo` 실제 OpenAI 추출 검증 완료

---

## Task 5. 페이지별 이미지 분석과 멀티모달 정보 병합

상태: DONE

목표:

- 같은 페이지 텍스트 직접 조회
- 현재 개체 후보 목록 조회
- 이미지, 텍스트, 후보를 멀티모달 모델에 전달
- 외모, 옷차림, 표정, 행동, 장소, 실내외, 날씨, 시간대, 물건 추출
- 출처와 신뢰도 저장
- 텍스트와 이미지 결과 병합
- UNKNOWN과 CONFLICT 표현
- 테스트

완료 기록:

- `AiClient`에 이미지 구조화 응답 메서드와 OpenAI/Fake 구현 추가
- `EntityMention.imageId`, 이미지 후보 출처와 `extracted_fact` 테이블 구현
- 페이지 원문, 현재 후보와 해당 페이지 이미지를 함께 전달하는 멀티모달 분석 구현
- 기존 텍스트 후보의 이미지 근거 병합과 미확정 이미지 후보 분리 저장 구현
- 외모, 옷차림, 표정, 행동, 장소, 실내외, 날씨, 시간대, 물건 관찰 사실 검증 및 저장
- `CONFIRMED`, `CANDIDATE`, `UNKNOWN`, `CONFLICT` 상태와 IMAGE/TEXT_AND_IMAGE 출처 검증
- 모든 페이지 성공 후 이미지 결과만 교체·병합하고 책 상태를 `MULTIMODAL_MERGED`로 전환
- 이미지 분석 실행·사실 조회 API와 Fake 기반 10페이지 통합 테스트 구현
- `alice-demo` 대표 삽화의 실제 OpenAI 구조화 멀티모달 분석 검증 완료

---

## Task 6. 사건 및 관계 추출과 KG 구축

상태: DONE

목표:

- 주요 사건 추출
- 사건 참여자 추출
- KG 개체 생성
- 직접 관계 추출
- 원문 및 이미지 근거 연결
- KG 조회 API
- 테스트

초기 제외:

- 복잡한 다단계 그래프 탐색
- 시점별 관계 변화

완료 기록:

- `story_event`, `event_participant`, `knowledge_entity`, `knowledge_relation` 테이블과 MyBatis 매핑 구현
- 전체 원문, 개체 후보와 이미지 관찰 사실 기반 구조화 사건·직접 관계 추출 구현
- 사건 순서, 참여자, 관계 유형, 신뢰도와 원문·이미지 근거 검증 구현
- 검수 전 후보와 사건을 PENDING KnowledgeEntity로 투영하고 PostgreSQL KG ID로 연결
- 모든 추출 성공 후 사건과 KG를 교체하고 책 상태를 `KG_BUILT`로 전환
- KG 구축 및 책별 사건·개체·관계 조회 API 구현
- Fake 기반 통합 테스트와 `alice-demo` 실제 OpenAI 사건·관계 추출 검증 완료

---

## Task 7. 등장인물 검수 및 대화 가능 인물 선택

상태: DONE

목표:

- 등장인물 후보 목록 조회
- AI 추천 역할 표시
- 사람이 역할을 수정
- chatEnabled 설정
- 동일 인물 병합 또는 잘못된 후보 제외
- 한 명만 활성화하는 PoC 규칙 적용
- 테스트

프론트 화면은 이후 Next.js 작업에서 구현할 수 있으며, 초기에는 API로 검수할 수 있다.

완료 기록:

- AI narrativeRole 추천과 추천 이유 저장 구현
- `character`, `character_alias` 테이블 및 후보 추천·병합 필드 구현
- APPROVE, REJECT, MERGE 검수 상태 전환 API 구현
- 승인 시 최종 역할, 별칭과 Character 생성 및 KG 상태 연결
- 병합 시 mention, 관찰 사실, 사건 참여자와 KG 관계 이동
- 책별 chatEnabled 한 명 제한을 서비스 및 PostgreSQL 부분 유일 인덱스로 적용
- 활성 인물 선택에 따른 `CHARACTERS_REVIEWED`/`KG_BUILT` 상태 전환 구현
- Fake 기반 추천·승인·병합 통합 테스트와 실제 OpenAI 역할 추천 검증 완료

---

## Task 8. 최종 캐릭터 프로필 생성

상태: TODO

목표:

- 대화 가능 인물에 대해서만 실행
- 텍스트, 이미지 사실, 사건, KG를 종합
- 결말 직후 프로필 생성
- EXPLICIT과 INFERRED 구분
- 근거 연결
- 프로필 조회 API
- 테스트

---

## Task 9. 기본 RAG 검색

상태: TODO

목표:

- 원문 문단 임베딩 생성
- 벡터 검색
- topK 설정
- 앞뒤 contextWindow 문단 확장
- 겹치는 범위 병합
- pageNumber와 sourceOrder 반환
- 검색 전략 인터페이스 분리
- 테스트 질문을 이용한 검색 결과 확인

시작 전 결정:

- 임베딩 모델
- topK 초기값
- contextWindow 초기값

---

## Task 10. 캐릭터 대화 API

상태: TODO

목표:

- 캐릭터 프로필 직접 조회
- RAG 원문 검색
- KG 관계 조회
- 프롬프트 조립
- 외부 LLM 답변 생성
- 근거가 없을 때 모른다고 답변
- 발표용 디버그 정보 반환
- 테스트

---

## Task 11. Next.js 발표용 화면

상태: TODO

목표:

- Next.js 15 + React 19 + TypeScript 단일 화면
- TanStack Query, Tailwind CSS, Radix UI, lucide-react 기본 구성
- 책 정보
- 등장인물 후보 및 검수 결과
- 대화 가능 캐릭터 표시
- 질문 입력
- 캐릭터 답변
- RAG 원문 근거
- KG 관계
- 캐릭터 최종 프로필
- 로딩과 오류 상태

제외:

- 로그인
- 복잡한 라우팅
- 운영 서비스 수준의 디자인
- 복잡한 상태 관리 라이브러리

---

## Task 12. 평가 질문 실행과 개선

상태: TODO

목표:

- 테스트 질문 세트 작성
- 검색된 원문 검토
- KG 관계 검토
- 캐릭터 말투 검토
- 원문에 없는 사실 생성 여부 검토
- 실패 유형 기록
- 필요한 부분만 개선

실패 유형 예시:

```text
관련 문단을 못 찾음
관련 문단은 찾았지만 문맥이 부족함
잘못된 KG 관계를 사용함
이미지 정보를 잘못 연결함
추론을 사실처럼 답변함
캐릭터 말투가 유지되지 않음
```
