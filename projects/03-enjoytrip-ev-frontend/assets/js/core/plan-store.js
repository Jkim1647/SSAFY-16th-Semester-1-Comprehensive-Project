// 여행 계획 초안 다루기 (관광지 조회 화면과 여행 계획 화면이 함께 쓴다)
import { draftPlan } from "./storage.js";
import { uid } from "./util.js";

export const emptyPlan = () => ({
  id: null, // 저장 전에는 null
  title: "",
  startDate: new Date().toISOString().slice(0, 10),
  rangeKm: 300,
  efficiency: 5.0, // 전비 km/kWh (예시 기본값)
  pricePerKwh: 347, // 충전 단가 원/kWh (예시 기본값, 사용자가 수정)
  stops: [],
});

/** 장소별 경비 항목 (충전비는 이동 거리로 자동 계산하므로 여기 없다) */
export const EXPENSE_CATEGORIES = [
  { id: "ticket", label: "입장료" },
  { id: "food", label: "식비" },
  { id: "stay", label: "숙박" },
  { id: "etc", label: "기타" },
];

const DEFAULT_CATEGORY = { 12: "ticket", 14: "ticket", 15: "ticket", 28: "ticket", 39: "food", 32: "stay" };
export const defaultCategory = (contentTypeId) => DEFAULT_CATEGORY[contentTypeId] ?? "etc";

export const loadDraft = () => ({ ...emptyPlan(), ...(draftPlan.get() ?? {}) });
export const saveDraft = (plan) => draftPlan.set(plan);

/** 관광지 하나를 초안 끝에 추가. 이미 있으면 false */
export const addSpotToDraft = (spot) => {
  const plan = loadDraft();
  if (plan.stops.some((s) => s.spotId === spot.id)) return false;
  plan.stops.push({
    key: uid(),
    spotId: spot.id,
    title: spot.title,
    address: spot.address,
    x: spot.x,
    y: spot.y,
    contentTypeId: spot.contentTypeId,
    time: "",
    cost: 0,
    category: defaultCategory(spot.contentTypeId),
    memo: "",
  });
  saveDraft(plan);
  return true;
};
