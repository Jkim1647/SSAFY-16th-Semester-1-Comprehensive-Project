// README 용 차트 이미지(docs/charts/*.svg) 생성. 시연 페이지와 같은 charts.js 를 쓴다.
// 사용법: node scripts/charts.mjs   (프로젝트 루트에서, Node 18 이상, 외부 패키지 없음)
import fs from "node:fs";
import path from "node:path";
import vm from "node:vm";
import { createRequire } from "node:module";
import { fileURLToPath } from "node:url";

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const require = createRequire(import.meta.url);
const staticDir = path.join(root, "backend", "src", "main", "resources", "static");
const Charts = require(path.join(staticDir, "charts.js"));

// window.X = ... 형태의 데이터 파일을 읽는다
const load = (file, name) => {
  const ctx = { window: {} };
  vm.runInNewContext(fs.readFileSync(path.join(staticDir, file), "utf8"), ctx);
  if (!ctx.window[name]) throw new Error(`${file} 에 window.${name} 이 없습니다.`);
  return ctx.window[name];
};

const outDir = path.join(root, "docs", "charts");
fs.mkdirSync(outDir, { recursive: true });
const charts = Charts.all(load("bench-data.js", "BENCH"), load("demo-result.js", "DEMO_RESULT"));
for (const c of charts) {
  fs.writeFileSync(path.join(outDir, `${c.id}.svg`), c.svg + "\n", "utf8");
}
console.log(`docs/charts 에 차트 ${charts.length}개 생성: ${charts.map((c) => c.id).join(", ")}`);
