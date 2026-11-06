<%@ page contentType="text/html; charset=UTF-8"%>
<c:set var="menu" value="sim" />
<!doctype html>
<html lang="ko">
<head>
<title>충전 티켓팅 · 예약 몰림 시뮬레이션 · EnjoyTrip EV</title>
<%@ include file="../fragments/head.jsp" %>
<link rel="stylesheet" href="${root}/assets/css/sim.css?v=20261030-1">
</head>
<body>
<%@ include file="../fragments/header.jsp" %>

<%-- ① 이야기로 시작: 클릭/스페이스로 다음 장면 --%>
<section class="intro" id="intro" aria-label="이야기" hidden data-auto-open="${activeRunId <= 0 && param.skip != '1'}">
  <div class="intro-scene" data-scene>
    <h2>연휴 전날, 오후 6시 40분.</h2>
    <p>광주로 들어오는 고속도로는 벌써 붉은 미등으로 가득합니다.</p>
  </div>
  <div class="intro-scene" data-scene hidden>
    <div class="big-battery" id="intro-battery"><i></i></div>
    <h2>배터리 <b class="hl-red">12%</b>.<br>다음 충전소까지 38km.</h2>
  </div>
  <div class="intro-scene" data-scene hidden>
    <h2>도심 급속충전소의 충전기는<br><b class="hl-amber">수십 대</b>뿐입니다.</h2>
    <p>그리고 지금, 같은 시간대를 노리는 전기차 <b class="hl-amber" id="intro-cars">800</b>대가<br>동시에 예약 버튼을 누릅니다.</p>
  </div>
  <div class="intro-scene" data-scene hidden>
    <h2>충전도 이제 <b class="hl-green">티켓팅</b>입니다.</h2>
    <p>동시에 눌러도 한 자리에는 한 대만. 그 약속을 지킬 수 있을까요?</p>
    <button type="button" class="btn btn-light btn-press" id="btn-press">예약 버튼 누르기</button>
  </div>
  <div class="intro-dots" aria-hidden="true"></div>
  <span class="intro-hint">클릭하거나 스페이스를 누르면 다음으로</span>
  <div class="intro-controls">
    <label class="intro-hide-today" for="intro-hide-today"><input type="checkbox" id="intro-hide-today"> 오늘 하루 다시 보지 않기</label>
    <button type="button" class="btn btn-sm btn-outline-light" id="btn-skip">건너뛰기</button>
  </div>
</section>

<%-- ③ 충전 티켓팅 대시보드 --%>
<main class="sim-page">
  <div class="sim-wrap">
    <div class="sim-head d-flex flex-wrap justify-content-between align-items-end gap-2 mb-3">
      <div>
        <h1 class="mb-1">충전 티켓팅 — 예약 몰림 시뮬레이션</h1>
        <p class="small text-body-secondary mb-0" id="scenario">내일 저녁 7시, 광주 도심 급속충전소. 가상 차량이 줄을 서서 실제 DB 예약을 시도합니다.</p>
      </div>
      <button class="btn btn-sm btn-outline-secondary" type="button" id="btn-replay">이야기 다시 보기</button>
    </div>

    <form class="panel run-form mb-3" id="run-form">
      <div class="row g-2 align-items-end">
        <div class="col-6 col-md-2">
          <label class="form-label small mb-1" for="vehicles">차량 수</label>
          <select class="form-select" id="vehicles" name="vehicles">
            <option>300</option><option selected>800</option><option>1500</option><option>3000</option>
          </select>
        </div>
        <div class="col-6 col-md-2">
          <label class="form-label small mb-1" for="stations">충전소 수</label>
          <select class="form-select" id="stations" name="stations">
            <option>8</option><option selected>16</option><option>32</option>
          </select>
        </div>
        <div class="col-6 col-md-3">
          <label class="form-label small mb-1" for="mode">예약 방식</label>
          <select class="form-select" id="mode" name="mode">
            <option value="LOCKED" selected>잠금 예약 (트랜잭션 + FOR UPDATE)</option>
            <option value="NAIVE">잠금 없음 (세고 → 넣기)</option>
          </select>
        </div>
        <div class="col-6 col-md-3">
          <label class="form-label small mb-1" for="queue">대기열(큐)</label>
          <select class="form-select" id="queue" name="queue">
            <option value="memory" selected>메모리 큐 (작업자 16명 공유)</option>
            <option value="memory-partitioned">메모리 큐 · 충전소별 줄</option>
            <option value="kafka">Kafka (키 = 충전소, 파티션 8)</option>
          </select>
        </div>
        <div class="col-12 col-md-2 d-grid">
          <button class="btn btn-primary" type="submit" id="btn-start">시작</button>
        </div>
      </div>
      <p class="small text-danger mb-0 mt-2" id="run-error" role="alert"></p>
    </form>

    <div class="row g-3">
      <div class="col-lg-7 vstack gap-3">
        <div class="panel panel-dark">
          <div class="my-car">
            <div class="ticket"><small>내 대기 번호</small><strong id="my-ticket">-</strong></div>
            <div>
              <div class="small" style="color:#9aa3b8"><span class="mini-battery"><i style="width:12%"></i></span>내 차 · 배터리 <span id="my-soc">12</span>%</div>
              <div class="status wait" id="my-status" aria-live="polite">시작을 누르면 줄을 섭니다.</div>
            </div>
          </div>
        </div>

        <div class="kpis" id="kpis">
          <div class="kpi"><div class="v" id="k-published">0</div><div class="l">줄 선 차량</div></div>
          <div class="kpi"><div class="v" id="k-queued">0</div><div class="l">큐 대기</div></div>
          <div class="kpi is-good"><div class="v" id="k-confirmed">0</div><div class="l">예약 확정</div></div>
          <div class="kpi"><div class="v" id="k-retries">0</div><div class="l">다음 지망 재도전</div></div>
          <div class="kpi"><div class="v" id="k-rejected">0</div><div class="l">예약 실패</div></div>
          <div class="kpi"><div class="v" id="k-elapsed">0.0s</div><div class="l">경과 · <span id="k-tps">0</span>건/초</div></div>
          <div class="kpi" id="kpi-over"><div class="v" id="k-over">-</div><div class="l">오버부킹 (정원 초과)</div></div>
        </div>

        <div class="panel panel-dark">
          <h2>① 차량 <span id="grid-count">0</span>대의 배터리 <span class="sub">한 칸 = 차 한 대 · 예약에 성공하면 초록으로 바뀝니다</span></h2>
          <canvas class="battery-grid" id="grid" height="200" aria-label="차량별 배터리와 예약 상태"></canvas>
          <div class="legend">
            <span style="--c:#ff4d5e">배터리 15% 미만</span><span style="--c:#ffb347">15~35%</span><span style="--c:#c9d36a">35% 이상</span>
            <span style="--c:#41d98a">예약 확정</span><span style="--c:#4a1d24">예약 실패</span><span style="--c:#fff">내 차</span>
          </div>
        </div>

        <div class="panel">
          <h2>실시간 처리 로그</h2>
          <div class="log" id="log" role="log" aria-live="off"></div>
        </div>
      </div>

      <div class="col-lg-5 vstack gap-3">
        <div class="panel">
          <h2>③ 좌석 배치도 <span class="sub">행 = 충전소, 칸 = 충전기. DB 에 실제로 들어간 예약입니다.</span></h2>
          <div class="seat-board" id="seats"><p class="text-body-secondary small mb-0">시뮬레이션을 시작하면 좌석이 채워집니다.</p></div>
          <div class="legend" style="color:#6b7280">
            <span style="--c:#dfe5f1">빈 자리</span><span style="--c:#1f6bff">예약됨</span><span style="--c:#41d98a">내 차</span><span style="--c:#ff4d5e">정원 초과(오버부킹)</span>
          </div>
        </div>

        <div class="panel how">
          <h2>한 자리에 한 대만 — 어떻게?</h2>
          <p class="small mb-1"><strong>잠금 예약</strong>: 트랜잭션 안에서 해당 충전소·시간대 행을 잠그고 남은 자리를 확인합니다. 같은 자리를 노리는 요청은 앞 요청이 commit 할 때까지 기다립니다.</p>
          <pre>SELECT capacity, booked FROM ev_slots
 WHERE run_id=? AND stat_id=? AND slot_start=? FOR UPDATE;
-- booked &lt; capacity 일 때만
INSERT INTO ev_reservations ...;
UPDATE ev_slots SET booked = booked + 1 ...;
COMMIT;</pre>
          <p class="small mt-2 mb-1"><strong>잠금 없음</strong>: "몇 명 예약했지?" 를 센 뒤 넣습니다. 세는 순간과 넣는 순간 사이에 다른 요청이 같은 숫자를 보고 끼어들어 정원을 넘깁니다.</p>
          <p class="small mb-0"><strong>충전소별 줄 / Kafka</strong>: 메시지 키를 충전소로 두면 같은 충전소 요청이 한 줄(파티션)에 대기 번호 순으로 쌓이고 한 작업자가 차례로 처리합니다. 잠금 경합 없이 선착순이 보장됩니다.</p>
        </div>

        <div class="panel">
          <h2>지난 실행 비교</h2>
          <div class="table-responsive">
            <table class="table table-sm history-table mb-0">
              <thead><tr><th>#</th><th>방식</th><th>큐</th><th>차량</th><th>확정</th><th>오버부킹</th><th>시간</th></tr></thead>
              <tbody id="history">
                <c:forEach var="h" items="${history}">
                  <tr>
                    <td>${h.runId}</td>
                    <td>${h.mode == 'LOCKED' ? '잠금' : '잠금 없음'}</td>
                    <td><c:out value="${h.queueType}" /></td>
                    <td><fmt:formatNumber value="${h.vehicles}" /></td>
                    <td><fmt:formatNumber value="${h.confirmed}" /></td>
                    <td class="${h.overbooked > 0 ? 'text-danger fw-bold' : 'text-success fw-bold'}">${h.status == 'DONE' ? h.overbooked : '-'}</td>
                    <td>${empty h.durationMs ? '-' : h.durationMs}${empty h.durationMs ? '' : 'ms'}</td>
                  </tr>
                </c:forEach>
              </tbody>
            </table>
          </div>
        </div>
      </div>
    </div>
  </div>
</main>
<script type="application/json" id="sim-boot">{"activeRunId": ${activeRunId}}</script>
<%@ include file="../fragments/scripts.jspf" %>
<script src="${root}/assets/js/sim-intro-preference.js?v=20261030-1"></script>
<script src="${root}/assets/js/sim.js?v=20261030-1"></script>
</body>
</html>
