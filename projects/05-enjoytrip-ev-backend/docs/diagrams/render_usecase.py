from pathlib import Path
from html import escape

# 배치를 직접 지정해 UML 액터와 유스케이스를 읽기 쉽게 유지한다.
groups = [
    ("비회원", 210, [("회원가입·로그인", 465, 170), ("비밀번호 재설정", 800, 170), ("관광지 검색·상세", 1135, 170), ("핫플·뉴스·게시판 조회", 465, 270), ("충전소 검색·상세", 800, 270), ("예약 시뮬레이션", 1135, 270)]),
    ("회원", 565, [("여행 계획 관리", 465, 430), ("핫플레이스 관리", 800, 430), ("충전 예약·취소", 1135, 430), ("게시글·댓글 관리", 465, 530), ("좋아요·북마크·신고", 800, 530), ("내 예약 조회", 1135, 530), ("내 정보 수정·탈퇴", 465, 630), ("로그아웃", 800, 630), ("경로 최적화·충전 경로", 1135, 630)]),
    ("관리자", 900, [("공지 작성·수정·삭제·고정", 465, 840), ("신고 검토·숨김·복구", 800, 840), ("회원 조회·삭제", 1135, 840), ("뉴스 수집", 465, 940)]),
]
parts = ['<svg xmlns="http://www.w3.org/2000/svg" width="1350" height="1080" viewBox="0 0 1350 1080" role="img" aria-label="EnjoyTrip 유스케이스 다이어그램">',
         '<rect width="1350" height="1080" fill="white"/>',
         '<g font-family="Malgun Gothic, sans-serif" fill="#111" stroke="#222" stroke-width="1.6">',
         '<rect x="300" y="35" width="1000" height="1010" fill="white"/>',
         '<text x="800" y="68" text-anchor="middle" font-size="24" stroke="none">EnjoyTrip 여행 서비스</text>']
for name, y, cases in groups:
    # 액터는 시스템 경계 밖에 위치한다. 각 선은 수행 가능한 기능과 연결된다.
    for label, x, cy in cases:
        parts.append(f'<path d="M 197 {y} L {x-125} {cy}" fill="none" stroke="#555" stroke-width="1.2"/>')
    parts.extend([f'<circle cx="155" cy="{y-45}" r="16" fill="white"/>',
                  f'<path d="M155 {y-29} V{y+17} M135 {y-12} H175 M155 {y+17} L135 {y+47} M155 {y+17} L175 {y+47}" fill="none"/>',
                  f'<text x="155" y="{y+76}" text-anchor="middle" font-size="20" stroke="none">{escape(name)}</text>'])
    for label, x, cy in cases:
        parts.append(f'<ellipse cx="{x}" cy="{cy}" rx="125" ry="32" fill="white"/>')
        parts.append(f'<text x="{x}" y="{cy+6}" text-anchor="middle" font-size="17" stroke="none">{escape(label)}</text>')
parts.append('</g></svg>')
Path(__file__).with_name('usecase.svg').write_text('\n'.join(parts), encoding='utf-8')
