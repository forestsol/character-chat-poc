# Character Chat PoC 학습 안내서

이 폴더는 프로젝트에 실제로 사용된 기술과 실무 기법을 개발 입문자의 눈높이에서 설명합니다.

## 시작하기

1. [프로젝트 기술 학습 가이드](./character-chat-technology-guide.md)를 처음부터 읽습니다.
2. 각 장의 **코드에서 찾기** 링크로 실제 구현을 확인합니다.
3. 마지막의 실습 로드맵을 따라 서버 실행 → API 관찰 → 작은 변경 순으로 연습합니다.

## 도해

| 파일 | 설명 |
|---|---|
| [system-architecture.svg](./images/system-architecture.svg) | 브라우저부터 AI·DB까지 전체 구성 |
| [analysis-pipeline.svg](./images/analysis-pipeline.svg) | 책 입력부터 캐릭터 대화까지의 분석 단계 |
| [request-lifecycle.svg](./images/request-lifecycle.svg) | 한 API 요청이 계층을 통과하는 과정 |
| [rag-flow.svg](./images/rag-flow.svg) | 임베딩 색인과 검색 기반 답변 과정 |

> 이 문서는 저장소의 현재 코드와 설정을 기준으로 작성했습니다. 일반론과 프로젝트의 실제 구현을 구분해 표시합니다.
