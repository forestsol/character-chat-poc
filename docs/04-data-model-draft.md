# 데이터 모델 초안

## 1. 목적

이 문서는 개념 데이터 모델의 초안이다.

실제 PostgreSQL 테이블과 MyBatis 매핑 구조는 각 작업을 구현하기 전에 다시 검토한다.

## 2. 원본 데이터

### Book

```text
id
bookKey
title
author
status
createdAt
updatedAt
```

### BookPage

```text
id
bookId
pageNumber
createdAt
```

제약:

- 한 책 안에서 pageNumber는 유일

### BookParagraph

```text
id
bookId
pageId
paragraphIndex
sourceOrder
content
createdAt
```

제약:

- 한 페이지 안에서 paragraphIndex는 유일
- 한 책 안에서 sourceOrder는 유일
- sourceOrder는 책 전체의 문단 순서를 표현

### BookImage

```text
id
bookId
pageId
imageOrder
filePath
createdAt
```

제약:

- 한 페이지 안에서 imageOrder는 유일

## 3. 개체 후보와 등장인물

### EntityCandidate

```text
id
bookId
entityType
canonicalNameCandidate
description
confidence
reviewStatus
createdAt
```

entityType 후보:

```text
CHARACTER
PLACE
OBJECT
ORGANIZATION
```

reviewStatus 후보:

```text
PENDING
APPROVED
REJECTED
MERGED
```

### EntityMention

```text
id
entityCandidateId
paragraphId nullable
imageId nullable
mentionText
sourceType
confidence
```

sourceType:

```text
TEXT
IMAGE
TEXT_AND_IMAGE
INFERRED
```

Task 4 구현에서는 `entity_candidate`와 `entity_mention` 테이블을 생성했다. 텍스트에서 실제로 발견한 정규 이름과 별칭은 `EntityMention.mentionText`에 원본 문단 근거와 함께 저장한다. 이미지 기반 mention은 Task 5에서 확장한다.

### Character

검수 후 확정된 등장인물이다.

```text
id
bookId
knowledgeEntityId
name
narrativeRole
chatEnabled
reviewed
createdAt
updatedAt
```

narrativeRole:

```text
MAIN
SUPPORTING
MINOR
UNKNOWN
```

### CharacterAlias

```text
id
characterId
alias
sourceType
```

## 4. 추출 정보와 장면 관찰

### ExtractedFact

텍스트 또는 이미지에서 얻은 일반적인 사실 후보를 저장한다.

```text
id
bookId
factType
subjectEntityId nullable
value
sourceType
status
confidence
paragraphId nullable
imageId nullable
description nullable
createdAt
```

factType 예시:

```text
APPEARANCE
CLOTHING
EXPRESSION
ACTION
LOCATION
INDOOR_OUTDOOR
WEATHER
TIME_OF_DAY
PERSONALITY
GOAL
KNOWLEDGE
```

status:

```text
CONFIRMED
CANDIDATE
UNKNOWN
CONFLICT
```

## 5. 사건

### StoryEvent

```text
id
bookId
name
description
sequenceOrder nullable
confidence
reviewStatus
createdAt
```

이번 PoC에서는 장별 시점 제어를 하지 않지만, 사건의 상대적 순서를 보존할 수 있도록 sequenceOrder를 둘 수 있다.

### EventParticipant

```text
id
eventId
entityId
participantRole
evidenceParagraphId nullable
evidenceImageId nullable
```

## 6. KG

KG는 별도 그래프 DB 없이 PostgreSQL의 KnowledgeEntity 및 KnowledgeRelation 테이블로 구현한다. 원본 문단, 이미지, 사건, 캐릭터 프로필과 같은 ID 체계로 근거를 연결한다.

### KnowledgeEntity

```text
id
bookId
entityType
referenceId nullable
name
description nullable
reviewStatus
createdAt
```

entityType 예시:

```text
CHARACTER
PLACE
OBJECT
ORGANIZATION
EVENT
```

referenceId는 Character 또는 StoryEvent 등 원본 도메인 데이터와 연결할 때 사용한다.

### KnowledgeRelation

```text
id
bookId
sourceEntityId
relationType
targetEntityId
description nullable
confidence
reviewStatus
evidenceParagraphId nullable
evidenceImageId nullable
createdAt
```

이번 PoC에서는 결말 시점의 주요 관계를 저장한다.

향후 확장 후보:

```text
startOrder
endOrder
```

이는 관계가 이야기의 어느 구간에서 유효한지 나타낸다. 이번 PoC에서는 사용하지 않아도 된다.

## 7. 캐릭터 프로필

### CharacterProfile

```text
id
characterId
storyPoint
roleDescription
appearance
personality
values
goals
speechStyle
majorExperiences
attitudesTowardOthers
knownFacts
systemPrompt nullable
createdAt
updatedAt
```

storyPoint 초기값:

```text
AFTER_FINAL_EVENT
```

긴 텍스트 필드를 하나의 문자열로 저장할지 구조화된 하위 테이블 또는 JSON으로 저장할지는 미정이다.

### ProfileEvidence

```text
id
characterProfileId
profileField
paragraphId nullable
imageId nullable
sourceType
inferenceType
description
confidence
```

inferenceType:

```text
EXPLICIT
INFERRED
```

## 8. RAG

### RagDocument

초기에는 문단 하나를 하나의 검색 단위로 사용할 수 있다.

```text
id
bookId
documentType
referenceId
content
sourceOrderStart
sourceOrderEnd
pageNumberStart
pageNumberEnd
embedding
strategyVersion
active
createdAt
```

documentType 후보:

```text
PARAGRAPH
SCENE_DESCRIPTION
EVENT_SUMMARY
RELATION_SUMMARY
CHARACTER_PROFILE
```

초기 구현은 PARAGRAPH만 사용한다.

embedding은 pgvector의 `vector` 타입으로 저장할 예정이다. 정확한 임베딩 차원과 벡터 인덱스 종류는 임베딩 모델과 검색 요구사항을 확정한 뒤 결정한다.

캐릭터 프로필은 매번 직접 조회하므로 RAG에 반드시 저장할 필요는 없다.

## 9. 대화

### Conversation

```text
id
bookId
characterId
createdAt
```

### ConversationMessage

```text
id
conversationId
role
content
createdAt
```

role:

```text
USER
ASSISTANT
SYSTEM
```

대화 저장은 캐릭터 대화 API 구현 시점에 범위를 다시 결정한다.

## 10. 아직 확정하지 않은 사항

- JSON 컬럼 사용 여부
- 원본 이미지 저장 방식
- 외부 파일 스토리지 사용 여부
- CharacterProfile의 세부 정규화 방식
- LLM 요청 및 응답 로그 저장 범위
- 대화 기록 저장 여부와 보존 기간
