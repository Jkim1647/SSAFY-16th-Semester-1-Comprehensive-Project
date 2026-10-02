// 키는 .env 에서 관리하고 scripts/env.mjs 가 keys.js 를 생성한다.
// keys.js 가 없어도 페이지 전체가 죽지 않도록 동적 import 로 읽어온다.
// (정적 import 는 파일이 없으면 모듈 그래프 전체가 로드 실패한다)
let keys = {};
try {
  keys = await import("../keys.js");
} catch {
  console.warn("[EnjoyTrip] assets/js/keys.js 가 없습니다 (.env 작성 후 npm run env). 더미 데이터 모드로 동작합니다.");
}

export const DATA_GO_KR_API_KEY = keys.DATA_GO_KR_API_KEY || "";
export const KAKAO_MAP_KEY = keys.KAKAO_MAP_KEY || "";
export const KAKAO_REST_KEY = keys.KAKAO_REST_KEY || ""; // 선택: 실제 도로 경로
// 선택: 충전소 API 를 다른 계정으로 승인받은 경우. 없으면 공공데이터포털 공통 키를 쓴다
export const EV_API_KEY = keys.EV_API_KEY || DATA_GO_KR_API_KEY;

export const hasTourKey = () => DATA_GO_KR_API_KEY.length > 0;
export const hasKakaoKey = () => KAKAO_MAP_KEY.length > 0;
