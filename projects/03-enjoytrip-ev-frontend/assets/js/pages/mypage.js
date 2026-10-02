// F107 회원 정보 조회 · 수정 · 탈퇴
import { $, escapeHtml, formatDate, formatWon, setFieldError, toast } from "../core/util.js";
import { plans, hotplaces } from "../core/storage.js";
import { contentTypeLabel } from "../core/tour-api.js";
import { distanceKm } from "../core/kakao.js";
import { EXPENSE_CATEGORIES } from "../core/plan-store.js";
import { requireLogin, updateProfile, validate, verifyPassword, withdraw } from "../core/auth.js";

let user = requireLogin();

const viewSection = $("#view-section");
const editForm = $("#edit-form");

const renderView = () => {
  $("#v-id").textContent = user.id + (user.role === "admin" ? " (관리자)" : "");
  $("#v-name").textContent = user.name;
  $("#v-email").textContent = user.email;
  $("#v-created").textContent = formatDate(user.createdAt);
};

const toggleEdit = (editing) => {
  viewSection.classList.toggle("d-none", editing);
  $("#stats-section").classList.toggle("d-none", editing);
  editForm.classList.toggle("d-none", !editing);
  if (editing) {
    $("#name").value = user.name;
    $("#email").value = user.email;
    $("#new-password").value = "";
    $("#current-password").value = "";
    editForm.querySelectorAll(".is-invalid, .is-valid").forEach((el) => el.classList.remove("is-invalid", "is-valid"));
    $("#name").focus();
  }
};

$("#btn-edit").addEventListener("click", () => toggleEdit(true));
$("#btn-cancel").addEventListener("click", () => toggleEdit(false));

editForm.addEventListener("submit", async (e) => {
  e.preventDefault();
  const name = $("#name").value.trim();
  const email = $("#email").value.trim();
  const newPw = $("#new-password").value;
  const ok = [
    setFieldError($("#name"), validate("name", name)),
    setFieldError($("#email"), validate("email", email)),
    setFieldError($("#new-password"), newPw ? validate("password", newPw) : ""),
  ];
  if (ok.includes(false)) return;
  if (!(await verifyPassword(user.id, $("#current-password").value))) {
    setFieldError($("#current-password"), "현재 비밀번호가 올바르지 않습니다.");
    return;
  }
  user = await updateProfile(user.id, { name, email, password: newPw || null });
  renderView();
  toggleEdit(false);
  toast("회원 정보가 수정되었습니다.", "success");
});

$("#withdraw-form").addEventListener("submit", async (e) => {
  e.preventDefault();
  const input = $("#withdraw-password");
  if (!(await verifyPassword(user.id, input.value))) {
    setFieldError(input, "비밀번호가 올바르지 않습니다.");
    return;
  }
  withdraw(user.id);
  alert("탈퇴 처리되었습니다. 이용해 주셔서 감사합니다.");
  location.replace("index.html");
});

/* =========================================================
   나의 여행 통계 (Chart.js) — 저장한 계획·핫플레이스 기준
   ========================================================= */
const PALETTE = ["#047857", "#f59e0b", "#2563eb", "#db2777", "#7c3aed", "#0891b2", "#65a30d", "#6b7280"];

const legDistance = (stops) =>
  stops.slice(1).reduce((sum, s, i) => (s.x && stops[i].x ? sum + distanceKm(stops[i], s) : sum), 0);

const renderStats = () => {
  const myPlans = plans.filter((p) => p.userId === user.id);
  const myHot = hotplaces.filter((h) => h.userId === user.id);
  const stops = myPlans.flatMap((p) => p.stops);

  const tiles = [
    { num: myPlans.length, lbl: "저장한 계획" },
    { num: stops.length, lbl: "담은 장소" },
    { num: `${myHot.length}`, lbl: `핫플 (인증 ${myHot.filter((h) => h.verified).length})` },
  ];
  $("#stat-tiles").innerHTML = tiles
    .map((t) => `<div class="stat-tile"><div class="num">${escapeHtml(t.num)}</div><div class="lbl">${escapeHtml(t.lbl)}</div></div>`)
    .join("");

  if (myPlans.length === 0) {
    $("#stats-body").innerHTML = `<p class="empty-state">아직 저장한 여행 계획이 없습니다.<br><a class="btn btn-sm btn-primary mt-2" href="plan.html">여행 계획 만들기</a></p>`;
    return;
  }

  // 1) 장소 유형 비율
  const typeCount = {};
  stops.forEach((s) => (typeCount[contentTypeLabel(s.contentTypeId)] = (typeCount[contentTypeLabel(s.contentTypeId)] ?? 0) + 1));
  new Chart($("#chart-types"), {
    type: "doughnut",
    data: { labels: Object.keys(typeCount), datasets: [{ data: Object.values(typeCount), backgroundColor: PALETTE, borderWidth: 2 }] },
    options: { maintainAspectRatio: false, plugins: { legend: { position: "right" } } },
  });

  // 2) 항목별 경비 (충전비는 직선거리 ÷ 전비 × 단가로 추정)
  const expense = { "충전비": 0 };
  EXPENSE_CATEGORIES.forEach((c) => (expense[c.label] = 0));
  myPlans.forEach((p) => {
    expense["충전비"] += Math.round((legDistance(p.stops) / (p.efficiency || 5)) * (p.pricePerKwh ?? 347));
    p.stops.forEach((s) => {
      const label = EXPENSE_CATEGORIES.find((c) => c.id === (s.category ?? "etc"))?.label ?? "기타";
      expense[label] += Number(s.cost) || 0;
    });
  });
  new Chart($("#chart-expense"), {
    type: "bar",
    data: {
      labels: Object.keys(expense),
      datasets: [{ label: "예상 경비(원)", data: Object.values(expense), backgroundColor: ["#f59e0b", "#047857", "#2563eb", "#7c3aed", "#6b7280"], borderRadius: 6 }], // 충전비=accent
    },
    options: {
      maintainAspectRatio: false,
      plugins: { legend: { display: false }, tooltip: { callbacks: { label: (ctx) => formatWon(ctx.raw) } } },
      scales: { y: { beginAtZero: true, ticks: { callback: (v) => (v >= 10000 ? `${(v / 10000).toLocaleString()}만` : v.toLocaleString()) } } },
    },
  });

  // 3) 월별 여행 히트맵 (올해, 출발일 기준)
  const year = new Date().getFullYear();
  const months = Array(12).fill(0);
  myPlans.filter((p) => p.startDate?.startsWith(String(year))).forEach((p) => months[Number(p.startDate.slice(5, 7)) - 1]++);
  $("#heatmap-year").textContent = `(${year}년)`;
  $("#heatmap").innerHTML = months
    .map((n, i) => `<div class="cell" data-level="${Math.min(n, 3)}" title="${i + 1}월 ${n}회" aria-label="${i + 1}월 여행 ${n}회">${i + 1}</div>`)
    .join("");
};

if (user) {
  renderView();
  renderStats();
}
