// 백엔드가 없으므로 localStorage 를 간단한 DB 처럼 사용한다.
// 컬렉션 하나 = localStorage 키 하나 (JSON 배열)
const PREFIX = "enjoytrip.";

const read = (name) => {
  try {
    return JSON.parse(localStorage.getItem(PREFIX + name)) ?? [];
  } catch {
    return [];
  }
};

const write = (name, rows) => {
  try {
    localStorage.setItem(PREFIX + name, JSON.stringify(rows));
  } catch (e) {
    // 사진 dataURL 등으로 용량(약 5MB)을 넘기면 QuotaExceededError
    throw new Error("브라우저 저장 공간이 부족합니다. 사진 크기를 줄이거나 기존 데이터를 삭제해 주세요.", { cause: e });
  }
};

export const collection = (name) => ({
  all: () => read(name),
  find: (pred) => read(name).find(pred),
  filter: (pred) => read(name).filter(pred),
  get: (id) => read(name).find((row) => row.id === id),
  insert(row) {
    const rows = read(name);
    rows.push(row);
    write(name, rows);
    return row;
  },
  update(id, patch) {
    const rows = read(name);
    const idx = rows.findIndex((row) => row.id === id);
    if (idx < 0) return null;
    rows[idx] = { ...rows[idx], ...patch };
    write(name, rows);
    return rows[idx];
  },
  remove(id) {
    write(name, read(name).filter((row) => row.id !== id));
  },
  removeWhere(pred) {
    write(name, read(name).filter((row) => !pred(row)));
  },
});

export const users = collection("users");
export const posts = collection("posts");
export const hotplaces = collection("hotplaces");
export const plans = collection("plans");

/** 작성 중인 여행 계획 (저장 전 초안). 관광지 조회 화면에서 '계획에 담기'로 추가된다. */
export const draftPlan = {
  get() {
    try {
      return JSON.parse(localStorage.getItem(PREFIX + "draftPlan")) ?? null;
    } catch {
      return null;
    }
  },
  set(plan) {
    localStorage.setItem(PREFIX + "draftPlan", JSON.stringify(plan));
  },
  clear() {
    localStorage.removeItem(PREFIX + "draftPlan");
  },
};

/** 개인 설정 (내 차 커넥터 등) */
export const prefs = {
  get: (key, fallback = "") => localStorage.getItem(`${PREFIX}pref.${key}`) ?? fallback,
  set: (key, value) => localStorage.setItem(`${PREFIX}pref.${key}`, value),
};

export const session = {
  get: () => localStorage.getItem(PREFIX + "session"),
  set: (userId) => localStorage.setItem(PREFIX + "session", userId),
  clear: () => localStorage.removeItem(PREFIX + "session"),
};
