// 카카오맵 SDK 동적 로딩 + 마커 레이어. (FE_08 실습의 kakao.js 를 확장)
import { KAKAO_MAP_KEY, hasKakaoKey } from "./config.js";
import { escapeHtml } from "./util.js";

let loading = null;

/** SDK 를 한 번만 로드하고 Promise 로 완료를 알린다. 키가 없거나 로드 실패 시 reject. */
export const loadKakaoMap = () => {
  if (window.kakao?.maps?.Map) return Promise.resolve();
  if (loading) return loading;
  loading = new Promise((resolve, reject) => {
    if (!hasKakaoKey()) {
      reject(new Error("카카오맵 키가 없습니다. .env 에 KAKAO_MAP_KEY 를 넣고 npm run env 를 실행해 주세요."));
      return;
    }
    const script = document.createElement("script");
    script.src = `https://dapi.kakao.com/v2/maps/sdk.js?appkey=${encodeURIComponent(KAKAO_MAP_KEY)}&autoload=false&libraries=services`;
    script.async = true;
    script.onload = () => kakao.maps.load(resolve);
    script.onerror = () => reject(new Error("카카오맵 SDK 를 불러오지 못했습니다. 키와 등록 도메인을 확인해 주세요."));
    document.head.appendChild(script);
  });
  loading.catch(() => (loading = null)); // 실패 시 재시도 가능
  return loading;
};

/** 지도 영역에 안내 메시지를 그린다 (키 미설정 등) */
export const showMapNotice = (container, message) => {
  container.classList.add("map-notice");
  container.innerHTML = `<div class="text-center p-4">
    <p class="fw-semibold mb-1">지도를 표시할 수 없습니다</p>
    <p class="small text-body-secondary mb-0">${escapeHtml(message)}</p></div>`;
};

/** 대한민국 범위를 벗어난 좌표(데이터 오류)는 걸러낸다 */
const inKorea = ({ x, y }) => 124.5 <= x && x <= 132 && 33.0 <= y && y <= 38.7;

const geocodeAddress = (geocoder, address) =>
  new Promise((resolve) => {
    geocoder.addressSearch(address, (result, status) => {
      if (status === kakao.maps.services.Status.OK) resolve({ x: Number(result[0].x), y: Number(result[0].y) });
      else resolve(null);
    });
  });

/**
 * 지도 + 마커 묶음을 관리하는 객체를 만든다.
 * 마커는 지도가 아니라 마커가 자기 지도를 가진다(marker.setMap) → 따로 배열로 들고 있어야 지울 수 있다.
 */
export const createMap = (container, { center = { y: 35.1595, x: 126.8526 }, level = 7 } = {}) => {
  const map = new kakao.maps.Map(container, {
    center: new kakao.maps.LatLng(center.y, center.x),
    level,
  });
  map.addControl(new kakao.maps.ZoomControl(), kakao.maps.ControlPosition.RIGHT);
  const geocoder = new kakao.maps.services.Geocoder();
  const markers = [];
  let polyline = null;
  const infoWindow = new kakao.maps.InfoWindow({ removable: true });

  const clear = () => {
    markers.forEach((m) => m.setMap(null));
    markers.length = 0;
    polyline?.setMap(null);
    polyline = null;
    infoWindow.close();
  };

  /** 주소만 있고 좌표가 없는 항목은 geocoder 로 좌표를 채운다 (FE_08 TODO 01-07) */
  const fillCoords = (infos) =>
    Promise.all(
      infos
        .filter((info) => !info.x || !info.y)
        .map(async (info) => {
          const coords = info.address ? await geocodeAddress(geocoder, info.address) : null;
          if (coords) Object.assign(info, coords);
          else console.warn("주소 → 좌표 변환 실패:", info.address);
        })
    );

  /**
   * 마커를 다시 그린다.
   * @param {Array<{x,y,title,address?}>} infos
   * @param {{numbered?:boolean, path?:boolean, onClick?:(info, idx)=>void, fit?:boolean}} opts
   */
  const render = async (infos, { numbered = false, path = false, pathPoints = null, onClick, fit = true } = {}) => {
    clear();
    await fillCoords(infos);
    const valid = infos.filter(inKorea);
    const bounds = new kakao.maps.LatLngBounds();

    valid.forEach((info, idx) => {
      const position = new kakao.maps.LatLng(info.y, info.x);
      bounds.extend(position);
      const marker = numbered
        ? new kakao.maps.CustomOverlay({
            map,
            position,
            yAnchor: 1,
            content: `<div class="num-marker" title="${escapeHtml(info.title)}"><span>${idx + 1}</span></div>`,
          })
        : new kakao.maps.Marker({ map, position, title: info.title });
      markers.push(marker);

      if (!numbered) {
        kakao.maps.event.addListener(marker, "click", () => {
          infoWindow.setContent(`<div class="map-iw"><strong>${escapeHtml(info.title)}</strong>
            ${info.address ? `<br><span>${escapeHtml(info.address)}</span>` : ""}</div>`);
          infoWindow.open(map, marker);
          onClick?.(info, idx);
        });
      }
    });

    if (path && valid.length > 1) {
      // pathPoints(실제 도로 좌표)가 있으면 실선, 없으면 정류지끼리 잇는 점선(직선거리)
      const roadPath = pathPoints?.length > 1;
      polyline = new kakao.maps.Polyline({
        map,
        path: (roadPath ? pathPoints : valid).map((i) => new kakao.maps.LatLng(i.y, i.x)),
        strokeWeight: roadPath ? 5 : 4,
        strokeColor: "#047857",
        strokeOpacity: 0.85,
        strokeStyle: roadPath ? "solid" : "shortdash",
      });
      if (roadPath) pathPoints.forEach((p) => bounds.extend(new kakao.maps.LatLng(p.y, p.x)));
    }

    if (fit && valid.length === 1) map.setCenter(new kakao.maps.LatLng(valid[0].y, valid[0].x));
    else if (fit && valid.length > 1) map.setBounds(bounds);
    return valid;
  };

  /** 특정 항목으로 이동하고 정보창을 연다 */
  const focus = (info) => {
    if (!info.x || !info.y) return;
    const position = new kakao.maps.LatLng(info.y, info.x);
    map.panTo(position);
    infoWindow.setContent(`<div class="map-iw"><strong>${escapeHtml(info.title)}</strong></div>`);
    infoWindow.setPosition(position);
    infoWindow.open(map);
  };

  /** 지도 크기가 바뀐 뒤(탭 전환, 레이아웃 변경) 호출 */
  const relayout = () => map.relayout();

  /** 좌표 → 시군구 코드 5자리 (법정동 코드 앞 5자리, 충전소 API 의 zscode 와 같다) */
  const regionCode = ({ x, y }) =>
    new Promise((resolve) => {
      geocoder.coord2RegionCode(x, y, (result, status) => {
        const b = status === kakao.maps.services.Status.OK && result.find((r) => r.region_type === "B");
        resolve(b ? b.code.slice(0, 5) : "");
      });
    });

  /** 충전소처럼 기본 마커와 따로 켜고 끄는 보조 레이어 */
  const createLayer = (className = "ev-marker", symbol = "⚡") => {
    const items = [];
    return {
      set(list, { onClick } = {}) {
        this.clear();
        list.forEach((info) => {
          const el = document.createElement("button");
          el.type = "button";
          el.className = className;
          el.title = info.title;
          el.setAttribute("aria-label", `${info.title} 충전소`);
          el.textContent = symbol;
          el.addEventListener("click", () => {
            infoWindow.setContent(info.html ?? `<div class="map-iw"><strong>${escapeHtml(info.title)}</strong></div>`);
            infoWindow.setPosition(new kakao.maps.LatLng(info.y, info.x));
            infoWindow.open(map);
            onClick?.(info);
          });
          items.push(new kakao.maps.CustomOverlay({ map, position: new kakao.maps.LatLng(info.y, info.x), content: el, yAnchor: 0.5 }));
        });
      },
      clear() {
        items.forEach((o) => o.setMap(null));
        items.length = 0;
      },
    };
  };

  /** 지도 클릭 위치를 받는다 (핫플 등록 등) */
  const onMapClick = (handler) =>
    kakao.maps.event.addListener(map, "click", (e) => handler({ x: e.latLng.getLng(), y: e.latLng.getLat() }));

  return {
    map, geocoder, render, focus, clear, relayout, regionCode, createLayer, onMapClick,
    geocode: (addr) => geocodeAddress(geocoder, addr),
  };
};

/** 두 좌표 사이 직선거리(km) — 여행 계획의 이동 거리 표시용 */
export const distanceKm = (a, b) => {
  const R = 6371;
  const rad = (d) => (d * Math.PI) / 180;
  const dLat = rad(b.y - a.y);
  const dLng = rad(b.x - a.x);
  const h = Math.sin(dLat / 2) ** 2 + Math.cos(rad(a.y)) * Math.cos(rad(b.y)) * Math.sin(dLng / 2) ** 2;
  return 2 * R * Math.asin(Math.sqrt(h));
};
