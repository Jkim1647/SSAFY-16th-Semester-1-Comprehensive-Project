# 게시판 담당자를 위한 현황 조사 및 확장 검토

작성일: 2026-10-30 (Asia/Seoul)

> 이 문서는 구현 전 조사 시점의 기록이다. 이후 승인된 확장 구현 현황은 [통합 게시판 문서](board-community.md)를 참고한다.

## 1. 이번 문서의 범위

프로젝트 문서와 게시판 관련 소스·SQL·테스트를 읽고 정리했다. 기능 구현, SQL 변경·실행, 서버 기동은 하지 않았다.

- **확인된 사실**: 저장소 문서와 현재 코드에서 확인한 내용이다. 실행 검증 결과를 뜻하지 않는다.
- **사용자 의도**: 게시판을 담당하며 좋아요·북마크 등의 기능을 구현할 예정이다.
- **검토 제안**: 향후 구현을 논의하기 위한 후보이며 확정 요구사항이나 승인된 설계가 아니다.
- **미정**: 사용자·팀의 결정이 필요한 내용이다. 아래에 없는 추가 기능까지 담당 범위로 간주하지 않는다.

## 2. 읽은 자료와 프로젝트 구조

| 자료 | 확인 내용 |
|---|---|
| [README](../README.md) | 서비스 목적, MVC 구조, 유스케이스·클래스·ER 다이어그램, 실행 방법, 보안 원칙 |
| [요구사항 목록](requirements.md) | F101~F116, 비기능 요구사항 NF101~NF107 |
| [Maven 설정](../pom.xml) | Java 17, WAR, Servlet 6.1, JSP/JSTL, JDBC, Jackson, JUnit 의존성 |
| [게시판 스키마](../src/main/resources/sql/40-board.sql) | 공지·자유게시판을 boards 한 테이블로 관리 |
| [회원 스키마](../src/main/resources/sql/01-core.sql) | 회원 ID·권한 및 게시판 FK 연결 대상 |
| [여행 계획](../src/main/resources/sql/20-plan.sql), [핫플](../src/main/resources/sql/30-hotplace.sql), [뉴스](../src/main/resources/sql/50-news.sql) | 게시판과 별도 도메인으로 관리되는 데이터 |
| [DB 초기화](../src/main/java/com/ssafy/enjoytrip/listener/AppInitListener.java) | 스키마 파일 실행 순서와 CREATE TABLE IF NOT EXISTS 방식 |
| 아래 계층별 파일 및 공통 인증·CSRF·JS, 마이페이지 관련 코드 | 문서 내용과 실제 게시판 동작 대조, 확장 접점 확인 |

EnjoyTrip EV는 전기차 여행 서비스다. 관광지 조회, 여행 계획, 핫플레이스, 뉴스, 회원, 커뮤니티, 충전소 예약·시뮬레이션으로 구성된다. README는 Java 17·Tomcat 11·MySQL 8 기반이라고 설명한다. 현재 프로젝트는 Spring/JPA가 아닌 Servlet/JSP/JDBC 방식이다.

게시판 요청 흐름은 브라우저 → `BoardController` → `BoardService` → `BoardDao`/`JdbcBoardDao` → DB이며, 조회 결과는 JSP에 전달한다. 공통 JS에는 JSON 응답을 받는 fetch 도우미도 있다.

## 3. 기존 요구사항과 담당 범위

| 구분 | 현재 근거 | 상태 |
|---|---|---|
| F109 공지사항 | 관리자 등록·수정·삭제, 누구나 조회, 자유게시판 상단 공지 노출 | 문서에 완료로 기재, 관련 코드 존재 |
| F110 자유게시판 | CRUD, 검색·페이징·조회수 | 문서에 완료로 기재, 관련 코드 존재 |
| 좋아요 | 사용자 언급 | 신규 예정, 기존 요구사항 번호 없음 |
| 북마크 | 사용자 언급 | 신규 예정, 대상·정책 미정 |
| 댓글·대댓글·신고·인기순·게시판 첨부파일 등 | 이번 요청에서 구체화되지 않음 | 담당 범위에 임의로 추가하지 않음 |

F110은 요구사항 문서에 “로그인 회원 CRUD”라고 적혀 있지만 실제 컨트롤러는 목록·상세에 로그인을 요구하지 않는다. 현재 코드를 기준으로 **비회원 읽기 허용, 쓰기 로그인 필요**로 이해하되 팀 문구를 맞출 필요가 있다.

## 4. 현재 게시판 동작

### 4.1 기능·권한

| 기능 | 현재 코드에서 확인한 규칙 |
|---|---|
| 게시판 구분 | URL의 `type=free/notice` → 내부 값 `FREE/NOTICE`. 생략 시 FREE, 지원하지 않는 값은 해당 처리 경로에서 404 |
| 목록·상세 | 비회원 접근 가능. 상세는 저장된 게시글의 종류를 사용 |
| 글 작성 | FREE는 로그인 회원, NOTICE는 관리자 |
| 수정·삭제 | FREE는 작성자 또는 관리자, NOTICE는 관리자만 가능 |
| 검색 | 제목·내용·작성자 중 하나. 허용된 검색 컬럼만 사용하고 LIKE 특수문자 이스케이프 |
| 목록 | 게시글 ID 내림차순, 페이지당 10건, 페이지 번호 묶음 5개 |
| 상단 공지 | 자유게시판에서 검색어가 유효하지 않거나 검색하지 않을 때 최신 공지 3건 별도 노출. 별도 고정 여부 필드는 없음 |
| 조회수 | 세션의 `viewedBoardIds`에 글 ID를 기록해 세션 내 같은 글 재조회 시 증가 억제 |
| 입력 | 제목 1~200, 본문 1~10,000 길이 검증. Java String.length 기준이며 앞뒤 공백 정리, 본문 CRLF 정규화 |
| 본문 출력 | 일반 텍스트를 이스케이프해 표시. HTML 편집기·게시판 첨부 처리 없음 |
| 삭제 | 게시글 물리 삭제. 회원 삭제 시 해당 회원 게시글은 FK의 ON DELETE CASCADE로 삭제 |

조회수는 계정별·하루별 집계가 아니다. 다른 세션에서는 다시 증가할 수 있다. 조회수 증가는 DB에서 `view_count + 1`로 수행하고 글 수정 시각은 유지한다.

### 4.2 기존 요청 경로

경로 앞에는 배포 컨텍스트(예: `/enjoytrip`)가 붙는다. 아래 표는 현재 코드에 있는 경로다.

| 메서드 | 경로 | 용도 |
|---|---|---|
| GET | `/board?type=free` 또는 `type=notice` | 목록. action 기본값은 index |
| GET | `/board?action=detail&id=...` | 상세 |
| GET | `/board?action=write-form&type=...` | 작성 화면 |
| GET | `/board?action=edit-form&id=...` | 수정 화면 |
| POST | `/board?action=write` | 작성. type/title/content 전달 |
| POST | `/board?action=modify` | 수정. id/title/content 전달 |
| POST | `/board?action=delete` | 삭제. id 전달 |

기존 작성·수정·삭제는 주로 폼 제출 후 리다이렉트 방식이다. 수정·삭제 권한은 요청의 type이 아니라 DB에서 읽은 게시글로 판단한다.

### 4.3 데이터

`boards` 필드는 `board_id`, `board_type`, `member_id`, `title`, `content`, `view_count`, `created_at`, `updated_at`이다.

- PK: `board_id` BIGINT 자동 증가.
- 인덱스: `(board_type, board_id)`.
- FK: `member_id` → `members.member_id`, 회원 삭제 시 게시글 삭제.
- DTO의 `writerName`은 members JOIN으로 채우며 boards 컬럼이 아니다.
- `NOTICE/FREE`는 코드에서 사용하는 값이다. 현재 DDL에 해당 값만 강제하는 CHECK/ENUM은 없다.
- 좋아요 수, 좋아요 회원 관계, 북마크 관계를 저장하는 컬럼·테이블은 현재 SQL에 없다.

## 5. 파일별 역할과 향후 접점

Java 파일의 공통 경로는 `src/main/java/com/ssafy/enjoytrip/`이다.

| 파일 | 역할 및 향후 검토할 부분 |
|---|---|
| [BoardController](../src/main/java/com/ssafy/enjoytrip/controller/BoardController.java) | action 분기, 로그인·권한 검사, JSP 속성, 조회수 중복 방지. 신규 요청 연결 후보 |
| [BoardService](../src/main/java/com/ssafy/enjoytrip/model/service/BoardService.java) | 게시판 규칙·입력 검증. 좋아요·북마크 정책을 둘 계층 |
| [BoardDao](../src/main/java/com/ssafy/enjoytrip/model/dao/BoardDao.java), [JdbcBoardDao](../src/main/java/com/ssafy/enjoytrip/model/dao/JdbcBoardDao.java) | 게시글 저장·검색·조회수. 관계 조회는 별도 DAO로 나누는 방안 검토 가능 |
| [Board DTO](../src/main/java/com/ssafy/enjoytrip/model/dto/Board.java) | 현재 게시글 데이터. 좋아요 수·내 상태는 응답용 DTO 분리 여부 검토 |
| [목록 JSP](../src/main/webapp/WEB-INF/views/board/list.jsp), [상세 JSP](../src/main/webapp/WEB-INF/views/board/detail.jsp), [폼 JSP](../src/main/webapp/WEB-INF/views/board/form.jsp) | 현재 화면. 상세의 반응 버튼·목록의 개수 표시 후보 |
| [ControllerHelper](../src/main/java/com/ssafy/enjoytrip/controller/ControllerHelper.java) | 세션 loginUser, 로그인 검사, JSON 응답. 회원 ID는 세션에서 결정할 수 있음 |
| [CsrfFilter](../src/main/java/com/ssafy/enjoytrip/filter/CsrfFilter.java) | 상태 변경 요청 토큰 검사. `_csrf` 또는 `X-CSRF-Token` |
| [common.js](../src/main/webapp/assets/js/common.js) | `ET.post`가 폼 인코딩·CSRF·AJAX 헤더 처리. 새 동작 연동 후보 |
| [마이페이지 컨트롤러](../src/main/java/com/ssafy/enjoytrip/controller/MemberController.java), [마이페이지 JSP](../src/main/webapp/WEB-INF/views/member/mypage.jsp) | 현재 내 예약·회원 정보 중심. 내 북마크 목록 노출 위치를 회원 담당자와 협의할 접점 |
| [BoardServiceTest](../src/test/java/com/ssafy/enjoytrip/model/service/BoardServiceTest.java) | DB 없이 권한·입력 오류를 검사하는 테스트 4개 존재 |

## 6. 좋아요·북마크 확장 검토 제안 — 미확정

이 절은 기존 구현 설명이 아니다. 게시판 담당이라는 맥락에서 대상을 **게시글**로 가정한 검토안이다. 관광지·핫플 북마크도 원하는 경우 대상과 담당 경계부터 다시 정해야 한다.

### 6.1 우선 정리할 사용자 동작

| 기능 후보 | 검토할 규칙 |
|---|---|
| 좋아요 등록·해제 | 로그인 회원이 글마다 하나의 좋아요 관계를 생성·삭제 |
| 좋아요 표시 | 글 전체 좋아요 수와 현재 회원의 선택 여부를 구분 |
| 북마크 등록·해제 | 로그인 회원이 나중에 볼 글을 개인 목록에 저장·제거 |
| 내 북마크 목록 | 현재 회원의 목록만 검색·페이징. 저장한 순서 등 정렬 기준 결정 필요 |

좋아요와 북마크는 목적이 다르므로 각각 독립 상태로 검토한다. 북마크의 공개 여부, 공지사항 적용 여부, 자기 글 좋아요 허용 여부는 미정이다.

### 6.2 저장 구조의 선택지

| 선택지 | 장점 | 주의점 |
|---|---|---|
| 좋아요·북마크 각각 별도 관계 테이블 | 각 의미와 FK가 명확하고 기능별 조회·정책 분리가 쉬움 | DAO·쿼리가 각각 필요 |
| 게시글 반응 테이블에 종류를 구분 | 공통 등록·삭제 로직을 묶을 수 있음 | 종류별 제약·정책·중복 키 설계가 필요 |

**검토 의견**: 현재 두 기능만 고려하면 별도 관계 테이블이 읽고 관리하기 쉽다. 선택은 확정하지 않았다. 여러 도메인을 묶는 `target_type + target_id` 구조는 대상 테이블 FK 유지가 어려워지므로 범위 확인 없이 도입하지 않는다.

별도 테이블을 택할 경우 논리 후보는 다음과 같다. 실제 DDL은 작성하지 않았다.

| 관계 후보 | 필드 후보 | 제약·조회 검토 |
|---|---|---|
| board_likes | board_id, member_id, created_at | `(board_id, member_id)` 유일성, 두 대상 FK. 게시글별 개수 조회 고려 |
| board_bookmarks | board_id, member_id, created_at | `(board_id, member_id)` 유일성, 두 대상 FK. 내 목록을 위한 `(member_id, created_at, board_id)` 인덱스 후보 |

게시글·회원 삭제 시 관계를 함께 지울지 결정해야 한다. 기존 물리 삭제 정책과 맞추려면 관계 FK의 CASCADE가 후보가 된다. 특히 탈퇴하면 **그 회원이 작성한 게시글도 삭제되므로 다른 회원의 해당 글 북마크까지 사라질 수 있다**는 점을 팀에서 공유해야 한다.

좋아요 수는 관계 행을 집계하는 방식과 boards에 별도 카운터를 두는 방식이 있다. 초기에는 관계를 원본으로 집계하는 방안이 단순하다. 별도 카운터를 택하면 관계 변경·카운터 갱신·삭제를 같은 트랜잭션으로 관리해야 한다. 목록에서 좋아요와 북마크를 함께 JOIN할 때 행이 곱해져 개수가 부풀지 않도록 사전 집계 또는 EXISTS 등을 검토하고, 글마다 추가 쿼리를 반복하지 않도록 한다.

### 6.3 요청 처리 원칙 후보

- 현재 `/board?action=...` 방식을 유지할지, 별도 API를 둘지 팀 규칙에 맞춰 결정한다. 현재 좋아요·북마크 API는 없다.
- 단순 토글보다 등록·해제 의도가 명시된 요청을 검토한다. 같은 등록/해제를 재시도해도 상태가 뒤집히지 않도록 한다.
- 회원 ID는 세션 loginUser에서 가져오고 클라이언트가 다른 회원 ID를 지정하지 못하게 한다.
- DB 유일성으로 중복 관계를 막는다. 사전 존재 확인만으로 동시 중복 요청을 막을 수 있다고 가정하지 않는다.
- 중복 등록은 상태 유지로 처리하되, FK 오류·연결 오류 같은 다른 실패를 성공으로 숨기지 않는다.
- 글 존재 여부·적용 가능한 게시판 종류를 서버에서 확인한다. 글 삭제와 반응 등록이 겹치는 경우도 오류 정책을 정한다.
- 비동기 응답에는 서버에서 확인한 내 상태와 필요한 개수를 담는 방안을 검토한다. 다른 회원의 동시 변경까지 영구히 고정된 수치로 보장하지는 않는다.
- 요청 중 버튼 중복 클릭을 막고 실패 시 재조회·재시도할 수 있게 한다. 화면 방지만으로 DB 정합성을 대신하지 않는다.
- 현재 로그인 도우미는 AJAX를 구분하면 401 JSON을 줄 수 있지만 CSRF 필터는 403 sendError를 사용한다. 모든 오류가 JSON이라고 가정하지 않는다.
- GET은 조회 용도로 유지하고 변경 요청에는 기존 CSRF 체계를 적용한다. 북마크 목록도 세션 회원으로 조회 범위를 제한한다.

## 7. 구현 전에 팀에서 정할 사항

아래 항목은 지금 답변을 요구하는 질문이 아니라 향후 설계를 확정할 때 사용할 목록이다.

| 우선순위 | 미정 사항 | 결정의 영향 |
|---|---|---|
| 높음 | 북마크·좋아요 대상이 게시글만인지, 관광지·핫플까지인지 | 스키마, API, 담당자 범위 |
| 높음 | 자유게시판만 적용할지, 공지도 포함할지 | 서버 정책과 화면 노출 |
| 높음 | 내 북마크 목록을 어디에 둘지, 회원 담당자와 누가 연결할지 | 컨트롤러·마이페이지 변경 범위 |
| 높음 | 탈퇴·게시글 삭제 후 기록을 남길지 | 현재 CASCADE 정책 유지 또는 변경 |
| 보통 | 자기 글 좋아요, 북마크 공개 여부 | 허용 규칙과 조회 응답 |
| 보통 | 상세만 표시할지, 목록에도 표시할지 | 집계 쿼리와 응답 데이터 |
| 보통 | 좋아요 수 정렬·내 좋아요 목록이 필요한지 | 추가 조회·인덱스·요구사항 |
| 보통 | 기존 Servlet/JSP 방식으로 확장할지, 별도 전환 계획이 있는지 | 구현 형식. README의 Spring 전환 언급만으로 현재 전환을 가정하지 않음 |

## 8. 향후 구현 검증 항목

이번에는 테스트를 추가하거나 실행하지 않았다. 기존 테스트의 존재와 내용을 읽었으며 README의 “21개 통과” 기록을 이번 작업의 검증 결과로 사용하지 않는다.

향후에는 다음 사용자 동작과 데이터 규칙을 검증할 필요가 있다.

1. 비회원 변경 차단, 로그인 회원의 등록·해제, 타인 북마크 목록 접근 차단.
2. 같은 회원·글의 중복 등록 및 동시 등록 후 관계 1건 유지, 반복 해제의 일관성.
3. 서로 다른 회원의 좋아요 수와 회원별 선택 상태 구분.
4. 삭제된 글 요청, 게시글·회원 삭제와 관계 정리, 삭제와 등록이 겹치는 경우.
5. CSRF 실패·세션 만료·통신 실패 후 화면 상태 복구.
6. 내 북마크의 페이징·정렬·빈 목록, 여러 관계 JOIN 시 개수 정확성.
7. 기존 공지 권한·자유글 수정/삭제 권한·검색·상단 공지·조회수 동작 유지.

기존 BoardServiceTest 4개는 DB 없는 권한·입력 검증 중심이다. 게시글 저장·삭제, 실제 조회수, CSRF, 좋아요·북마크 동시성을 검증하는 테스트로 확대 해석하면 안 된다.

## 9. 조사 결과와 한계

- 게시판은 기존 CRUD 위에 좋아요·북마크를 확장할 수 있는 계층 구조를 갖고 있다.
- 두 신규 기능은 기존 요구사항 목록·게시판 코드·SQL에서 확인되지 않았다. 추천 알고리즘의 likedIds는 관광지 선호 입력이며 게시글 좋아요 저장 기능이 아니다.
- 저장소의 로컬 문서와 관련 코드만 근거로 삼았다. 외부 Notion·Drive·Figma의 별도 문서나 수업 원본 명세를 읽었다고 가정하지 않는다.
- 이 문서는 현황 조사와 미확정 검토사항을 기록한다. 새 요구사항 번호·스키마·API·작업 순서는 확정하지 않았다.
- 구현 파일과 기존 요구사항·README는 변경하지 않고 이 문서만 추가했다.
