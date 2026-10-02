// F111 관광지 기간별 날씨 · F113 일출/일몰 시각
// Open-Meteo (키 불필요, 비상업 무료) 일별 예보 — 오늘부터 최대 16일
const END_POINT = "https://api.open-meteo.com/v1/forecast";
const cache = new Map();

// WMO 날씨 코드 → 아이콘·설명
const WMO = [
  [[0], "☀️", "맑음"],
  [[1, 2], "🌤️", "구름 조금"],
  [[3], "☁️", "흐림"],
  [[45, 48], "🌫️", "안개"],
  [[51, 53, 55, 56, 57], "🌦️", "이슬비"],
  [[61, 63, 65, 66, 67, 80, 81, 82], "🌧️", "비"],
  [[71, 73, 75, 77, 85, 86], "🌨️", "눈"],
  [[95, 96, 99], "⛈️", "뇌우"],
];
export const weatherInfo = (code) => {
  const hit = WMO.find(([codes]) => codes.includes(code));
  return hit ? { icon: hit[1], label: hit[2] } : { icon: "🌡️", label: "정보 없음" };
};

/**
 * 좌표의 일별 예보
 * @returns {Promise<Array<{date, code, max, min, rain, sunrise, sunset}>>}
 */
export const getForecast = async ({ x, y }, days = 16) => {
  const key = `${y.toFixed(2)},${x.toFixed(2)}`; // 약 1km 격자로 캐시
  if (!cache.has(key)) {
    const query = new URLSearchParams({
      latitude: y,
      longitude: x,
      daily: "weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max,sunrise,sunset",
      timezone: "Asia/Seoul",
      forecast_days: days,
    });
    cache.set(
      key,
      fetch(`${END_POINT}?${query}`)
        .then((r) => {
          if (!r.ok) throw new Error(`날씨 API 오류 (${r.status})`);
          return r.json();
        })
        .then(({ daily: d }) =>
          d.time.map((date, i) => ({
            date,
            code: d.weather_code[i],
            max: Math.round(d.temperature_2m_max[i]),
            min: Math.round(d.temperature_2m_min[i]),
            rain: d.precipitation_probability_max[i],
            sunrise: d.sunrise[i].slice(11, 16),
            sunset: d.sunset[i].slice(11, 16),
          }))
        )
        .catch((e) => {
          cache.delete(key); // 실패는 캐시하지 않는다
          throw e;
        })
    );
  }
  return cache.get(key);
};

/** 특정 날짜(YYYY-MM-DD) 예보. 예보 범위 밖이면 null */
export const getDayForecast = async (point, date) => (await getForecast(point)).find((d) => d.date === date) ?? null;

export const weekdayLabel = (date) => "일월화수목금토"[new Date(`${date}T00:00:00`).getDay()];
