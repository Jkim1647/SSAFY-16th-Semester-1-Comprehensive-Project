#!/usr/bin/env bash
# 빌드 후 Tomcat 11 로 실행한다. Eclipse/STS 없이도 같은 결과를 확인하기 위한 스크립트.
#   sh scripts/run.sh            # 빌드 + 실행 (Ctrl+C 로 종료)
#   sh scripts/run.sh build      # 빌드만
#   sh scripts/run.sh test       # 단위 테스트
# 환경변수: JAVA_HOME, CATALINA_HOME(기본 C:/SSAFY/apache-tomcat-11.0.27), PORT(기본 8090), BUILD_DIR(기본 target)
# .env 파일이 있으면 읽는다 (예: ENJOYTRIP_ADMIN_PASSWORD=..., ENJOYTRIP_DB_PASSWORD=...).
set -euo pipefail
cd "$(dirname "$0")/.."

if [ -f .env ]; then set -a; . ./.env; set +a; fi

if [ -z "${JAVA_HOME:-}" ]; then
  for c in "/c/SSAFY/tools/jdk-17.0.20.1+1" /c/Users/*/.vscode/extensions/redhat.java-*/jre/*; do
    if [ -x "$c/bin/java" ] || [ -x "$c/bin/java.exe" ]; then export JAVA_HOME="$c"; break; fi
  done
fi
: "${JAVA_HOME:?JAVA_HOME 을 JDK 17 이상으로 설정하세요}"
CATALINA_HOME="${CATALINA_HOME:-/c/SSAFY/apache-tomcat-11.0.27}"
PORT="${PORT:-8090}"
BUILD_DIR="${BUILD_DIR:-target}"
BASE="$PWD/.tomcat"
[ "$PORT" = "8090" ] || BASE="$PWD/.tomcat-$PORT"

case "${1:-run}" in
  test) ./mvnw -q -Dbuild.dir="$BUILD_DIR" test; exit ;;
  build) ./mvnw -q -Dbuild.dir="$BUILD_DIR" -DskipTests package; exit ;;
esac

./mvnw -q -Dbuild.dir="$BUILD_DIR" -DskipTests package

# 프로젝트 전용 CATALINA_BASE: 설치된 Tomcat(Eclipse 서버 설정 포함)을 건드리지 않는다
mkdir -p "$BASE"/{conf,logs,temp,work,webapps}
cp -n "$CATALINA_HOME"/conf/* "$BASE/conf/" 2>/dev/null || true
sed -i -E "s/port=\"8080\"/port=\"$PORT\"/; s/<Server port=\"8005\"/<Server port=\"-1\"/" "$BASE/conf/server.xml"
rm -rf "$BASE/webapps/enjoytrip" "$BASE/work/Catalina"
cp -r "$BUILD_DIR/enjoytrip" "$BASE/webapps/enjoytrip"

echo "▶ http://localhost:$PORT/enjoytrip/"
if command -v cygpath >/dev/null 2>&1; then
  # Git Bash: Windows java 는 /c/... 경로를 모르므로 C:/... 로 바꾸고 클래스패스 구분자는 ; 를 쓴다
  CATALINA_HOME="$(cygpath -m "$CATALINA_HOME")"
  BASE="$(cygpath -m "$BASE")"
  CP="$CATALINA_HOME/bin/bootstrap.jar;$CATALINA_HOME/bin/tomcat-juli.jar"
else
  CP="$CATALINA_HOME/bin/bootstrap.jar:$CATALINA_HOME/bin/tomcat-juli.jar"
fi
exec "$JAVA_HOME/bin/java" -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 \
  -Dcatalina.home="$CATALINA_HOME" -Dcatalina.base="$BASE" -Djava.io.tmpdir="$BASE/temp" \
  -cp "$CP" org.apache.catalina.startup.Bootstrap start
