// 실행 화면 캡처 (README 산출물용) — 의존성 없이 크롬 DevTools Protocol 로 직접 제어
// 사용: (로컬 서버 실행 중) node scripts/screenshots.mjs [baseUrl]
//   baseUrl 기본값 http://localhost:5500 · 결과: docs/screenshots/*.png
// 임시 크롬 프로필에 데모 계정·계획·핫플·게시글을 만들어 찍으므로 실제 브라우저 데이터에는 영향이 없다.
import { spawn } from "node:child_process";
import { existsSync, mkdirSync, mkdtempSync, rmSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join, resolve, dirname } from "node:path";
import { fileURLToPath } from "node:url";

const BASE = process.argv[2] ?? "http://localhost:5500";
const ROOT = resolve(dirname(fileURLToPath(import.meta.url)), "..");
const OUT = join(ROOT, "docs/screenshots");
const PORT = 9333;
const CHROME = [
  process.env.CHROME_PATH,
  "C:/Program Files/Google/Chrome/Application/chrome.exe",
  "C:/Program Files (x86)/Google/Chrome/Application/chrome.exe",
  "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome",
  "/usr/bin/google-chrome",
].find((p) => p && existsSync(p));
if (!CHROME) throw new Error("크롬을 찾지 못했습니다. CHROME_PATH 환경 변수로 경로를 지정하세요.");

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
mkdirSync(OUT, { recursive: true });
const profile = mkdtempSync(join(tmpdir(), "enjoytrip-shot-"));
const chrome = spawn(CHROME, [
  "--headless=new", `--remote-debugging-port=${PORT}`, `--user-data-dir=${profile}`,
  "--window-size=1440,900", "--hide-scrollbars", "--no-first-run", "--lang=ko-KR", "about:blank",
]);

/* ---------- 최소 CDP 클라이언트 ---------- */
let ws, seq = 0;
const pending = new Map();
const send = (method, params = {}) =>
  new Promise((res, rej) => {
    const id = ++seq;
    pending.set(id, { res, rej });
    ws.send(JSON.stringify({ id, method, params }));
  });
const evaluate = async (expression) => {
  const r = await send("Runtime.evaluate", { expression, awaitPromise: true, returnByValue: true });
  if (r.exceptionDetails) throw new Error(r.exceptionDetails.exception?.description ?? "evaluate 실패");
  return r.result.value;
};
const go = async (path, wait = 3500) => {
  await send("Page.navigate", { url: BASE + path });
  await sleep(wait);
};
const shot = async (name, { width = 1440, height = 900, mobile = false } = {}) => {
  await send("Emulation.setDeviceMetricsOverride", { width, height, deviceScaleFactor: 1, mobile });
  await sleep(600);
  const { data } = await send("Page.captureScreenshot", { format: "png" });
  writeFileSync(join(OUT, `${name}.png`), Buffer.from(data, "base64"));
  console.log("  ✓", name);
};

try {
  let target;
  for (let i = 0; i < 40 && !target; i++) {
    await sleep(250);
    target = await fetch(`http://127.0.0.1:${PORT}/json/list`).then((r) => r.json()).then((l) => l.find((t) => t.type === "page")).catch(() => null);
  }
  if (!target) throw new Error("크롬 DevTools 에 연결하지 못했습니다.");
  ws = new WebSocket(target.webSocketDebuggerUrl);
  ws.onmessage = ({ data }) => {
    const msg = JSON.parse(data);
    const p = pending.get(msg.id);
    if (!p) return;
    pending.delete(msg.id);
    msg.error ? p.rej(new Error(msg.error.message)) : p.res(msg.result);
  };
  await new Promise((r) => (ws.onopen = r));
  await send("Page.enable");
  await send("Emulation.setDeviceMetricsOverride", { width: 1440, height: 900, deviceScaleFactor: 1, mobile: false });

  console.log("데모 데이터 준비…");
  await go("/index.html", 1500);
  await evaluate(`(async () => {
    const auth = await import('/assets/js/core/auth.js');
    const pw = crypto.randomUUID().slice(0, 8) + 'a1';               // 임시 프로필 전용
    await auth.signup({ id: 'demo', password: pw, name: '김진영', email: 'demo@example.com' });
    await auth.login('demo', pw);
    const { searchSpots } = await import('/assets/js/core/tour-api.js');
    const { addSpotToDraft, loadDraft, saveDraft } = await import('/assets/js/core/plan-store.js');
    for (const kw of ['국립아시아문화전당', '양림동', '죽녹원', '1913송정역시장']) {
      const { items } = await searchSpots({ keyword: kw, size: 1 });
      if (items[0]) addSpotToDraft(items[0]);
    }
    const plan = loadDraft();
    plan.title = '광주 → 담양 EV 당일 드라이브';
    const times = ['09:30', '11:30', '14:00', '19:00'], costs = [0, 0, 3000, 25000];
    plan.stops.forEach((s, i) => { s.time = times[i] ?? ''; s.cost = costs[i] ?? 0; if (i === 3) s.category = 'food'; });
    saveDraft(plan);
    const { plans, hotplaces } = await import('/assets/js/core/storage.js');
    plans.insert({ ...plan, id: 'demo-plan', userId: 'demo', createdAt: new Date().toISOString(), updatedAt: new Date().toISOString() });
    hotplaces.insert({ id: 'demo-hp', userId: 'demo', userName: '김진영', title: '죽녹원 옆 충전 카페 (예시)', category: 'ev',
      visitDate: '2026-09-27', address: '전남광주통합특별시 담양군 담양읍 죽녹원로 119', x: 126.9862, y: 35.3255, photo: '',
      verified: true, description: '급속 충전 40분 동안 대나무숲 산책하고 커피 한 잔.', createdAt: new Date().toISOString() });
    const board = await import('/assets/js/core/board.js');
    board.createPost({ type: 'free', title: '광주→담양 드라이브, 충전 한 번이면 충분했어요', content: '죽녹원 공영주차장 급속 충전기를 이용했습니다.', user: auth.currentUser() });
  })()`);

  console.log("캡처…");
  await go("/index.html"); await shot("01-main");
  await go("/attraction.html?sido=12&gugun=210&type=12", 5000); await shot("02-attraction");
  await evaluate(`document.querySelector('.spot-item')?.click()`); await sleep(4000); await shot("03-attraction-detail");
  await go("/plan.html", 6000); await shot("04-plan");
  await go("/hotplace.html", 4500); await shot("05-hotplace");
  await go("/board.html?type=free", 2000); await shot("06-board");
  await go("/mypage.html", 3000); await evaluate(`document.querySelector('#stats-section').scrollIntoView()`); await shot("07-mypage-stats");
  await go("/developers.html", 2000); await shot("08-developers");
  await evaluate(`localStorage.removeItem('enjoytrip.session')`);
  await go("/signup.html", 2000); await shot("09-signup");
  await go("/attraction.html?sido=12&type=12", 5000); await shot("10-mobile-attraction", { width: 390, height: 844, mobile: true });
  console.log(`완료 → ${OUT}`);
} finally {
  ws?.close();
  chrome.kill();
  await sleep(800);
  rmSync(profile, { recursive: true, force: true, maxRetries: 5, retryDelay: 300 });
}
