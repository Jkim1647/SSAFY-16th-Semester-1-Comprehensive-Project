# 통합 게시판 구현 계획

> 실행 방식: 사용자 구현 요청에 따라 현재 세션에서 executing-plans와 test-driven-development 지침을 적용한다.

**Goal:** 기존 게시판 호환성을 유지하며 높음·보통 기능과 공지 고정을 구현한다.
**Architecture:** 기존 MVC/JDBC 유지, 추가 테이블과 게시판 전용 DAO·서비스, 기존 컨트롤러/JSP 확장.
**Tech Stack:** Java 17, Servlet 6.1, JSP/JSTL, MySQL, JUnit 5.
**Spec:** ../specs/2026-10-30-board-community-design.md

## 제약

기존 공지 ID/유형/권한 유지. 신규 라이브러리 불필요. 기존 데이터 삭제·변환 없음. 새 브랜치 codex/board-community. 최초 커밋이 없는 저장소이므로 기존 파일을 일괄 커밋하지 않음.

## 검토 초점

숨김 글 우회 접근, 다른 회원 활동 노출, 중복 요청 경쟁, 삭제와 등록 경쟁, 공지 기간 경계와 검색 복귀 주소.

## 작업

- [x] 1. 실제 DB 통합 테스트를 먼저 작성하고 기능 부재로 실패함을 확인한다. 테스트는 별도 임의 이름 스키마를 사용하고 종료 시 해당 스키마만 정리한다.
- [x] 2. sql/45-board-community.sql, BoardQuery·BoardEntry·BoardComment DTO, BoardCommunityDao/JdbcBoardCommunityDao 및 Service를 추가한다. 기존 DBUtil을 이용하되 테스트 스키마 연결은 DAO 생성자에 Connection 공급자로 주입한다.
- [x] 3. 통합 조회·분류·정렬·고정·좋아요·북마크·댓글·활동·신고·숨김을 구현하고 MySQL 통합 테스트를 통과시킨다.
- [x] 4. BoardController와 board JSP, board.js를 연결한다. 헤더의 두 메뉴를 게시판 하나로 바꾸고 마이페이지에 활동 링크를 제공한다. JSP의 상태 변경은 CSRF 폼과 PRG, 반응은 복구 가능한 fetch를 사용한다.
- [x] 5. 전체 테스트·WAR 빌드·JSP 및 HTTP 검증, 독립 리뷰, 문서 업데이트. 원본과 변경 비교로 관련 없는 수정 여부 확인.

## 검증 명령

`./mvnw.cmd test` / `./mvnw.cmd package` / 임시 Tomcat 인스턴스와 격리 DB로 HTTP 검증. 실패 시 원인을 추적하고 재현 테스트로 보완한다.

## 실행 기록

- 원본: 최초 커밋 없는 master의 로컬 파일을 임시 경로에 복사한 뒤 codex/board-community에서 작업. 사용자 기존 파일을 일괄 스테이징·커밋하지 않음.
- 신규 테스트의 기능 부재 실패 확인 후 스키마·서비스 구현. 실제 DB에서 권한·반응·댓글·신고·고정 검증.
- 독립 검토의 URL 호환·UTC 세션 시간·오류 처리·댓글 페이지 문제를 재현 테스트 후 보완.
- 기존 DAO 인터페이스+Jdbc 구현 패턴에 맞춰 추가 DAO를 분리. 목록 본문 생략, 바인딩, 쿼리 수 검증.
- 소규모 부하 1만 건·독자8·32회 p95 최종 224ms. 운영 SLA로 해석하지 않음.
- Ruling: 새 브랜치 요청에 따라 현재 체크아웃을 재사용. 최초 커밋이 없고 사용자 요청이 브랜치 생성이므로 새 worktree·초기 일괄 커밋은 생성하지 않음.
- Ruling: 사용자가 앞선 기능 제안을 선택하고 구현을 명시적으로 요청했으므로 추가 승인 질문 없이 문서화 후 직접 실행.

- 최종 clean WAR 빌드 성공, Java/MySQL/HTTP 51개 통과(실패·오류·건너뜀0), Node UI 3개 통과. 새 문서 링크 및 원본 변경 범위 확인.
