package com.ssafy.enjoytrip.algorithm.ev;

/**
 * 전기차 제원과 충전 정책. SOC(State of Charge) 는 0~100 (%) 로 다룬다.
 * 기본값은 중형 전기 SUV(77.4kWh, 전비 5km/kWh)를 가정한 값이다.
 */
public class VehicleSpec {

    private final double batteryKwh;
    private final double kmPerKwh;
    private final double maxChargeKw;
    private final double reserveSoc;
    private final double targetSoc;
    private final double avgSpeedKmh;
    private final double chargingEfficiency;
    private final double stopOverheadMin;

    /**
     * @param batteryKwh         배터리 용량 (kWh)
     * @param kmPerKwh           전비 (km/kWh)
     * @param maxChargeKw        차량이 받아들이는 최대 충전 출력 (kW)
     * @param reserveSoc         이 아래로는 떨어지지 않게 남겨 둘 잔량 (%)
     * @param targetSoc          충전소에서 충전할 목표 잔량 (%). 80% 이후는 충전 속도가 급감하므로 80 을 권장
     * @param avgSpeedKmh        평균 주행 속도 (km/h)
     * @param chargingEfficiency 충전 효율 (0~1)
     * @param stopOverheadMin    충전소 진입·주차·결제 등 충전 1회당 고정 소요 시간 (분)
     */
    public VehicleSpec(double batteryKwh, double kmPerKwh, double maxChargeKw, double reserveSoc, double targetSoc,
                       double avgSpeedKmh, double chargingEfficiency, double stopOverheadMin) {
        require(batteryKwh > 0, "batteryKwh");
        require(kmPerKwh > 0, "kmPerKwh");
        require(maxChargeKw > 0, "maxChargeKw");
        require(reserveSoc >= 0 && reserveSoc < targetSoc && targetSoc <= 100, "0 <= reserveSoc < targetSoc <= 100");
        require(avgSpeedKmh > 0, "avgSpeedKmh");
        require(chargingEfficiency > 0 && chargingEfficiency <= 1, "chargingEfficiency");
        require(stopOverheadMin >= 0, "stopOverheadMin");
        this.batteryKwh = batteryKwh;
        this.kmPerKwh = kmPerKwh;
        this.maxChargeKw = maxChargeKw;
        this.reserveSoc = reserveSoc;
        this.targetSoc = targetSoc;
        this.avgSpeedKmh = avgSpeedKmh;
        this.chargingEfficiency = chargingEfficiency;
        this.stopOverheadMin = stopOverheadMin;
    }

    public static VehicleSpec defaultSpec() {
        return new VehicleSpec(77.4, 5.0, 230, 10, 80, 60, 0.9, 5);
    }

    private static void require(boolean ok, String name) {
        if (!ok) throw new IllegalArgumentException("차량 제원 값이 올바르지 않습니다: " + name);
    }

    /** 배터리 100% 로 갈 수 있는 거리 (km) */
    public double fullRangeKm() {
        return batteryKwh * kmPerKwh;
    }

    /** distanceKm 를 달리면 소모되는 SOC (%) */
    public double socUsed(double distanceKm) {
        return distanceKm / fullRangeKm() * 100;
    }

    public double driveMinutes(double distanceKm) {
        return distanceKm / avgSpeedKmh * 60;
    }

    /** fromSoc → toSoc 충전 시간 (분). 출력은 충전소와 차량 중 낮은 쪽, 80% 까지는 선형으로 근사한다. */
    public double chargeMinutes(double fromSoc, double toSoc, double stationKw) {
        if (toSoc <= fromSoc) return 0;
        double kwh = (toSoc - fromSoc) / 100 * batteryKwh;
        double kw = Math.min(stationKw, maxChargeKw) * chargingEfficiency;
        return kwh / kw * 60;
    }

    public double getBatteryKwh() {
        return batteryKwh;
    }

    public double getKmPerKwh() {
        return kmPerKwh;
    }

    public double getMaxChargeKw() {
        return maxChargeKw;
    }

    public double getReserveSoc() {
        return reserveSoc;
    }

    public double getTargetSoc() {
        return targetSoc;
    }

    public double getAvgSpeedKmh() {
        return avgSpeedKmh;
    }

    public double getChargingEfficiency() {
        return chargingEfficiency;
    }

    public double getStopOverheadMin() {
        return stopOverheadMin;
    }
}
