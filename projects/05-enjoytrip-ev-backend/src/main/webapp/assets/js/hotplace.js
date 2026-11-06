// F105 핫플레이스 등록/수정 폼: 지도 클릭으로 위치 지정, 사진 미리보기·형식/크기 확인, 사진 EXIF 의 GPS·촬영일 읽기
(() => {
  const form = document.getElementById("hp-form");
  if (!form) return;

  const MAX_BYTES = 5 * 1024 * 1024;
  const TYPES = ["image/jpeg", "image/png", "image/webp", "image/gif"];
  // 서버(HotplaceService)와 같은 국내 범위
  const inKorea = (lat, lng) => lat >= 33 && lat <= 38.7 && lng >= 124.5 && lng <= 132;

  const latEl = document.getElementById("hp-lat");
  const lngEl = document.getElementById("hp-lng");
  const hint = document.getElementById("pick-hint");
  const photoEl = document.getElementById("hp-photo");
  const photoError = document.getElementById("photo-error");
  const preview = document.getElementById("hp-preview");
  const exifHint = document.getElementById("exif-hint");
  const dateEl = document.getElementById("hp-date");

  const hasPoint = () => latEl.value !== "" && lngEl.value !== "";
  const map = ET.createMap("pick-map", hasPoint() ? { lat: +latEl.value, lng: +lngEl.value, zoom: 15 } : {});
  let marker = null;

  const setPoint = (lat, lng) => {
    if (!inKorea(lat, lng)) {
      hint.textContent = "국내 위치만 선택할 수 있습니다.";
      hint.classList.add("text-danger");
      return false;
    }
    latEl.value = lat.toFixed(7);
    lngEl.value = lng.toFixed(7);
    hint.classList.remove("text-danger");
    hint.textContent = `선택한 위치: ${lat.toFixed(5)}, ${lng.toFixed(5)}`;
    if (marker) marker.setLatLng([lat, lng]);
    else marker = L.marker([lat, lng]).addTo(map);
    return true;
  };

  if (hasPoint()) setPoint(+latEl.value, +lngEl.value);
  map.on("click", (e) => setPoint(e.latlng.lat, e.latlng.lng));

  /** 사진 EXIF 에서 GPS 좌표와 촬영일을 읽는다 (3회차와 같은 exifr lite, 실패하면 빈 값) */
  const readExif = async (file) => {
    try {
      const exifr = (await import("https://cdn.jsdelivr.net/npm/exifr@7.1.3/dist/lite.esm.mjs")).default;
      const meta = (await exifr.parse(file)) ?? {};
      const taken = meta.DateTimeOriginal instanceof Date ? meta.DateTimeOriginal : null;
      const pad = (n) => String(n).padStart(2, "0");
      return {
        lat: Number.isFinite(meta.latitude) ? meta.latitude : null,
        lng: Number.isFinite(meta.longitude) ? meta.longitude : null,
        date: taken ? `${taken.getFullYear()}-${pad(taken.getMonth() + 1)}-${pad(taken.getDate())}` : "",
      };
    } catch (e) {
      console.warn("EXIF 를 읽지 못했습니다:", e);
      return { lat: null, lng: null, date: "" };
    }
  };

  const setPhotoError = (msg) => {
    photoError.textContent = msg;
    photoEl.classList.toggle("is-invalid", Boolean(msg));
  };

  photoEl.addEventListener("change", async () => {
    const file = photoEl.files[0];
    setPhotoError("");
    exifHint.textContent = "";
    preview.classList.add("d-none");
    if (!file) return;
    // 서버가 다시 검사하지만, 큰 파일을 보내기 전에 미리 알려 준다
    if (!TYPES.includes(file.type)) {
      photoEl.value = "";
      return setPhotoError("JPG, PNG, WEBP, GIF 이미지만 올릴 수 있습니다.");
    }
    if (file.size > MAX_BYTES) {
      photoEl.value = "";
      return setPhotoError("사진은 5MB 이하만 올릴 수 있습니다.");
    }
    preview.src = URL.createObjectURL(file);
    preview.classList.remove("d-none");

    const exif = await readExif(file);
    const notes = [];
    if (exif.date && !dateEl.value && exif.date <= dateEl.max) {
      dateEl.value = exif.date;
      notes.push(`촬영일 ${exif.date}`);
    }
    if (exif.lat !== null && exif.lng !== null && !hasPoint() && setPoint(exif.lat, exif.lng)) {
      map.setView([exif.lat, exif.lng], 15);
      notes.push("촬영 위치");
    }
    if (notes.length) exifHint.textContent = `사진에서 ${notes.join("·")}를 읽어 채웠습니다.`;
  });

  form.addEventListener("submit", (e) => {
    if (!hasPoint()) {
      e.preventDefault();
      hint.textContent = "지도를 클릭해 위치를 선택하세요.";
      hint.classList.add("text-danger");
      document.getElementById("pick-map").scrollIntoView({ block: "center", behavior: "smooth" });
      return;
    }
    if (!form.checkValidity()) {
      e.preventDefault();
      form.reportValidity();
    }
  });
})();
