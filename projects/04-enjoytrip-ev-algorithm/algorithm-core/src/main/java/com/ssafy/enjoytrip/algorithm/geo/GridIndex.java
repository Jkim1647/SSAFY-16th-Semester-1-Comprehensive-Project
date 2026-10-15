package com.ssafy.enjoytrip.algorithm.geo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

/**
 * 격자(그리드) 공간 인덱스. 지도를 cellKm 크기의 칸으로 나누고 각 지점을 칸에 넣어 두면,
 * "반경 r km 안의 지점"이나 "가장 가까운 k 곳"을 찾을 때 전체(N)를 보지 않고 주변 칸만 확인하면 된다.
 *
 * <ul>
 *   <li>만들기 O(N)</li>
 *   <li>반경 검색: 반경을 덮는 칸들 안의 지점만 거리 계산 — 지점이 고르게 퍼져 있으면 O(반경 안 지점 수 + 칸 수)</li>
 *   <li>최근접 k: 가운데 칸부터 고리 모양으로 넓혀 가며 크기 k 의 최대 힙을 유지하고,
 *       다음 고리까지의 최소 거리가 현재 k 번째 거리보다 멀면 멈춘다</li>
 * </ul>
 * 거리는 직선(대원) 거리다. 만든 뒤에는 읽기만 하므로 여러 스레드에서 공유해도 된다.
 */
public class GridIndex<T extends Location> {

    private static final double KM_PER_DEG_LAT = 111.32;

    private final double cellKm;
    private final double cellLat;
    private final double cellLng;
    private final Map<Long, List<T>> cells = new HashMap<Long, List<T>>();
    private final int size;
    private int minRow = Integer.MAX_VALUE;
    private int maxRow = Integer.MIN_VALUE;
    private int minCol = Integer.MAX_VALUE;
    private int maxCol = Integer.MIN_VALUE;

    /**
     * @param refLat 경도 1도의 km 를 정할 기준 위도 (데이터 지역의 중앙). 한국은 35~37 정도
     */
    public GridIndex(List<? extends T> points, double cellKm, double refLat) {
        if (points == null) throw new IllegalArgumentException("points 가 null 입니다.");
        if (!(cellKm > 0)) throw new IllegalArgumentException("cellKm 는 0보다 커야 합니다.");
        this.cellKm = cellKm;
        this.cellLat = cellKm / KM_PER_DEG_LAT;
        this.cellLng = cellKm / (KM_PER_DEG_LAT * Math.cos(Math.toRadians(refLat)));
        for (T p : points) {
            int row = row(p.getLat());
            int col = col(p.getLng());
            List<T> list = cells.get(key(row, col));
            if (list == null) {
                list = new ArrayList<T>();
                cells.put(key(row, col), list);
            }
            list.add(p);
            minRow = Math.min(minRow, row);
            maxRow = Math.max(maxRow, row);
            minCol = Math.min(minCol, col);
            maxCol = Math.max(maxCol, col);
        }
        this.size = points.size();
    }

    public int size() {
        return size;
    }

    /** 중심에서 직선 radiusKm 이내의 지점들 (순서 없음) */
    public List<T> within(double lat, double lng, double radiusKm) {
        List<T> result = new ArrayList<T>();
        if (size == 0 || radiusKm < 0) return result;
        int dr = (int) Math.ceil(radiusKm / cellKm) + 1;
        // 경도 1칸의 실제 km 는 위도에 따라 달라지므로 열 방향 칸 수는 따로 계산
        int dc = (int) Math.ceil(radiusKm / (cellLng * KM_PER_DEG_LAT * Math.cos(Math.toRadians(lat)))) + 1;
        int r0 = row(lat);
        int c0 = col(lng);
        for (int r = Math.max(r0 - dr, minRow); r <= Math.min(r0 + dr, maxRow); r++) {
            for (int c = Math.max(c0 - dc, minCol); c <= Math.min(c0 + dc, maxCol); c++) {
                List<T> list = cells.get(key(r, c));
                if (list == null) continue;
                for (T p : list) {
                    if (HaversineDistance.greatCircleKm(lat, lng, p.getLat(), p.getLng()) <= radiusKm) result.add(p);
                }
            }
        }
        return result;
    }

    public int countWithin(double lat, double lng, double radiusKm) {
        return within(lat, lng, radiusKm).size();
    }

    /** 가까운 순으로 최대 k 개 */
    public List<T> nearest(final double lat, final double lng, int k) {
        if (k < 1 || size == 0) return Collections.emptyList();
        // 가장 먼 것이 맨 위에 오는 최대 힙 (크기 k 유지)
        PriorityQueue<Object[]> heap = new PriorityQueue<Object[]>(k, new Comparator<Object[]>() {
            @Override
            public int compare(Object[] a, Object[] b) {
                return Double.compare((Double) b[1], (Double) a[1]);
            }
        });
        int r0 = row(lat);
        int c0 = col(lng);
        int maxRing = Math.max(Math.max(Math.abs(r0 - minRow), Math.abs(r0 - maxRow)),
                Math.max(Math.abs(c0 - minCol), Math.abs(c0 - maxCol)));
        // 경도 방향 칸이 위도 방향보다 좁을 수 있으므로, 고리 하나당 확실히 보장되는 거리는 짧은 쪽 기준
        double ringKm = Math.min(cellLat * KM_PER_DEG_LAT, cellLng * KM_PER_DEG_LAT * Math.cos(Math.toRadians(lat)));
        for (int ring = 0; ring <= maxRing; ring++) {
            if (heap.size() == k && (ring - 1) * ringKm > (Double) heap.peek()[1]) break;
            for (int r = r0 - ring; r <= r0 + ring; r++) {
                for (int c = c0 - ring; c <= c0 + ring; c++) {
                    if (Math.max(Math.abs(r - r0), Math.abs(c - c0)) != ring) continue; // 고리 테두리만
                    List<T> list = cells.get(key(r, c));
                    if (list == null) continue;
                    for (T p : list) {
                        double d = HaversineDistance.greatCircleKm(lat, lng, p.getLat(), p.getLng());
                        if (heap.size() < k) heap.add(new Object[]{p, d});
                        else if (d < (Double) heap.peek()[1]) {
                            heap.poll();
                            heap.add(new Object[]{p, d});
                        }
                    }
                }
            }
        }
        List<Object[]> sorted = new ArrayList<Object[]>(heap);
        Collections.sort(sorted, new Comparator<Object[]>() {
            @Override
            public int compare(Object[] a, Object[] b) {
                return Double.compare((Double) a[1], (Double) b[1]);
            }
        });
        List<T> result = new ArrayList<T>(sorted.size());
        for (Object[] e : sorted) {
            @SuppressWarnings("unchecked")
            T p = (T) e[0];
            result.add(p);
        }
        return result;
    }

    private int row(double lat) {
        return (int) Math.floor(lat / cellLat);
    }

    private int col(double lng) {
        return (int) Math.floor(lng / cellLng);
    }

    private static long key(int row, int col) {
        return ((long) row << 32) ^ (col & 0xffffffffL);
    }
}
