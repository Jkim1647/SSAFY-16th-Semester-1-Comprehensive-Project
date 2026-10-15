#!/usr/bin/env sh
# 사용법: sh scripts/run.sh [test|server|demo|web|bench|bench2]   (프로젝트 루트에서 실행, JDK 17 이상)
#   test    전체 테스트 (algorithm-core 검증 + backend API 테스트)
#   server  Spring Boot 백엔드 실행 → http://localhost:5520
#   demo    #1·#2 실데이터 콘솔 시연
#   web     #1·#2 시연 결과를 시연 페이지 데이터(demo-result.js)로 내보내기
#   bench   #1·#2 성능 측정 / bench2  #3~#8 성능 측정
set -e
cd "$(dirname "$0")/.."
STATIC=backend/src/main/resources/static

# Windows(Git Bash)의 java 는 클래스패스 구분자로 ; 를 쓴다
case "$(uname -s)" in
  MINGW*|MSYS*|CYGWIN*) SEP=';' ;;
  *) SEP=':' ;;
esac
CP="algorithm-core/target/classes${SEP}algorithm-bench/target/classes"

bench_build() {
  ./mvnw -q -pl algorithm-core,algorithm-bench -am -DskipTests package
}

case "${1:-test}" in
  test)   ./mvnw -q verify && echo "전체 테스트 통과" ;;
  server) ./mvnw -q -DskipTests package && java -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -jar backend/target/backend-0.1.0-SNAPSHOT.jar ;;
  demo)   bench_build && java -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -cp "$CP" com.ssafy.enjoytrip.algorithm.demo.AlgorithmDemo data ;;
  web)    bench_build && java -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -cp "$CP" com.ssafy.enjoytrip.algorithm.demo.AlgorithmDemo data "$STATIC/demo-result.js" ;;
  bench)  bench_build && java -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -cp "$CP" com.ssafy.enjoytrip.algorithm.demo.AlgorithmBenchmark data ;;
  bench2) bench_build && java -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -cp "$CP" com.ssafy.enjoytrip.algorithm.demo.ExtendedBenchmark data ;;
  *) echo "사용법: sh scripts/run.sh [test|server|demo|web|bench|bench2]"; exit 1 ;;
esac
