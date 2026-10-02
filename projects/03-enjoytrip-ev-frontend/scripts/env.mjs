// .env → assets/js/keys.js 생성
// 빌드 도구가 없는 정적 사이트라 브라우저가 .env 를 직접 읽을 수 없으므로,
// 키를 .env 한 곳에서 관리하고 이 스크립트로 브라우저용 모듈을 만들어 쓴다.
// 사용: npm run env   (또는 node scripts/env.mjs)
import { existsSync, readFileSync, writeFileSync } from "node:fs";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const root = resolve(dirname(fileURLToPath(import.meta.url)), "..");
const envPath = resolve(root, ".env");
const outPath = resolve(root, "assets/js/keys.js");
const KEYS = ["DATA_GO_KR_API_KEY", "KAKAO_MAP_KEY", "KAKAO_REST_KEY", "EV_API_KEY"];

if (!existsSync(envPath)) {
  console.error("[env] .env 파일이 없습니다. .env.example 을 복사해 .env 를 만든 뒤 키를 입력하세요.");
  process.exit(1);
}

const env = Object.fromEntries(
  readFileSync(envPath, "utf8")
    .split(/\r?\n/)
    .map((line) => line.trim())
    .filter((line) => line && !line.startsWith("#") && line.includes("="))
    .map((line) => {
      const i = line.indexOf("=");
      const value = line.slice(i + 1).trim().replace(/^(['"])(.*)\1$/, "$2");
      return [line.slice(0, i).trim(), value];
    })
);

const body = KEYS.map((k) => `export const ${k} = ${JSON.stringify(env[k] ?? "")};`).join("\n");
writeFileSync(outPath, `// 자동 생성 파일 — scripts/env.mjs 가 .env 에서 만든다. 직접 수정하거나 커밋하지 마세요.\n${body}\n`);

// 값은 출력하지 않고 설정 여부만 알린다
console.log("[env] assets/js/keys.js 생성 완료");
KEYS.forEach((k) => console.log(`  ${k}: ${env[k] ? "설정됨" : "비어 있음 (더미/안내 모드로 동작)"}`));
