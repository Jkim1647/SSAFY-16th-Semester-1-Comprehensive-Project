// 시연 영상 자동 제작: 장면별 TTS 내레이션 + 크롬 화면 녹화 → mp4
// 사용: (로컬 서버 실행 중) node scripts/demo-video.mjs [baseUrl]
// 필요: Google Chrome, ffmpeg/ffprobe (PATH), python + edge-tts (`pip install edge-tts`)
// 결과: docs/demo/enjoytrip-ev-demo.mp4, docs/demo/enjoytrip-ev-demo.srt
// 임시 크롬 프로필에서 데모 계정·데이터를 만들어 녹화하므로 실제 브라우저 데이터에는 영향이 없다.
import { execFileSync, spawn } from "node:child_process";
import { existsSync, mkdirSync, mkdtempSync, readFileSync, rmSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { dirname, join, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const BASE = process.argv[2] ?? "http://localhost:5500";
const ROOT = resolve(dirname(fileURLToPath(import.meta.url)), "..");
const OUT_DIR = join(ROOT, "docs/demo");
const WORK = mkdtempSync(join(tmpdir(), "enjoytrip-demo-"));
const VOICE = "ko-KR-SunHiNeural";
const W = 1440, H = 900, PORT = 9334;
const CHROME = [
  process.env.CHROME_PATH,
  "C:/Program Files/Google/Chrome/Application/chrome.exe",
  "C:/Program Files (x86)/Google/Chrome/Application/chrome.exe",
  "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome",
  "/usr/bin/google-chrome",
].find((p) => p && existsSync(p));
if (!CHROME) throw new Error("크롬을 찾지 못했습니다. CHROME_PATH 를 지정하세요.");

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const DEMO_PW = `Demo${Math.random().toString(36).slice(2, 8)}9`; // 임시 프로필 전용 데모 비밀번호
const PHOTO_B64 = readFileSync(join(ROOT, "scripts/demo/sample-gps.jpg")).toString("base64");

/* =========================================================
   장면 정의: say(내레이션) · prepare(녹화 전, 화면에 안 나옴) · act(녹화 중 동작)
   act/prepare 는 페이지 안에서 실행되는 JS 문자열 — __demo 헬퍼 사용
   ========================================================= */
const SCENES = [
  {
    id: "intro",
    say: "안녕하세요. SSAFY 16기 광주 3반 김진영, 강상민 팀의 EnjoyTrip EV 시연을 시작하겠습니다. EnjoyTrip EV는 전기차 운전자를 위한 드라이브 여행 플래너입니다. 가고 싶은 관광지를 고르면, 동선에 맞춰 충전소까지 함께 챙겨 줍니다.",
    url: "/index.html",
    act: `await __demo.move('.hero h1'); await __demo.sleep(1500); await __demo.move('.quick-search'); await __demo.sleep(1500);
          await __demo.move('.feature-list li:nth-child(1)'); await __demo.sleep(900); await __demo.move('.feature-list li:nth-child(3)');`,
  },
  {
    id: "signup",
    say: "먼저 회원가입입니다. 아이디 중복 확인과 비밀번호 규칙, 이메일 형식 같은 입력값을 바로바로 검사해서 알려 줍니다.",
    url: "/signup.html",
    act: `await __demo.type('#id', 'demo01'); await __demo.click('#btn-check-id'); await __demo.sleep(500);
          await __demo.type('#password', 'abc'); document.querySelector('#password').dispatchEvent(new Event('blur')); await __demo.sleep(1200);
          await __demo.type('#password', ${JSON.stringify(DEMO_PW)}, 30); document.querySelector('#password').dispatchEvent(new Event('blur'));
          await __demo.type('#password2', ${JSON.stringify(DEMO_PW)}, 20);
          await __demo.type('#name', '김진영'); await __demo.type('#email', 'demo01@example.com', 30);
          await __demo.click('#signup-form button[type=submit]');`,
  },
  {
    id: "login",
    say: "가입한 계정으로 로그인합니다. 로그인하면 여행 계획과 핫플레이스를 저장할 수 있고, 상단 메뉴에 내 이름이 표시됩니다.",
    url: "/login.html",
    act: `await __demo.type('#id', 'demo01'); await __demo.type('#password', ${JSON.stringify(DEMO_PW)}, 30);
          await __demo.click('#login-form button[type=submit]');`,
    act2: `await __demo.move('#app-header .dropdown-toggle'); await __demo.sleep(800); await __demo.move('.hero h1');`,
  },
  {
    id: "attraction",
    say: "다음은 관광지 조회입니다. 한국관광공사 공공데이터로 지역과 유형별 관광지를 찾을 수 있습니다. 시도와 구군을 고르고 관광지, 음식점처럼 유형을 바꾸면, 목록과 카카오 지도의 마커가 함께 바뀝니다.",
    url: "/attraction.html",
    act: `await __demo.sleep(800); await __demo.select('#sido', '12'); await __demo.sleep(2200); await __demo.select('#gugun', '210'); await __demo.sleep(2200);
          await __demo.click('label[for=type-12]'); await __demo.sleep(2200); await __demo.click('label[for=type-39]'); await __demo.sleep(2200);
          await __demo.click('label[for=type-12]'); await __demo.sleep(1500); await __demo.move('#map');`,
  },
  {
    id: "detail",
    say: "관광지를 누르면 상세 정보가 열립니다. 소개와 함께 7일 날씨 예보, 오늘의 일출과 일몰 시각, 관광공사 추천 사진, 그리고 반경 1킬로미터 안의 전기차 충전소를 실시간으로 보여 줍니다. 마음에 드는 곳은 여행 계획에 담아 보겠습니다.",
    act: `await __demo.click('.spot-item'); await __demo.sleep(2500);
          for (const sel of ['#detail-overview', '#detail-weather', '#detail-photos', '#detail-ev']) { await __demo.move(sel); await __demo.sleep(2200); }
          await __demo.click('#btn-add-plan'); await __demo.sleep(1200);`,
  },
  {
    id: "plan",
    say: "그렇다면 여행 계획 페이지로 이동해 보겠습니다. 담아 둔 장소는 카카오모빌리티 길찾기로 실제 도로 경로와 소요 시간을 계산합니다. 정류지마다 1킬로미터 안의 충전소 수와 사용 가능한 충전기 수를 보여 주고, 지도에서도 충전소 위치를 확인할 수 있습니다.",
    // 녹화 전에 장소 3곳을 더 담아 둔다
    prepare: `const { searchSpots } = await import('/assets/js/core/tour-api.js');
              const { addSpotToDraft, loadDraft, saveDraft } = await import('/assets/js/core/plan-store.js');
              for (const kw of ['양림동', '죽녹원', '1913송정역시장']) { const { items } = await searchSpots({ keyword: kw, size: 1 }); if (items[0]) addSpotToDraft(items[0]); }
              const p = loadDraft(); p.title = '광주 → 담양 EV 당일 드라이브'; p.stops.forEach((s, i) => (s.time = ['10:00', '11:30', '14:00', '17:00'][i] ?? '')); saveDraft(p);`,
    url: "/plan.html",
    act: `await __demo.sleep(3500); await __demo.move('.leg'); await __demo.sleep(1500); await __demo.move('.ev-line'); await __demo.sleep(1800);
          await __demo.move('#map'); await __demo.sleep(1500); await __demo.move('.map-legend');`,
  },
  {
    id: "reorder",
    say: "방문 순서는 손잡이를 끌어서 바꿀 수 있고, 키보드로도 바꿀 수 있습니다. 순서가 바뀌면 경로와 거리, 소요 시간이 바로 다시 계산됩니다.",
    act: `const h = () => document.querySelectorAll('.stop .drag-handle');
          await __demo.move(h()[3]); await __demo.sleep(500);
          h()[3].focus(); h()[3].dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowUp', altKey: true, bubbles: true }));
          await __demo.sleep(3000); await __demo.move('#sum-main'); await __demo.sleep(2000);`,
  },
  {
    id: "charge",
    say: "주행가능거리를 입력하면, 누적 거리가 그 70퍼센트를 넘기 전에 충전할 정류지를 추천해 줍니다. 경비는 항목별로 입력하고, 충전비는 이동 거리와 전비, 충전 단가로 자동 계산됩니다. 출발일의 날씨와 일몰 시각도 보여 주고, 일몰 이후에 도착하는 일정은 미리 알려 줍니다.",
    act: `await __demo.type('#plan-range', '60'); document.querySelector('#plan-range').dispatchEvent(new Event('change')); await __demo.sleep(2500);
          await __demo.move('.ev-line.is-warn'); await __demo.sleep(2000);
          const costs = document.querySelectorAll('.stop [data-field=cost]'); await __demo.type(costs[2], '3000'); await __demo.type(costs[3], '25000');
          const times = document.querySelectorAll('.stop [data-field=time]'); await __demo.move(times[3]); times[3].value = '19:30'; times[3].dispatchEvent(new Event('input', { bubbles: true }));
          await __demo.sleep(1500); await __demo.move('.sun-line:not(:empty)'); await __demo.sleep(1500); await __demo.move('#sum-weather'); await __demo.sleep(1500); await __demo.move('#sum-expense');`,
  },
  {
    id: "save",
    say: "완성한 계획은 저장해 두었다가 다시 불러올 수 있고, 마크다운 파일로 내보낼 수도 있습니다.",
    act: `await __demo.click('#btn-save'); await __demo.sleep(1500); await __demo.move('#btn-export'); await __demo.sleep(1200);
          await __demo.click('#tab-saved'); await __demo.sleep(1500);`,
  },
  {
    id: "hotplace",
    say: "다음은 핫플레이스입니다. 직접 다녀온 숨은 명소를 사진과 함께 등록합니다. 사진에 위치 정보가 있으면 촬영 위치와 날짜가 자동으로 채워지고, 등록 위치가 촬영 위치에서 1킬로미터 이내면 방문 인증 표시가 붙습니다.",
    url: "/hotplace.html",
    act: `await __demo.click('#btn-open-form'); await __demo.sleep(800);
          await __demo.type('#hp-title', '죽녹원 옆 충전 카페'); await __demo.select('#hp-category', 'ev');
          await __demo.move('#hp-photo');
          const bin = atob(window.__PHOTO); const bytes = new Uint8Array(bin.length); for (let i = 0; i < bin.length; i++) bytes[i] = bin.charCodeAt(i);
          const dt = new DataTransfer(); dt.items.add(new File([bytes], 'juknokwon.jpg', { type: 'image/jpeg' }));
          const f = document.querySelector('#hp-photo'); f.files = dt.files; f.dispatchEvent(new Event('change'));
          await __demo.sleep(3000); await __demo.move('#exif-hint'); await __demo.sleep(1500);
          await __demo.type('#hp-desc', '급속 충전 40분 동안 대숲 산책하고 커피 한 잔.', 40);
          await __demo.click('#hp-form button[type=submit]'); await __demo.sleep(2000); await __demo.move('.verify-badge'); await __demo.sleep(1200);`,
  },
  {
    id: "board",
    say: "커뮤니티에는 공지사항과 자유게시판이 있습니다. 글 작성과 수정, 삭제, 검색, 페이지 이동을 지원하고, 수정과 삭제는 작성자 본인만 할 수 있습니다.",
    url: "/board.html?type=free",
    act: `await __demo.sleep(800); await __demo.click('#btn-write'); await __demo.sleep(1500);`,
    act2: `await __demo.type('#title', '광주에서 담양까지, 충전 한 번이면 충분했어요', 35);
           await __demo.type('#content', '죽녹원 공영주차장 급속 충전기를 이용했습니다. 산책하는 동안 충전이 끝나서 좋았어요.', 25);
           await __demo.click('#btn-submit');`,
    act3: `await __demo.move('#post-title'); await __demo.sleep(1000); await __demo.move('#btn-edit'); await __demo.sleep(1000);`,
  },
  {
    id: "mypage",
    say: "마이페이지에서는 회원 정보를 수정하거나 탈퇴할 수 있고, 저장한 여행 계획을 바탕으로 장소 유형과 항목별 경비, 월별 여행 기록을 차트로 확인할 수 있습니다.",
    url: "/mypage.html",
    act: `await __demo.move('#v-name'); await __demo.sleep(1200); await __demo.move('#stat-tiles'); await __demo.sleep(1500);
          await __demo.move('#chart-types'); await __demo.sleep(2000); await __demo.move('#chart-expense'); await __demo.sleep(2000); await __demo.move('#heatmap');`,
  },
  {
    id: "outro",
    say: "마지막으로 개발자 소개 페이지입니다. 화면은 피그마로 먼저 설계했고, 모든 페이지는 반응형이라 모바일에서도 사용할 수 있습니다. 이것으로 EnjoyTrip EV 시연을 마치겠습니다. 감사합니다.",
    url: "/developers.html",
    act: `await __demo.move('.member-card'); await __demo.sleep(2500); await __demo.move('#stack-title'); await __demo.sleep(1500);`,
    mobileAfter: "/attraction.html?sido=12&gugun=210&type=12",
  },
];

/* ---------- 페이지에 주입하는 헬퍼: 가짜 커서 · 타이핑 · 자막 ---------- */
const HELPER = `(() => {
  if (window.__demo) return;
  const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
  let cur;
  const cursor = () => {
    if (cur && document.body.contains(cur)) return cur;
    cur = document.createElement('div');
    Object.assign(cur.style, { position: 'fixed', left: '50%', top: '40%', width: '26px', height: '26px', margin: '-13px 0 0 -13px',
      borderRadius: '50%', background: 'rgba(245,158,11,.55)', border: '2px solid #fff', boxShadow: '0 0 0 2px rgba(17,24,39,.55)',
      zIndex: 2147483646, pointerEvents: 'none', transition: 'left .55s ease, top .55s ease, transform .15s' });
    document.body.appendChild(cur);
    return cur;
  };
  const el = (s) => (typeof s === 'string' ? document.querySelector(s) : s);
  async function move(s) {
    const e = el(s); if (!e) { console.warn('demo: no element', s); return null; }
    e.scrollIntoView({ block: 'center', behavior: 'smooth' }); await sleep(450);
    const r = e.getBoundingClientRect(), c = cursor();
    c.style.left = Math.min(innerWidth - 20, r.left + Math.min(r.width / 2, 160)) + 'px'; c.style.top = (r.top + Math.min(r.height / 2, 120)) + 'px';
    await sleep(650); return e;
  }
  async function click(s) { const e = await move(s); if (!e) return; const c = cursor(); c.style.transform = 'scale(.65)'; await sleep(160); c.style.transform = ''; e.click(); await sleep(350); }
  async function type(s, text, delay = 55) {
    const e = await move(s); if (!e) return; e.focus(); e.value = '';
    for (const ch of text) { e.value += ch; e.dispatchEvent(new Event('input', { bubbles: true })); await sleep(delay); }
    e.dispatchEvent(new Event('change', { bubbles: true }));
  }
  async function select(s, v) { const e = await move(s); if (!e) return; e.value = v; e.dispatchEvent(new Event('change', { bubbles: true })); await sleep(300); }
  function caption(text) {
    let b = document.getElementById('__demo_cap');
    if (!b) { b = document.createElement('div'); b.id = '__demo_cap';
      Object.assign(b.style, { position: 'fixed', left: '50%', bottom: '22px', transform: 'translateX(-50%)', maxWidth: '78%', background: 'rgba(17,24,39,.85)',
        color: '#fff', font: '500 19px/1.55 "Noto Sans KR", "Malgun Gothic", sans-serif', padding: '8px 20px', borderRadius: '10px', zIndex: 2147483647,
        textAlign: 'center', pointerEvents: 'none', wordBreak: 'keep-all' });
      document.body.appendChild(b); }
    b.textContent = text; b.style.display = text ? 'block' : 'none';
  }
  window.__demo = { sleep, move, click, type, select, caption };
})();`;

/* ---------- CDP ---------- */
let ws, seq = 0;
const pending = new Map();
const listeners = new Set();
const send = (method, params = {}) => new Promise((res, rej) => { const id = ++seq; pending.set(id, { res, rej }); ws.send(JSON.stringify({ id, method, params })); });
const evaluate = async (expression) => {
  const r = await send("Runtime.evaluate", { expression, awaitPromise: true, returnByValue: true });
  if (r.exceptionDetails) throw new Error(r.exceptionDetails.exception?.description ?? r.exceptionDetails.text);
  return r.result.value;
};
const run = (code) => evaluate(`(async () => { ${code} })()`);
const go = async (path, wait = 3500) => { await send("Page.navigate", { url: BASE + path }); await sleep(wait); await evaluate(HELPER); };

/* ---------- 1) TTS ---------- */
const ffprobeDuration = (file) =>
  Number(execFileSync("ffprobe", ["-v", "error", "-show_entries", "format=duration", "-of", "csv=p=0", file]).toString().trim());

const splitSentences = (text) => text.match(/[^.!?]+[.!?]?/g).map((s) => s.trim()).filter(Boolean);

const makeAudio = () => {
  console.log("1) 내레이션 음성 생성 (edge-tts)");
  SCENES.forEach((s, i) => {
    s.audio = join(WORK, `s${i}.mp3`);
    execFileSync("python", ["-m", "edge_tts", "--voice", VOICE, "--rate=+8%", "--text", s.say, "--write-media", s.audio]);
    s.audioDur = ffprobeDuration(s.audio);
    console.log(`   ${String(i + 1).padStart(2)} ${s.id.padEnd(10)} ${s.audioDur.toFixed(1)}s`);
  });
};

/* ---------- 2) 녹화 ---------- */
const record = async () => {
  console.log("2) 화면 녹화");
  const profile = mkdtempSync(join(tmpdir(), "enjoytrip-demo-profile-"));
  const chrome = spawn(CHROME, ["--headless=new", `--remote-debugging-port=${PORT}`, `--user-data-dir=${profile}`, `--window-size=${W},${H}`,
    "--hide-scrollbars", "--no-first-run", "--lang=ko-KR", "--autoplay-policy=no-user-gesture-required", "about:blank"]);
  try {
    let target;
    for (let i = 0; i < 40 && !target; i++) {
      await sleep(250);
      target = await fetch(`http://127.0.0.1:${PORT}/json/list`).then((r) => r.json()).then((l) => l.find((t) => t.type === "page")).catch(() => null);
    }
    ws = new WebSocket(target.webSocketDebuggerUrl);
    ws.onmessage = ({ data }) => {
      const m = JSON.parse(data);
      if (m.id && pending.has(m.id)) { const p = pending.get(m.id); pending.delete(m.id); m.error ? p.rej(new Error(m.error.message)) : p.res(m.result); }
      else if (m.method) listeners.forEach((fn) => fn(m));
    };
    await new Promise((r) => (ws.onopen = r));
    await send("Page.enable");
    await send("Emulation.setDeviceMetricsOverride", { width: W, height: H, deviceScaleFactor: 1, mobile: false });
    await send("Browser.setDownloadBehavior", { behavior: "deny" }).catch(() => {});

    // 화면 프레임 수집
    let frames = null;
    listeners.add((m) => {
      if (m.method !== "Page.screencastFrame") return;
      send("Page.screencastFrameAck", { sessionId: m.params.sessionId }).catch(() => {});
      if (!frames) return;
      const file = join(WORK, `f${String(frames.all).padStart(6, "0")}.jpg`);
      writeFileSync(file, Buffer.from(m.params.data, "base64"));
      frames.list.push({ file, t: Date.now() });
      frames.all++;
    });
    let frameCounter = 0;

    for (const [i, s] of SCENES.entries()) {
      if (s.prepare) { await go("/index.html", 1200); await run(s.prepare); }
      if (s.url) await go(s.url);
      else await evaluate(HELPER);
      if (s.id === "hotplace") await evaluate(`window.__PHOTO = ${JSON.stringify(PHOTO_B64)}`);

      frames = { list: [], all: frameCounter };
      await send("Page.startScreencast", { format: "jpeg", quality: 82, maxWidth: W, maxHeight: H, everyNthFrame: 1 });
      const t0 = Date.now();

      // 자막: 문장 길이에 비례해 시간 배분 (노드에서 제어 → 장면 중 페이지 이동에도 유지)
      const sentences = splitSentences(s.say);
      const total = sentences.reduce((a, x) => a + x.length, 0);
      let acc = 0;
      s.cues = sentences.map((text) => { const start = (acc / total) * s.audioDur; acc += text.length; return { text, start, end: (acc / total) * s.audioDur }; });
      const timers = s.cues.map((c) => setTimeout(() => evaluate(`window.__demo?.caption(${JSON.stringify(c.text)})`).catch(() => {}), c.start * 1000));

      for (const [k, code] of [s.act, s.act2, s.act3].filter(Boolean).entries()) {
        if (k > 0) { await sleep(1800); await evaluate(HELPER).catch(() => {}); }
        // 클릭으로 페이지가 바뀌면 "Execution context was destroyed" 가 나는데, 정상 흐름이므로 무시한다
        await run(code).catch((e) => {
          if (!/context|navigat/i.test(e.message)) console.warn(`   ! ${s.id} 단계 ${k + 1}: ${e.message.split(/\r?\n/)[0]}`);
        });
      }
      try {
        if (s.mobileAfter) {
          await send("Emulation.setDeviceMetricsOverride", { width: 390, height: 844, deviceScaleFactor: 1, mobile: true });
          await go(s.mobileAfter, 4000);
          await run(`await __demo.move('.spot-item'); await __demo.sleep(1500);`);
        }
      } catch (e) {
        console.warn(`   ! ${s.id} 동작 중 오류(녹화는 계속): ${e.message.split("\n")[0]}`);
      }
      // 내레이션이 끝날 때까지 + 여유 0.8초
      const minEnd = t0 + s.audioDur * 1000 + 800;
      if (Date.now() < minEnd) await sleep(minEnd - Date.now());
      timers.forEach(clearTimeout);
      // 정지 화면이면 프레임이 안 오므로 마지막에 한 장 강제로 남긴다
      const { data } = await send("Page.captureScreenshot", { format: "jpeg", quality: 82 });
      const lastFile = join(WORK, `f${String(frames.all).padStart(6, "0")}.jpg`);
      writeFileSync(lastFile, Buffer.from(data, "base64"));
      frames.list.push({ file: lastFile, t: Date.now() });
      frames.all++;
      await send("Page.stopScreencast");
      s.frames = frames.list;
      s.t0 = t0;
      s.t1 = Date.now();
      frameCounter = frames.all;
      frames = null;
      await evaluate(`window.__demo?.caption('')`).catch(() => {});
      console.log(`   ${String(i + 1).padStart(2)} ${s.id.padEnd(10)} ${((s.t1 - s.t0) / 1000).toFixed(1)}s · 프레임 ${s.frames.length}`);
    }
  } finally {
    ws?.close();
    chrome.kill();
    await sleep(800);
    rmSync(profile, { recursive: true, force: true, maxRetries: 5, retryDelay: 300 });
  }
};

/* ---------- 3) 합성 ---------- */
const VF = `scale=${W}:${H}:force_original_aspect_ratio=decrease,pad=${W}:${H}:(ow-iw)/2:(oh-ih)/2:color=0xF8FAF9,format=yuv420p`;
const ff = (args) => execFileSync("ffmpeg", ["-y", "-loglevel", "error", ...args], { stdio: "inherit" });

/** JPEG 의 SOF 마커에서 가로·세로를 읽는다 */
const jpegSize = (buf) => {
  for (let i = 2; i < buf.length - 9; ) {
    if (buf[i] !== 0xff) { i++; continue; }
    const marker = buf[i + 1];
    if (marker >= 0xc0 && marker <= 0xc3) return { h: buf.readUInt16BE(i + 5), w: buf.readUInt16BE(i + 7) };
    i += 2 + buf.readUInt16BE(i + 2);
  }
  return null;
};

/**
 * 한 장면 안에서 프레임 크기가 바뀌면(데스크톱 → 모바일) concat 입력의 해상도가 계속 바뀌어
 * ffmpeg 필터가 프레임마다 다시 만들어져 인코딩이 극단적으로 느려진다. 미리 같은 캔버스로 맞춘다.
 */
const normalizeFrames = (frames) => {
  frames.forEach(({ file }) => {
    const size = jpegSize(readFileSync(file));
    if (size && size.w === W && size.h === H) return;
    const tmp = file.replace(/\.jpg$/, ".norm.jpg");
    ff(["-i", file, "-vf", VF.replace(",format=yuv420p", ""), "-q:v", "3", tmp]);
    writeFileSync(file, readFileSync(tmp));
    rmSync(tmp);
  });
};

const assemble = () => {
  console.log("3) 영상 합성 (ffmpeg)");
  const parts = [];
  const srt = [];
  let offset = 0;
  SCENES.forEach((s, i) => {
    const list = s.frames.filter((f) => f.t >= s.t0 - 50);
    normalizeFrames(list);
    const dur = (s.t1 - s.t0) / 1000;
    const lines = [];
    list.forEach((f, k) => {
      const next = k + 1 < list.length ? list[k + 1].t : s.t1;
      const start = k === 0 ? s.t0 : f.t;
      lines.push(`file '${f.file.replace(/\\/g, "/")}'`, `duration ${Math.max(0.001, (next - start) / 1000).toFixed(3)}`);
    });
    lines.push(`file '${list.at(-1).file.replace(/\\/g, "/")}'`); // concat demuxer 는 마지막 항목을 한 번 더 적어야 길이가 반영된다
    const listFile = join(WORK, `s${i}.txt`);
    writeFileSync(listFile, lines.join("\n"));
    const part = join(WORK, `part${i}.mp4`);
    ff(["-f", "concat", "-safe", "0", "-i", listFile, "-i", s.audio, "-vf", VF, "-r", "30", "-fps_mode", "cfr",
      "-c:v", "libx264", "-preset", "veryfast", "-crf", "26", "-af", "apad", "-t", dur.toFixed(3),
      "-c:a", "aac", "-b:a", "128k", "-ar", "44100", part]);
    parts.push(part);
    s.cues.forEach((c) => srt.push({ start: offset + c.start, end: offset + c.end, text: c.text }));
    offset += dur;
  });
  const concatList = join(WORK, "parts.txt");
  writeFileSync(concatList, parts.map((p) => `file '${p.replace(/\\/g, "/")}'`).join("\n"));
  mkdirSync(OUT_DIR, { recursive: true });
  const out = join(OUT_DIR, "enjoytrip-ev-demo.mp4");
  ff(["-f", "concat", "-safe", "0", "-i", concatList, "-c", "copy", "-movflags", "+faststart", out]);

  const ts = (sec) => {
    const ms = Math.round(sec * 1000), h = Math.floor(ms / 3600000), m = Math.floor(ms / 60000) % 60, s = Math.floor(ms / 1000) % 60;
    return `${String(h).padStart(2, "0")}:${String(m).padStart(2, "0")}:${String(s).padStart(2, "0")},${String(ms % 1000).padStart(3, "0")}`;
  };
  writeFileSync(join(OUT_DIR, "enjoytrip-ev-demo.srt"), srt.map((c, i) => `${i + 1}\n${ts(c.start)} --> ${ts(c.end)}\n${c.text}\n`).join("\n"));
  writeFileSync(join(OUT_DIR, "narration.md"),
    `# 시연 영상 내레이션 대본\n\n음성: ${VOICE} (edge-tts) · 총 ${Math.round(offset)}초\n\n` +
    SCENES.map((s, i) => `${i + 1}. **${s.id}** — ${s.say}`).join("\n\n") + "\n");
  console.log(`완료 → ${out} (${(offset / 60).toFixed(1)}분)`);
};

try {
  makeAudio();
  await record();
  assemble();
} finally {
  if (!process.env.KEEP_WORK) rmSync(WORK, { recursive: true, force: true, maxRetries: 5, retryDelay: 300 });
}
