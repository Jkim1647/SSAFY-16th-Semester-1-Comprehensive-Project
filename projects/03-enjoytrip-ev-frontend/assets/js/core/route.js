// 실제 도로 경로 — 카카오모빌리티 다중 경유지 길찾기
// REST API 키(KAKAO_REST_KEY)가 있을 때만 쓰고, 없거나 실패하면 null → 호출부가 직선거리로 대체한다.
// ※ 프론트 단독 과제라 REST 키가 브라우저에 노출된다. 실서비스라면 서버를 거쳐 호출해야 한다.
import { KAKAO_REST_KEY } from "./config.js";

const END_POINT = "https://apis-navi.kakaomobility.com/v1/waypoints/directions";
const MAX_WAYPOINTS = 30;
const cache = new Map();

export const hasRouteKey = () => KAKAO_REST_KEY.length > 0;

/**
 * @param {Array<{x:number,y:number}>} stops 2곳 이상
 * @returns {Promise<null | {distanceKm:number, durationMin:number, legs:Array<{distanceKm,durationMin}>, path:Array<{x,y}>}>}
 */
export const getRoute = async (stops) => {
  const points = stops.filter((s) => s.x && s.y);
  if (!hasRouteKey() || points.length < 2 || points.length - 2 > MAX_WAYPOINTS) return null;

  const key = points.map((p) => `${p.x.toFixed(5)},${p.y.toFixed(5)}`).join("|");
  if (cache.has(key)) return cache.get(key);

  const xy = ({ x, y }) => ({ x: String(x), y: String(y) });
  try {
    const res = await fetch(END_POINT, {
      method: "POST",
      headers: { Authorization: `KakaoAK ${KAKAO_REST_KEY}`, "Content-Type": "application/json" },
      body: JSON.stringify({
        origin: xy(points[0]),
        destination: xy(points.at(-1)),
        waypoints: points.slice(1, -1).map(xy),
        priority: "RECOMMEND",
      }),
    });
    if (!res.ok) throw new Error(`길찾기 API 오류 (${res.status})`);
    const route = (await res.json()).routes?.[0];
    if (!route || route.result_code !== 0) throw new Error(route?.result_msg ?? "경로를 찾지 못했습니다.");

    const path = [];
    route.sections.forEach((sec) =>
      sec.roads.forEach((road) => {
        for (let i = 0; i < road.vertexes.length; i += 2) path.push({ x: road.vertexes[i], y: road.vertexes[i + 1] });
      })
    );
    const result = {
      distanceKm: route.summary.distance / 1000,
      durationMin: Math.round(route.summary.duration / 60),
      legs: route.sections.map((s) => ({ distanceKm: s.distance / 1000, durationMin: Math.round(s.duration / 60) })),
      path,
    };
    cache.set(key, result);
    return result;
  } catch (e) {
    console.warn("[route] 도로 경로 조회 실패, 직선거리로 대체:", e);
    return null;
  }
};
