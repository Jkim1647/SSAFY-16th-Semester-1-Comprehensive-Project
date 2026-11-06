"""싸피가 제공한 관광지(ssafy_trip.attractions)를 앱이 시작할 때 읽는 CSV(gzip)로 내보낸다.

앱(AppInitListener)이 이 파일을 읽어 enjoytrip.attractions 에 넣는다. ssafy_trip 에 싸피 덤프를 먼저 올려 두어야 한다.

  MYSQL="C:/Program Files/MySQL/MySQL Server 8.0/bin/mysql.exe" MYSQL_PWD=ssafy python scripts/export_ssafy_attractions.py
"""
import csv
import gzip
import os
import re
import subprocess
import sys

MYSQL = os.environ.get("MYSQL", "mysql")
USER = os.environ.get("MYSQL_USER", "ssafy")
HOST = os.environ.get("MYSQL_HOST", "127.0.0.1")
OUT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "data", "attractions-ssafy.csv.gz")

HEADER = ["contentId", "contentTypeId", "title", "addr1", "addr2", "tel", "firstImage", "firstImage2",
          "lat", "lng", "sidoCode", "signguCode", "overview", "homepage", "mapLevel"]
SQL = ("SELECT content_id, content_type_id, title, addr1, addr2, tel, first_image1, first_image2, latitude, longitude,"
       " area_code, si_gun_gu_code, overview, homepage, map_level FROM ssafy_trip.attractions ORDER BY content_id")

ESCAPES = {"0": "\0", "n": "\n", "t": "\t", "r": "\r", "\\": "\\"}


def unescape(field):
    """mysql -B(배치 모드)가 탭·줄바꿈·역슬래시를 \\t \\n \\\\ 로 바꿔서 내보내므로 되돌린다. NULL 은 빈 문자열로 둔다
    (배치 모드는 NULL 을 글자 NULL 로 찍는다. 싸피 관광지에 진짜 'NULL' 글자만 있는 값은 없다)."""
    if field in ("NULL", "\\N"):
        return ""
    return re.sub(r"\\(.)", lambda m: ESCAPES.get(m.group(1), m.group(1)), field)


def main():
    cmd = [MYSQL, "-u", USER, "-h", HOST, "--default-character-set=utf8mb4", "-B", "-N", "-e", SQL]
    raw = subprocess.run(cmd, check=True, capture_output=True).stdout.decode("utf-8")
    # Windows 의 mysql.exe 는 줄 끝을 \r\n 으로 찍는다. 값 안의 \r 은 \\r 글자로 바뀌어 나오므로 줄 끝의 \r 만 떼면 된다.
    rows = [line.rstrip("\r").split("\t") for line in raw.split("\n") if line.rstrip("\r")]
    for r in rows:
        if len(r) != len(HEADER):
            sys.exit("열 수가 맞지 않습니다: %d != %d" % (len(r), len(HEADER)))
    with gzip.open(OUT, "wt", encoding="utf-8", newline="") as f:
        w = csv.writer(f, lineterminator="\n")
        w.writerow(HEADER)
        for r in rows:
            w.writerow([unescape(x) for x in r])
    print("%d행 -> %s (%.1f MB)" % (len(rows), os.path.normpath(OUT), os.path.getsize(OUT) / 1e6))


if __name__ == "__main__":
    main()
