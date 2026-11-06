"""Render the README's use cases as an editable, reference-style UML SVG."""
from pathlib import Path
from html import escape

OUT = Path(__file__).with_name("usecase.svg")
actors = {"guest": (130, 300, "비회원"), "user": (130, 625, "회원"), "admin": (130, 950, "관리자"), "sim": (1670, 625, "가상 차량", "시뮬레이터")}
groups = [
 ("여행", 220, 55, 430, 1050, [("U1", "관광지 조회", "지역·유형·검색"), ("U2", "관광지 상세", "날씨·일출일몰·근처 충전소"), ("U3", "여행 계획 작성", "순서 최적화·충전 경로"), ("U4", "핫플레이스 등록", ""), ("U5", "뉴스 보기", "")]),
 ("충전", 685, 55, 430, 1050, [("E1", "충전소 지도·검색", ""), ("E2", "시간대 좌석 예약·취소", ""), ("E3", "예약 몰림 시뮬레이션 실행", "")]),
 ("회원·커뮤니티", 1150, 55, 430, 1050, [("M1", "회원가입·로그인", "비밀번호 찾기"), ("M2", "정보 수정·탈퇴", ""), ("B1", "자유게시판 CRUD", ""), ("B2", "공지사항 관리", ""), ("A1", "회원 관리", ""), ("A2", "뉴스 수집", "")]),
]
positions = {}
for _, x, y, w, h, items in groups:
    for i, (key, title, subtitle) in enumerate(items): positions[key] = (x+w/2, y+130+i*145, title, subtitle)
links = {"guest": ["U1", "U2", "U5", "E1", "E3", "M1"], "user": ["U3", "U4", "E2", "M2", "B1"], "admin": ["B2", "A1", "A2"], "sim": ["E2"]}
svg = ['<svg xmlns="http://www.w3.org/2000/svg" width="1800" height="1160" viewBox="0 0 1800 1160" role="img" aria-labelledby="title desc">', '<title id="title">EnjoyTrip EV 유스케이스 다이어그램</title>', '<desc id="desc">비회원, 회원, 관리자와 가상 차량이 여행, 충전, 회원 및 커뮤니티 기능을 사용하는 관계</desc>', '<rect width="1800" height="1160" fill="white"/>', '<defs><marker id="inherit" viewBox="0 0 12 12" refX="10" refY="6" markerWidth="10" markerHeight="10" orient="auto"><path d="M1 1 L10 6 L1 11 Z" fill="white" stroke="#4c5561" stroke-width="1.3"/></marker></defs>', '<g font-family="Malgun Gothic, Noto Sans KR, sans-serif" fill="#24313e">']
for title,x,y,w,h,_ in groups:
    svg.append(f'<rect x="{x}" y="{y}" width="{w}" height="{h}" rx="14" fill="#fafbfd" stroke="#ced5dc"/><text x="{x+24}" y="{y+42}" font-size="23" font-weight="bold">{escape(title)}</text>')
svg.append('<g fill="none" stroke="#83909e" stroke-width="1.5">')
for actor, targets in links.items():
    ax,ay,*_=actors[actor]
    for target in targets:
        x,y,*_=positions[target]; start=ax+24 if actor != "sim" else ax-24; end=x-162 if actor != "sim" else x+162
        if actor == "sim":
            svg.append(f'<path d="M {start} {ay} L 1610 {ay} L 1610 1090 L 1130 1090 L 1130 {y} L {end} {y}"/>')
        else:
            svg.append(f'<path d="M {start} {ay} L {end} {y}"/>')
svg.append('<path d="M 105 600 L 105 333" stroke-dasharray="6 5" marker-end="url(#inherit)"/><path d="M 105 925 L 105 658" stroke-dasharray="6 5" marker-end="url(#inherit)"/></g>')
for key,(x,y,title,subtitle) in positions.items():
    svg.append(f'<ellipse cx="{x}" cy="{y}" rx="162" ry="47" fill="#fff" stroke="#657584" stroke-width="1.8"/><text x="{x}" y="{y-5 if subtitle else y+7}" text-anchor="middle" font-size="19">{escape(title)}</text>')
    if subtitle: svg.append(f'<text x="{x}" y="{y+19}" text-anchor="middle" font-size="14" fill="#526270">{escape(subtitle)}</text>')
for x,y,*labels in actors.values():
    svg.append(f'<g stroke="#293746" stroke-width="2.2" fill="white"><circle cx="{x}" cy="{y-31}" r="15"/><path fill="none" d="M{x} {y-16} v39 m-25-22 h50 m-25 22 l-22 29 m22-29 l22 29"/></g>')
    for i,label in enumerate(labels): svg.append(f'<text x="{x}" y="{y+80+i*22}" text-anchor="middle" font-size="19">{escape(label)}</text>')
svg.append('</g></svg>')
OUT.write_text("\n".join(svg),encoding="utf-8")
