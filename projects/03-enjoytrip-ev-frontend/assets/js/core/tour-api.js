// 한국관광공사 국문 관광정보 서비스_GW (KorService2) 래퍼.
// 키가 없거나 요청이 실패하면 data/dummy-tour.json 의 더미 데이터로 대체한다.
import { DATA_GO_KR_API_KEY, hasTourKey } from "./config.js";

const END_POINT = "https://apis.data.go.kr/B551011/KorService2";

export const CONTENT_TYPES = [
  { id: "", label: "전체" },
  { id: "12", label: "관광지" },
  { id: "32", label: "숙박" },
  { id: "39", label: "음식점" },
  { id: "14", label: "문화시설" },
  { id: "15", label: "축제·공연·행사" },
  { id: "25", label: "여행코스" },
  { id: "38", label: "쇼핑" },
  { id: "28", label: "레포츠" },
];

export const contentTypeLabel = (id) => CONTENT_TYPES.find((t) => t.id === String(id))?.label ?? "기타";

const commonParams = () => ({
  serviceKey: DATA_GO_KR_API_KEY,
  MobileOS: "WEB",
  MobileApp: "EnjoyTrip",
  _type: "json",
});

let dummyCache = null;
const loadDummy = async () => {
  dummyCache ??= await fetch("data/dummy-tour.json").then((r) => r.json());
  return dummyCache;
};

/** 실제 API 호출. 공공데이터포털은 오류 시에도 200 + XML 을 주는 경우가 있어 JSON 파싱 실패도 오류로 본다. */
const request = async (operation, params) => {
  const query = new URLSearchParams({ ...commonParams(), ...params });
  const res = await fetch(`${END_POINT}/${operation}?${query}`);
  if (!res.ok) throw new Error(`관광 API 오류 (${res.status})`);
  const text = await res.text();
  let json;
  try {
    json = JSON.parse(text);
  } catch {
    throw new Error("관광 API 응답을 해석할 수 없습니다. 인증키를 확인해 주세요.");
  }
  const header = json.response?.header;
  if (header && header.resultCode !== "0000") throw new Error(`관광 API 오류: ${header.resultMsg}`);
  const body = json.response?.body ?? {};
  const item = body.items?.item ?? [];
  return { items: Array.isArray(item) ? item : [item], totalCount: Number(body.totalCount ?? 0) };
};

/** 데이터 소스 상태 — 화면 배지에 표시 */
export const dataSource = { mode: hasTourKey() ? "api" : "dummy", lastError: null };

const withFallback = async (apiCall, dummyCall) => {
  if (dataSource.mode === "api") {
    try {
      return await apiCall();
    } catch (e) {
      console.warn("[tour-api] API 실패, 더미 데이터로 전환:", e);
      dataSource.mode = "dummy";
      dataSource.lastError = e.message;
    }
  }
  return dummyCall(await loadDummy());
};

/** 시도 목록: [{code, name}] */
export const getSidoList = () =>
  withFallback(
    async () => (await request("ldongCode2", { numOfRows: 50, pageNo: 1, lDongListYn: "N" })).items,
    (d) => d.sido
  );

/** 시군구 목록: [{code, name}] */
export const getGugunList = (sidoCode) =>
  withFallback(
    async () =>
      (await request("ldongCode2", { numOfRows: 100, pageNo: 1, lDongListYn: "N", lDongRegnCd: sidoCode })).items,
    (d) => d.gugun[sidoCode] ?? []
  );

/** API 응답 → 화면에서 쓰는 공통 형태 */
export const normalizeSpot = (item) => ({
  id: String(item.contentid),
  contentTypeId: String(item.contenttypeid ?? ""),
  title: item.title,
  address: [item.addr1, item.addr2].filter(Boolean).join(" "),
  tel: item.tel ?? "",
  // 공공누리 1·3유형(Type1/Type3)만 출처표시 조건으로 이용 가능 → 그 외는 사진을 쓰지 않는다
  // 이미지 URL 이 http:// 로 오므로 https 배포 시 혼합 콘텐츠로 막히지 않게 https 로 바꾼다
  image: ["Type1", "Type3"].includes(item.cpyrhtDivCd) || !item.cpyrhtDivCd ? (item.firstimage || "").replace(/^http:\/\//, "https://") : "",
  x: Number(item.mapx) || null,
  y: Number(item.mapy) || null,
  sidoCode: String(item.lDongRegnCd ?? ""),
});

/**
 * 지역/콘텐츠 타입/키워드 기반 관광지 조회
 * @param {{sido?:string, gugun?:string, contentTypeId?:string, keyword?:string, page?:number, size?:number}} q
 */
export const searchSpots = ({ sido = "", gugun = "", contentTypeId = "", keyword = "", page = 1, size = 12 } = {}) =>
  withFallback(
    async () => {
      const params = { numOfRows: size, pageNo: page, arrange: "Q" }; // Q: 이미지 있는 것 우선 + 수정일순
      if (sido) params.lDongRegnCd = sido;
      if (gugun) params.lDongSignguCd = gugun;
      if (contentTypeId) params.contentTypeId = contentTypeId;
      const { items, totalCount } = keyword
        ? await request("searchKeyword2", { ...params, keyword })
        : await request("areaBasedList2", params);
      return { items: items.map(normalizeSpot), totalCount };
    },
    (d) => {
      const filtered = d.spots
        .filter((s) => !sido || s.lDongRegnCd === sido)
        .filter((s) => !gugun || s.lDongSignguCd === gugun)
        .filter((s) => !contentTypeId || s.contenttypeid === contentTypeId)
        .filter((s) => !keyword || s.title.includes(keyword) || s.addr1.includes(keyword));
      const start = (page - 1) * size;
      return { items: filtered.slice(start, start + size).map(normalizeSpot), totalCount: filtered.length };
    }
  );

/** F112 관광지 추천 사진 (공공누리 1·3유형만) */
// 사진 조회 실패가 전체를 더미 모드로 바꾸지 않도록 withFallback 을 쓰지 않는다
export const getSpotImages = async (contentId) => {
  if (dataSource.mode !== "api") return []; // 더미 데이터는 사진을 포함하지 않는다
  try {
    const { items } = await request("detailImage2", { contentId, imageYN: "Y", numOfRows: 20, pageNo: 1 });
    return items
      .filter((i) => i.originimgurl && ["Type1", "Type3"].includes(i.cpyrhtDivCd))
      .map((i) => ({
        url: i.originimgurl.replace(/^http:\/\//, "https://"),
        thumb: (i.smallimageurl || i.originimgurl).replace(/^http:\/\//, "https://"),
        name: i.imgname ?? "",
        license: i.cpyrhtDivCd === "Type1" ? "공공누리 제1유형(출처표시)" : "공공누리 제3유형(출처표시·변경금지)",
      }));
  } catch (e) {
    console.warn("[tour-api] 사진 조회 실패:", e);
    return [];
  }
};

/** 상세 개요 (overview) */
export const getSpotDetail = (contentId) =>
  withFallback(
    async () => {
      const { items } = await request("detailCommon2", { contentId });
      return { overview: items[0]?.overview ?? "", homepage: items[0]?.homepage ?? "" };
    },
    (d) => ({ overview: d.spots.find((s) => s.contentid === contentId)?.overview ?? "", homepage: "" })
  );
