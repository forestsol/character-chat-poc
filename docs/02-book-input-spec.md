# 책 입력 형식

## 1. 목적

이번 PoC는 회사의 OCR 및 이미지 추출 기능이 이미 실행된 이후의 데이터를 입력으로 사용한다.

현재는 팀원이 AI를 활용해 합성 도서를 제작하고 사람이 검수한 뒤, 아래 표준 형식으로 전달한다.

향후 회사 시스템의 출력도 동일한 내부 입력 모델로 변환하여 같은 분석 파이프라인을 사용한다.

## 2. 권장 폴더 구조

```text
test-books/
└─ alice-demo/
   ├─ book.json
   ├─ pages/
   │  ├─ page-001.txt
   │  ├─ page-002.txt
   │  └─ page-003.txt
   └─ images/
      ├─ page-001-01.png
      ├─ page-001-02.png
      └─ page-003-01.png
```

## 3. book.json 초안

```json
{
  "bookKey": "alice-demo",
  "title": "앨리스의 이상한 모험",
  "author": "테스트 데이터 제작팀",
  "pages": [
    {
      "pageNumber": 1,
      "textFile": "pages/page-001.txt",
      "imageFiles": [
        "images/page-001-01.png",
        "images/page-001-02.png"
      ]
    },
    {
      "pageNumber": 2,
      "textFile": "pages/page-002.txt",
      "imageFiles": []
    },
    {
      "pageNumber": 3,
      "textFile": "pages/page-003.txt",
      "imageFiles": [
        "images/page-003-01.png"
      ]
    }
  ]
}
```

## 4. 텍스트 파일 규칙

- UTF-8 인코딩을 사용한다.
- 페이지 하나당 텍스트 파일 하나를 사용한다.
- 입력 페이지 번호는 속표지를 포함해 1부터 연속으로 부여한다.
- 표지 이미지는 현재 페이지 입력 및 DB 적재 범위에서 제외한다.
- 빈 줄을 문단 구분 기준으로 사용한다.
- 페이지 헤더, 페이지 번호, OCR 잡음은 가능하면 제거된 상태로 전달한다.
- 원문의 문장과 문단 순서를 임의로 재배열하지 않는다.

예시:

```text
앨리스는 언니 옆에 앉아 들판을 바라보고 있었다.

그때 흰 토끼 한 마리가 회중시계를 들고 달려갔다.

앨리스는 이상하다는 생각이 들어 토끼를 뒤쫓았다.
```

## 5. 이미지 파일 규칙

- PNG, JPEG, WEBP 중 하나를 사용한다.
- 이미지 파일명에는 페이지 번호와 페이지 내 순서를 포함한다.
- 이미지가 어느 페이지에 속하는지 book.json에 명시한다.
- 이미지 속 인물의 이름이나 장면 해설을 파일명에 직접 넣지 않는다.
- 이미지 자체만으로 분석 시스템이 의미를 추정할 수 있도록 한다.
- 텍스트와 이미지의 내용은 사람이 검수하여 모순을 줄인다.

## 6. 내부 표준 입력 모델

외부 파일 구조는 내부에서 다음 형태로 변환한다.

```text
BookInput
- bookKey
- title
- author
- pages

PageInput
- pageNumber
- text
- images

ImageInput
- imageOrder
- filePath
```

현재 구현:

```text
로컬 합성 도서 파일
→ LocalBookInputProvider
→ BookInput
```

향후 구현:

```text
회사 OCR 및 이미지 추출 결과
→ CompanyBookInputProvider
→ BookInput
```

분석 파이프라인은 BookInput 이후의 입력 출처를 알 필요가 없다.

## 7. 문단 순서

텍스트 저장 시 다음 값을 부여한다.

```text
pageNumber
- 책의 페이지 번호
- 1부터 시작

paragraphIndex
- 해당 페이지 안의 문단 순서
- 페이지마다 1부터 시작

sourceOrder
- 책 전체의 문단 순서
- 책 전체에서 1부터 연속 증가
```

예시:

| pageNumber | paragraphIndex | sourceOrder |
|---:|---:|---:|
| 1 | 1 | 1 |
| 1 | 2 | 2 |
| 1 | 3 | 3 |
| 2 | 1 | 4 |

## 8. 입력 검증

다음 경우 가져오기를 실패 처리한다.

- book.json이 없음
- bookKey가 없음
- 페이지 번호가 중복됨
- 텍스트 파일이 없음
- 이미지 파일이 없음
- 파일 경로가 허용된 책 디렉터리 밖을 가리킴
- JSON 형식이 잘못됨
- 같은 bookKey의 책이 이미 저장됨

중복 가져오기 정책은 추후 수정할 수 있으나 초기에는 중복 저장을 막는다.
