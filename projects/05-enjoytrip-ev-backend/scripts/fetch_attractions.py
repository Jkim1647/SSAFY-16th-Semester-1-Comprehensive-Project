"""한국관광공사 KorService2 에서 광주·전남(lDongRegnCd=12) 관광 데이터를 모아 src/main/resources/data/ 에 CSV 로 저장한다.

사용법:  DATA_GO_KR_API_KEY=... python -I scripts/fetch_attractions.py
- 키는 환경변수로만 받는다(파일·로그에 남기지 않음).
- 결과: attractions.csv (8개 콘텐츠 유형 전체), signgu.csv (시군구 코드)
- 앱은 시작할 때 이 CSV 를 DB 에 적재하므로, 데이터를 새로 받을 때만 실행하면 된다.
"""
import csv
import json
import os
import sys
import time
import urllib.parse
import urllib.request

END_POINT = "https://apis.data.go.kr/B551011/KorService2"
REGION = "12"  # 2026 법정동 개편 후 광주·전남 통합 코드
CONTENT_TYPES = ["12", "14", "15", "25", "28", "32", "38", "39"]
OUT_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "main", "resources", "data")


def call(operation, **params):
    key = os.environ.get("DATA_GO_KR_API_KEY")
    if not key:
        sys.exit("DATA_GO_KR_API_KEY 환경변수가 필요합니다.")
    query = {"serviceKey": key, "MobileOS": "ETC", "MobileApp": "EnjoyTrip", "_type": "json", **params}
    url = f"{END_POINT}/{operation}?{urllib.parse.urlencode(query)}"
    for attempt in range(3):
        try:
            with urllib.request.urlopen(url, timeout=30) as res:
                body = json.loads(res.read().decode("utf-8"))
            items = body["response"]["body"]["items"]
            total = int(body["response"]["body"]["totalCount"])
            return (items["item"] if items else []), total
        except Exception as e:  # 공공데이터포털은 간헐적으로 HTML 오류 페이지를 돌려준다
            if attempt == 2:
                raise RuntimeError(f"{operation} 호출 실패: {e}") from e
            time.sleep(2)


def fetch_signgu():
    items, _ = call("ldongCode2", numOfRows=100, pageNo=1, lDongRegnCd=REGION, lDongListYn="N")
    rows = sorted({(i["code"], i["name"]) for i in items})
    with open(os.path.join(OUT_DIR, "signgu.csv"), "w", newline="", encoding="utf-8") as f:
        w = csv.writer(f)
        w.writerow(["sidoCode", "signguCode", "name"])
        for code, name in rows:
            w.writerow([REGION, code, name])
    print(f"signgu: {len(rows)}")


def fetch_attractions():
    fields = ["contentId", "contentTypeId", "title", "addr1", "addr2", "tel", "firstImage", "firstImage2",
              "lat", "lng", "sidoCode", "signguCode", "lcls1", "lcls2", "lcls3"]
    seen = {}
    for type_id in CONTENT_TYPES:
        page, fetched = 1, 0
        while True:
            items, total = call("areaBasedList2", numOfRows=1000, pageNo=page, arrange="C",
                                lDongRegnCd=REGION, contentTypeId=type_id)
            for i in items:
                if not i.get("mapy") or not i.get("mapx"):
                    continue
                seen[i["contentid"]] = {
                    "contentId": i["contentid"], "contentTypeId": i["contenttypeid"], "title": i["title"],
                    "addr1": i.get("addr1", ""), "addr2": i.get("addr2", ""), "tel": i.get("tel", ""),
                    "firstImage": i.get("firstimage", "").replace("http://", "https://"),
                    "firstImage2": i.get("firstimage2", "").replace("http://", "https://"),
                    "lat": i["mapy"], "lng": i["mapx"], "sidoCode": i.get("lDongRegnCd", REGION),
                    "signguCode": i.get("lDongSignguCd", ""), "lcls1": i.get("lclsSystm1", ""),
                    "lcls2": i.get("lclsSystm2", ""), "lcls3": i.get("lclsSystm3", ""),
                }
            fetched += len(items)
            if not items or fetched >= total:
                break
            page += 1
        print(f"type {type_id}: {fetched}")
    with open(os.path.join(OUT_DIR, "attractions.csv"), "w", newline="", encoding="utf-8") as f:
        w = csv.DictWriter(f, fieldnames=fields)
        w.writeheader()
        w.writerows(sorted(seen.values(), key=lambda r: int(r["contentId"])))
    print(f"attractions: {len(seen)}")


if __name__ == "__main__":
    os.makedirs(OUT_DIR, exist_ok=True)
    fetch_signgu()
    fetch_attractions()
