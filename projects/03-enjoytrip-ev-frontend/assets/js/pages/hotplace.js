// F105 회원 주도 핫플레이스 등록 (지도 + 사진)
import { $, escapeHtml, formatDate, resizeImage, setFieldError, toast, uid } from "../core/util.js";
import { createMap, distanceKm, loadKakaoMap, showMapNotice } from "../core/kakao.js";
import { currentUser } from "../core/auth.js";
import { hotplaces } from "../core/storage.js";

const CATEGORIES = [
  { id: "ev", label: "⚡ 충전하며 들르기" },
  { id: "cafe", label: "카페" },
  { id: "food", label: "맛집" },
  { id: "nature", label: "자연" },
  { id: "photo", label: "포토스팟" },
  { id: "etc", label: "기타" },
];
const categoryLabel = (id) => CATEGORIES.find((c) => c.id === id)?.label ?? "기타";

let mapView = null;
let filter = "";
let picked = null; // {x, y} 지도에서 고른 위치
let photoData = "";
let pickMarker = null;
const formPanel = bootstrap.Offcanvas.getOrCreateInstance("#hp-form-panel");

/* ---------- 목록 ---------- */
const visibleItems = () =>
  hotplaces
    .all()
    .filter((h) => !filter || h.category === filter)
    .sort((a, b) => b.createdAt.localeCompare(a.createdAt));

const cardHtml = (h, user) => `
  <article class="hp-card" data-id="${h.id}">
    ${h.photo ? `<img class="hp-photo" src="${escapeHtml(h.photo)}" alt="${escapeHtml(h.title)} 사진">` : `<div class="hp-photo" aria-hidden="true">사진 없음</div>`}
    <div class="p-3">
      <span class="type-tag ${h.category === "ev" ? "is-ev" : ""}">${escapeHtml(categoryLabel(h.category))}</span>
      <h2 class="h6 fw-bold mb-1">${escapeHtml(h.title)}${h.verified ? `<span class="verify-badge" title="사진 촬영 위치가 등록 위치 ${VERIFY_KM}km 이내">📍 방문 인증</span>` : ""}</h2>
      <p class="small text-body-secondary mb-1">${escapeHtml(formatDate(h.visitDate))} 방문 · ${escapeHtml(h.userName)}</p>
      ${h.address ? `<p class="small text-body-secondary mb-1">${escapeHtml(h.address)}</p>` : ""}
      ${h.description ? `<p class="small mb-2" style="white-space:pre-line">${escapeHtml(h.description)}</p>` : ""}
      <div class="d-flex gap-2">
        ${h.x ? `<button class="btn btn-sm btn-outline-primary" data-focus="${h.id}" type="button">지도에서 보기</button>` : ""}
        ${user?.id === h.userId ? `<button class="btn btn-sm btn-outline-danger" data-del="${h.id}" type="button">삭제</button>` : ""}
      </div>
    </div>
  </article>`;

const render = () => {
  const user = currentUser();
  const items = visibleItems();
  $("#hp-meta").textContent = `핫플레이스 ${items.length}곳`;
  $("#hp-list").innerHTML = items.length
    ? items.map((h) => cardHtml(h, user)).join("")
    : `<div class="empty-state">아직 등록된 핫플레이스가 없습니다.<br>다녀온 숨은 명소를 처음으로 소개해 주세요.</div>`;
  mapView?.render(items.filter((h) => h.x), { onClick: (info) => highlight(info.id) });
};

const highlight = (id) => {
  document.querySelectorAll(".hp-card").forEach((c) => c.classList.toggle("is-active", c.dataset.id === id));
  document.querySelector(`.hp-card[data-id="${id}"]`)?.scrollIntoView({ block: "nearest", behavior: "smooth" });
};

$("#hp-list").addEventListener("click", (e) => {
  const focusId = e.target.dataset.focus;
  const delId = e.target.dataset.del;
  if (focusId) {
    mapView?.focus(hotplaces.get(focusId));
    highlight(focusId);
  }
  if (delId && confirm("이 핫플레이스를 삭제할까요?")) {
    if (hotplaces.get(delId)?.userId !== currentUser()?.id) return;
    hotplaces.remove(delId);
    render();
  }
});

$("#category-chips").innerHTML = [{ id: "", label: "전체" }, ...CATEGORIES]
  .map(
    (c) => `<input type="radio" class="btn-check" name="cat" id="cat-${c.id || "all"}" value="${c.id}" ${c.id === "" ? "checked" : ""} autocomplete="off">
      <label class="btn btn-outline-secondary" for="cat-${c.id || "all"}">${c.label}</label>`
  )
  .join("");
$("#category-chips").addEventListener("change", (e) => {
  filter = e.target.value;
  render();
});

/* ---------- 등록 폼 ---------- */
$("#hp-category").innerHTML = `<option value="">선택하세요</option>` + CATEGORIES.map((c) => `<option value="${c.id}">${c.label}</option>`).join("");
$("#hp-date").max = new Date().toISOString().slice(0, 10);

$("#btn-open-form").addEventListener("click", () => {
  if (!currentUser()) {
    if (confirm("핫플레이스를 등록하려면 로그인이 필요합니다. 로그인 페이지로 이동할까요?")) location.href = "login.html?redirect=hotplace.html";
    return;
  }
  formPanel.show();
});

const setPicked = (coords, address) => {
  picked = coords;
  if (address) $("#hp-address").value = address;
  $("#pick-hint").textContent = coords ? `선택한 위치: ${coords.y.toFixed(5)}, ${coords.x.toFixed(5)}` : "지도를 클릭하면 그 위치가 선택됩니다.";
  if (!mapView) return;
  pickMarker?.setMap(null);
  pickMarker = coords ? new kakao.maps.Marker({ map: mapView.map, position: new kakao.maps.LatLng(coords.y, coords.x) }) : null;
};

$("#btn-geocode").addEventListener("click", async () => {
  const addr = $("#hp-address").value.trim();
  if (!addr) return setFieldError($("#hp-address"), "주소를 입력하세요.");
  if (!mapView) return setFieldError($("#hp-address"), "지도 키가 없어 좌표를 찾을 수 없습니다. 주소만 저장됩니다.");
  const coords = await mapView.geocode(addr);
  if (!coords) return setFieldError($("#hp-address"), "주소를 찾지 못했습니다. 도로명 주소로 다시 입력하거나 지도를 클릭하세요.");
  setFieldError($("#hp-address"), "");
  setPicked(coords);
  mapView.map.setCenter(new kakao.maps.LatLng(coords.y, coords.x));
});

/** 사진 EXIF 에서 GPS 좌표와 촬영일을 읽는다 (exifr 라이브러리, 실패하면 null) */
const EXIFR_URL = "https://cdn.jsdelivr.net/npm/exifr@7.1.3/dist/lite.esm.mjs";
const readExif = async (file) => {
  try {
    const exifr = (await import(EXIFR_URL)).default;
    // lite 빌드에서는 parse(file, [태그]) 형태가 동작하지 않아 전체 파싱 결과(위도·경도 포함)를 쓴다
    const meta = (await exifr.parse(file)) ?? {};
    const taken = meta.DateTimeOriginal instanceof Date ? meta.DateTimeOriginal : null;
    return {
      coords: Number.isFinite(meta.latitude) && Number.isFinite(meta.longitude) ? { x: meta.longitude, y: meta.latitude } : null,
      date: taken ? `${taken.getFullYear()}-${String(taken.getMonth() + 1).padStart(2, "0")}-${String(taken.getDate()).padStart(2, "0")}` : "",
    };
  } catch (e) {
    console.warn("EXIF 를 읽지 못했습니다:", e);
    return { coords: null, date: "" };
  }
};

const VERIFY_KM = 1; // 사진 촬영 위치와 등록 위치가 이 거리 안이면 '방문 인증'
let photoGps = null;

const reverseGeocode = (coords) =>
  new Promise((resolve) => {
    if (!mapView) return resolve("");
    mapView.geocoder.coord2Address(coords.x, coords.y, (result, status) =>
      resolve(status === kakao.maps.services.Status.OK ? result[0].road_address?.address_name || result[0].address?.address_name : "")
    );
  });

$("#hp-photo").addEventListener("change", async (e) => {
  const file = e.target.files[0];
  photoData = "";
  photoGps = null;
  $("#hp-preview").classList.add("d-none");
  $("#exif-hint").textContent = "";
  if (!file) return;
  try {
    // 원본에서 EXIF 를 먼저 읽고, 저장은 캔버스로 줄인 사본(EXIF 제거됨)만 한다
    const exif = await readExif(file);
    photoData = await resizeImage(file, 800, 0.75);
    $("#hp-preview").src = photoData;
    $("#hp-preview").classList.remove("d-none");
    setFieldError(e.target, "");

    const notes = [];
    if (exif.date && !$("#hp-date").value && exif.date <= $("#hp-date").max) {
      $("#hp-date").value = exif.date;
      notes.push(`촬영일 ${exif.date}`);
    }
    if (exif.coords) {
      photoGps = exif.coords;
      if (!picked) {
        setPicked(exif.coords, await reverseGeocode(exif.coords));
        mapView?.map.setCenter(new kakao.maps.LatLng(exif.coords.y, exif.coords.x));
        notes.push("촬영 위치");
      }
      $("#exif-hint").textContent = `📍 사진에서 ${notes.join("·") || "위치 정보"}를 읽었습니다. 등록 위치가 촬영 위치에서 ${VERIFY_KM}km 이내면 '방문 인증'이 표시됩니다.`;
    } else {
      $("#exif-hint").textContent = notes.length
        ? `사진에서 ${notes.join("·")}를 읽었습니다. 위치 정보가 없어 방문 인증은 할 수 없습니다.`
        : "사진에 위치 정보가 없어 방문 인증은 할 수 없습니다.";
    }
  } catch (err) {
    e.target.value = "";
    setFieldError(e.target, err.message);
  }
});

$("#hp-form").addEventListener("submit", (e) => {
  e.preventDefault();
  const user = currentUser();
  if (!user) return (location.href = "login.html?redirect=hotplace.html");
  const title = $("#hp-title").value.trim();
  const date = $("#hp-date").value;
  const address = $("#hp-address").value.trim();
  const ok = [
    setFieldError($("#hp-title"), title ? "" : "장소 이름을 입력하세요."),
    setFieldError($("#hp-category"), $("#hp-category").value ? "" : "장소 유형을 고르세요."),
    setFieldError($("#hp-date"), !date ? "다녀온 날짜를 입력하세요." : date > $("#hp-date").max ? "미래 날짜는 고를 수 없습니다." : ""),
    setFieldError($("#hp-address"), address || picked ? "" : "주소를 입력하거나 지도에서 위치를 고르세요."),
  ];
  if (ok.includes(false)) return $("#hp-form .is-invalid")?.focus();

  try {
    hotplaces.insert({
      id: uid(),
      userId: user.id,
      userName: user.name,
      title,
      category: $("#hp-category").value,
      visitDate: date,
      address,
      x: picked?.x ?? null,
      y: picked?.y ?? null,
      photo: photoData,
      // 사진 촬영 위치와 등록 위치가 가까우면 방문 인증
      verified: Boolean(photoGps && picked && distanceKm(photoGps, picked) <= VERIFY_KM),
      description: $("#hp-desc").value.trim(),
      createdAt: new Date().toISOString(),
    });
  } catch (err) {
    toast(err.message, "danger");
    return;
  }
  e.target.reset();
  e.target.querySelectorAll(".is-valid, .is-invalid").forEach((el) => el.classList.remove("is-valid", "is-invalid"));
  $("#hp-preview").classList.add("d-none");
  photoData = "";
  photoGps = null;
  $("#exif-hint").textContent = "";
  setPicked(null);
  formPanel.hide();
  render();
  toast("핫플레이스를 등록했습니다.", "success");
});

/* ---------- 초기화 ---------- */
const init = async () => {
  try {
    await loadKakaoMap();
    mapView = createMap($("#map"));
    mapView.onMapClick((coords) => {
      if (!$("#hp-form-panel").classList.contains("show")) return;
      mapView.geocoder.coord2Address(coords.x, coords.y, (result, status) => {
        const addr = status === kakao.maps.services.Status.OK ? result[0].road_address?.address_name || result[0].address?.address_name : "";
        setPicked(coords, addr);
        setFieldError($("#hp-address"), "");
      });
    });
  } catch (e) {
    showMapNotice($("#map"), e.message);
    $("#pick-hint").textContent = "지도 키가 없어 주소만 저장됩니다.";
  }
  render();
};

init();
