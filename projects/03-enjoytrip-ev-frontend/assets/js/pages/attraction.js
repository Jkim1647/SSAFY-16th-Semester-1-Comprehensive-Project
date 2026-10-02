// F101~F103 지역별·콘텐츠 유형별 관광지 조회 + 지도 표시
import { $, escapeHtml, getParam, toast } from "../core/util.js";
import { CONTENT_TYPES, contentTypeLabel, dataSource, getGugunList, getSidoList, getSpotDetail, getSpotImages, searchSpots } from "../core/tour-api.js";
import { getForecast, weatherInfo, weekdayLabel } from "../core/weather.js";
import { createMap, loadKakaoMap, showMapNotice } from "../core/kakao.js";
import { evSource, findChargersNear } from "../core/ev-api.js";
import { addSpotToDraft } from "../core/plan-store.js";
import { prefs } from "../core/storage.js";

const PAGE_SIZE = 12;
const NEAR_KM = 1;
let evLayer = null;
const els = {
  form: $("#search-form"),
  keyword: $("#keyword"),
  sido: $("#sido"),
  gugun: $("#gugun"),
  chips: $("#type-chips"),
  list: $("#spot-list"),
  meta: $("#result-meta"),
  more: $("#btn-more"),
  badge: $("#source-badge"),
  mapEl: $("#map"),
};

const state = { query: {}, page: 1, items: [], total: 0 };
let mapView = null;

/* ---------- 검색 조건 UI ---------- */
const fillSelect = (select, rows, allLabel) => {
  select.innerHTML =
    `<option value="">${allLabel}</option>` +
    rows.map((r) => `<option value="${escapeHtml(r.code)}">${escapeHtml(r.name)}</option>`).join("");
};

const renderChips = (selected = "") => {
  els.chips.innerHTML = CONTENT_TYPES.map(
    (t) => `<input type="radio" class="btn-check" name="type" id="type-${t.id || "all"}" value="${t.id}"
        ${t.id === selected ? "checked" : ""} autocomplete="off">
      <label class="btn btn-outline-secondary" for="type-${t.id || "all"}">${t.label}</label>`
  ).join("");
};

const readQuery = () => ({
  keyword: els.keyword.value.trim(),
  sido: els.sido.value,
  gugun: els.gugun.value,
  contentTypeId: els.chips.querySelector("input:checked")?.value ?? "",
});

/* ---------- 결과 목록 ---------- */
const itemHtml = (spot, idx) => `
  <li>
    <button type="button" class="spot-item" data-idx="${idx}">
      ${spot.image
        ? `<img class="spot-thumb" src="${escapeHtml(spot.image)}" alt="" loading="lazy">`
        : `<span class="spot-thumb" aria-hidden="true">${escapeHtml(contentTypeLabel(spot.contentTypeId))}</span>`}
      <span>
        <span class="type-tag">${escapeHtml(contentTypeLabel(spot.contentTypeId))}</span>
        <p class="spot-title">${escapeHtml(spot.title)}</p>
        <p class="spot-addr">${escapeHtml(spot.address || "주소 정보 없음")}</p>
      </span>
      <span class="text-body-secondary small" aria-hidden="true">›</span>
    </button>
  </li>`;

const renderList = () => {
  if (state.items.length === 0) {
    els.list.innerHTML = `<li class="empty-state">조건에 맞는 관광지가 없습니다.<br>지역이나 유형을 바꿔 보세요.</li>`;
  } else {
    els.list.innerHTML = state.items.map(itemHtml).join("");
  }
  els.meta.textContent = `검색 결과 ${state.total.toLocaleString()}건`;
  els.more.classList.toggle("d-none", state.items.length >= state.total);
  els.badge.textContent = dataSource.mode === "api" ? "실시간 관광공사 데이터" : "더미 데이터 모드";
  els.badge.title = dataSource.lastError ?? "";
};

const search = async ({ append = false } = {}) => {
  if (!append) {
    state.query = readQuery();
    state.page = 1;
  }
  els.meta.textContent = "불러오는 중…";
  els.form.querySelector("button[type=submit]").disabled = true;
  try {
    const { items, totalCount } = await searchSpots({ ...state.query, page: state.page, size: PAGE_SIZE });
    state.items = append ? [...state.items, ...items] : items;
    state.total = totalCount;
    renderList();
    mapView?.render(state.items, { onClick: (_, idx) => highlight(idx) });
  } catch (e) {
    console.error(e);
    els.meta.textContent = "";
    els.list.innerHTML = `<li class="empty-state text-danger">조회 중 오류가 발생했습니다.<br>${escapeHtml(e.message)}</li>`;
  } finally {
    els.form.querySelector("button[type=submit]").disabled = false;
  }
};

const highlight = (idx) => {
  els.list.querySelectorAll(".spot-item").forEach((b) => b.classList.toggle("is-active", Number(b.dataset.idx) === idx));
  els.list.querySelector(`[data-idx="${idx}"]`)?.scrollIntoView({ block: "nearest" });
};

/* ---------- 상세 (오프캔버스) ---------- */
const detail = bootstrap.Offcanvas.getOrCreateInstance("#detail");

// 관광공사 overview 에는 <br> 같은 태그가 섞여 온다 → 태그를 줄바꿈/제거 후 텍스트로만 출력
const toPlainText = (html) => html.replace(/<br\s*\/?>/gi, "\n").replace(/<[^>]*>/g, "").trim();

let detailId = null; // 지금 열려 있는 상세의 관광지 — 늦게 도착한 이전 응답이 덮어쓰지 않게
const stale = (spot) => detailId !== spot.id;

const openDetail = async (spot) => {
  detailId = spot.id;
  $("#detail-title").textContent = spot.title;
  const body = $("#detail-body");
  body.innerHTML = `
    ${spot.image ? `<img src="${escapeHtml(spot.image)}" alt="${escapeHtml(spot.title)} 사진" class="img-fluid rounded mb-3">` : ""}
    <span class="type-tag">${escapeHtml(contentTypeLabel(spot.contentTypeId))}</span>
    <dl class="row small mt-2">
      <dt class="col-3">주소</dt><dd class="col-9">${escapeHtml(spot.address || "-")}</dd>
      <dt class="col-3">전화</dt><dd class="col-9">${escapeHtml(spot.tel || "-")}</dd>
    </dl>
    <div class="d-flex gap-2 mb-4">
      <button type="button" class="btn btn-primary flex-fill" id="btn-add-plan">여행 계획에 담기</button>
      <a class="btn btn-outline-primary" href="plan.html">계획 보기</a>
    </div>
    <h3 class="h6 fw-bold">소개</h3>
    <p class="small text-body-secondary" id="detail-overview">불러오는 중…</p>
    <h3 class="h6 fw-bold mt-4">날씨 <span class="fw-normal small text-body-secondary">(7일 예보)</span></h3>
    <div id="detail-weather" class="small text-body-secondary">불러오는 중…</div>
    <h3 class="h6 fw-bold mt-4">사진</h3>
    <div id="detail-photos" class="small text-body-secondary">불러오는 중…</div>
    <h3 class="h6 fw-bold mt-4">⚡ 주변 충전소 <span class="fw-normal small text-body-secondary">(${NEAR_KM}km 이내)</span></h3>
    <ul class="list-unstyled small" id="detail-ev"><li class="text-body-secondary">불러오는 중…</li></ul>`;
  $("#btn-add-plan").addEventListener("click", () => {
    const added = addSpotToDraft(spot);
    toast(added ? `'${spot.title}'을(를) 여행 계획에 담았습니다.` : "이미 계획에 담긴 장소입니다.", added ? "success" : "dark");
  });
  detail.show();
  renderNearbyChargers(spot);
  renderWeather(spot);
  renderPhotos(spot);
  try {
    const { overview } = await getSpotDetail(spot.id);
    if (stale(spot)) return;
    $("#detail-overview").textContent = toPlainText(overview) || "소개 정보가 없습니다.";
    $("#detail-overview").style.whiteSpace = "pre-line";
  } catch {
    $("#detail-overview").textContent = "소개 정보를 불러오지 못했습니다.";
  }
};

/** F111 기간별 날씨 + F113 일출·일몰 */
const renderWeather = async (spot) => {
  const box = $("#detail-weather");
  if (!spot.x || !spot.y) {
    box.textContent = "위치 정보가 없어 날씨를 알 수 없습니다.";
    return;
  }
  try {
    const days = (await getForecast(spot)).slice(0, 7);
    if (stale(spot)) return;
    const today = days[0];
    box.innerHTML = `
      <div class="forecast mb-2">
        ${days
          .map((d) => {
            const w = weatherInfo(d.code);
            return `<div title="${escapeHtml(w.label)} · 강수확률 ${d.rain ?? 0}%">
              <div>${d.date === today.date ? "오늘" : weekdayLabel(d.date)}</div>
              <div class="icon" aria-hidden="true">${w.icon}</div>
              <div class="max">${d.max}°</div><div class="min">${d.min}°</div>
              <span class="visually-hidden">${escapeHtml(w.label)}, 강수확률 ${d.rain ?? 0}%</span>
            </div>`;
          })
          .join("")}
      </div>
      <p class="mb-0">🌅 오늘 일출 ${today.sunrise} · 🌇 일몰 ${today.sunset}</p>
      <p class="form-hint mb-0">날씨: Open-Meteo</p>`;
  } catch {
    box.textContent = "날씨 정보를 불러오지 못했습니다.";
  }
};

/** F112 관광지 추천 사진 (공공누리 1·3유형) */
const renderPhotos = async (spot) => {
  const box = $("#detail-photos");
  const photos = await getSpotImages(spot.id);
  if (stale(spot)) return;
  if (photos.length === 0) {
    box.textContent = dataSource.mode === "api" ? "등록된 사진이 없습니다." : "더미 데이터 모드에서는 사진을 표시하지 않습니다.";
    return;
  }
  box.innerHTML = `
    <div class="gallery mb-1">
      ${photos
        .slice(0, 9)
        .map(
          (p) => `<a href="${escapeHtml(p.url)}" target="_blank" rel="noopener noreferrer" title="${escapeHtml(p.name)}">
            <img src="${escapeHtml(p.thumb)}" alt="${escapeHtml(p.name || spot.title)}" loading="lazy"></a>`
        )
        .join("")}
    </div>
    <p class="form-hint mb-0">출처: 한국관광공사 · ${escapeHtml(photos[0].license)}</p>`;
};

const renderNearbyChargers = async (spot) => {
  const box = $("#detail-ev");
  if (!spot.x || !spot.y) {
    box.innerHTML = `<li class="text-body-secondary">위치 정보가 없어 충전소를 찾을 수 없습니다.</li>`;
    return;
  }
  try {
    const regionCode = mapView ? await mapView.regionCode(spot) : "";
    const stations = await findChargersNear(spot, { radiusKm: NEAR_KM, regionCode, connector: prefs.get("connector") });
    if (stale(spot)) return;
    evLayer?.set(stations);
    box.innerHTML = stations.length
      ? stations
          .slice(0, 5)
          .map(
            (st) => `<li class="py-2 border-bottom">
              <strong>${escapeHtml(st.title)}</strong> <span class="text-body-secondary">${st.distance.toFixed(1)}km</span><br>
              <span class="text-body-secondary">급속 ${st.fastCount}기 · 사용 가능 ${st.availableCount}기 · ${escapeHtml(st.useTime)}</span>
            </li>`
          )
          .join("")
      : `<li class="text-body-secondary">반경 ${NEAR_KM}km 안에 충전소가 없습니다.</li>`;
    if (evSource.lastUsed === "dummy") box.insertAdjacentHTML("beforeend", `<li class="text-body-secondary mt-2">※ 예시(더미) 충전소 데이터입니다.</li>`);
  } catch (e) {
    box.innerHTML = `<li class="text-danger">충전소 정보를 불러오지 못했습니다.</li>`;
  }
};

/* ---------- 이벤트 ---------- */
els.form.addEventListener("submit", (e) => {
  e.preventDefault();
  search();
});

els.sido.addEventListener("change", async () => {
  fillSelect(els.gugun, [], "구군 전체");
  els.gugun.disabled = !els.sido.value;
  if (els.sido.value) fillSelect(els.gugun, await getGugunList(els.sido.value), "구군 전체");
  search();
});
els.gugun.addEventListener("change", () => search());
els.chips.addEventListener("change", () => search());

els.more.addEventListener("click", () => {
  state.page += 1;
  search({ append: true });
});

els.list.addEventListener("click", (e) => {
  const btn = e.target.closest(".spot-item");
  if (!btn) return;
  const idx = Number(btn.dataset.idx);
  const spot = state.items[idx];
  highlight(idx);
  mapView?.focus(spot);
  openDetail(spot);
});

/* ---------- 초기화 ---------- */
const init = async () => {
  // 메인 화면 빠른 검색에서 넘어온 조건 반영
  renderChips(getParam("type") ?? "");
  els.keyword.value = getParam("keyword") ?? "";

  const mapReady = loadKakaoMap()
    .then(() => {
      mapView = createMap(els.mapEl);
      evLayer = mapView.createLayer();
    })
    .catch((e) => showMapNotice(els.mapEl, e.message));

  fillSelect(els.sido, await getSidoList(), "시도 전체");
  const sido = getParam("sido");
  if (sido) {
    els.sido.value = sido;
    els.gugun.disabled = false;
    fillSelect(els.gugun, await getGugunList(sido), "구군 전체");
    els.gugun.value = getParam("gugun") ?? "";
  }

  await mapReady;
  search();
};

init();
