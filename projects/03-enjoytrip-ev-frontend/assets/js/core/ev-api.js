// F114 전기자동차 충전소 — 한국환경공단 「전기자동차 충전소 정보」 (data.go.kr, B552584/EvCharger)
// 공공데이터포털 인증키(EV_API_KEY, 없으면 공통 키)를 쓰며, 별도로 활용 신청이 필요하다.
// 호출 실패(미신청·CORS 등) 시 data/dummy-ev.json 으로 대체한다.
import { EV_API_KEY } from "./config.js";
import { distanceKm } from "./kakao.js";

const END_POINT = "https://apis.data.go.kr/B552584/EvCharger/getChargerInfo";

export const CHARGER_TYPES = {
  "01": "DC차데모",
  "02": "AC완속",
  "03": "DC차데모+AC3상",
  "04": "DC콤보",
  "05": "DC차데모+DC콤보",
  "06": "DC차데모+AC3상+DC콤보",
  "07": "AC3상",
  "08": "DC콤보(완속)",
  "09": "NACS",
  "10": "DC콤보+NACS",
};

/** 커넥터 필터 (마이페이지에서 고른 내 차 커넥터) → 해당 커넥터를 지원하는 chgerType 목록 */
export const CONNECTORS = {
  "": { label: "전체", types: null },
  combo: { label: "DC콤보", types: ["04", "05", "06", "08", "10"] },
  chademo: { label: "DC차데모", types: ["01", "03", "05", "06"] },
  ac3: { label: "AC3상", types: ["03", "06", "07"] },
  slow: { label: "AC완속", types: ["02"] },
};

export const STATUS = {
  1: { label: "통신이상", tone: "secondary" },
  2: { label: "충전대기", tone: "success" },
  3: { label: "충전중", tone: "warning" },
  4: { label: "운영중지", tone: "secondary" },
  5: { label: "점검중", tone: "secondary" },
  9: { label: "상태미확인", tone: "secondary" },
};

const isFast = (type) => type !== "02" && type !== "08";

// mode: API 를 시도할지 여부, lastUsed: 직전 조회가 실제로 어느 데이터를 썼는지 (화면 배지용)
export const evSource = { mode: EV_API_KEY ? "api" : "dummy", lastUsed: "dummy", lastError: null };

let dummyCache = null;
const loadDummy = async () => (dummyCache ??= (await fetch("data/dummy-ev.json").then((r) => r.json())).items);

const apiCache = new Map(); // zscode → items

const fetchByRegion = async (zscode) => {
  if (apiCache.has(zscode)) return apiCache.get(zscode);
  const query = new URLSearchParams({
    serviceKey: EV_API_KEY,
    pageNo: 1,
    numOfRows: 3000,
    zcode: zscode.slice(0, 2),
    zscode,
    dataType: "JSON",
  });
  const res = await fetch(`${END_POINT}?${query}`);
  if (!res.ok) throw new Error(`충전소 API 오류 (${res.status})`);
  let json;
  try {
    json = JSON.parse(await res.text());
  } catch {
    throw new Error("충전소 API 응답을 해석할 수 없습니다. 활용 신청 여부를 확인해 주세요.");
  }
  if (json.resultCode && json.resultCode !== "00") throw new Error(`충전소 API 오류: ${json.resultMsg}`);
  const item = json.items?.item ?? [];
  const items = Array.isArray(item) ? item : [item];
  apiCache.set(zscode, items);
  return items;
};

/** 충전기(행) 단위 응답을 충전소(statId) 단위로 묶는다 */
const groupStations = (rows) => {
  const map = new Map();
  rows
    .filter((r) => r.delYn !== "Y")
    .forEach((r) => {
      const st = map.get(r.statId) ?? {
        id: r.statId,
        title: r.statNm,
        address: r.addr,
        x: Number(r.lng),
        y: Number(r.lat),
        useTime: r.useTime ?? "",
        operator: r.busiNm ?? "",
        parkingFree: r.parkingFree === "Y",
        chargers: [],
      };
      st.chargers.push({ id: r.chgerId, type: r.chgerType, typeLabel: CHARGER_TYPES[r.chgerType] ?? "기타", stat: Number(r.stat), output: Number(r.output) || null });
      map.set(r.statId, st);
    });
  return [...map.values()].map((st) => ({
    ...st,
    fastCount: st.chargers.filter((c) => isFast(c.type)).length,
    availableCount: st.chargers.filter((c) => c.stat === 2).length,
  }));
};

const matchesConnector = (station, connector) => {
  const types = CONNECTORS[connector]?.types;
  return !types || station.chargers.some((c) => types.includes(c.type));
};

/**
 * 좌표 주변 충전소 (가까운 순)
 * @param {{x:number,y:number}} point
 * @param {{radiusKm?:number, regionCode?:string, connector?:string}} opts regionCode: 시군구 5자리(법정동 코드 앞 5자리)
 */
export const findChargersNear = async (point, { radiusKm = 3, regionCode = "", connector = "" } = {}) => {
  let rows;
  if (evSource.mode === "api" && regionCode) {
    try {
      rows = await fetchByRegion(regionCode);
    } catch (e) {
      console.warn("[ev-api] API 실패, 더미 데이터로 전환:", e);
      evSource.mode = "dummy";
      evSource.lastError = e.message;
    }
  }
  evSource.lastUsed = rows ? "api" : "dummy"; // 지역코드가 없으면(지도 키 없음) API 를 건너뛴다
  rows ??= await loadDummy();
  return groupStations(rows)
    .map((st) => ({ ...st, distance: distanceKm(point, st) }))
    .filter((st) => st.distance <= radiusKm && matchesConnector(st, connector))
    .sort((a, b) => a.distance - b.distance);
};
