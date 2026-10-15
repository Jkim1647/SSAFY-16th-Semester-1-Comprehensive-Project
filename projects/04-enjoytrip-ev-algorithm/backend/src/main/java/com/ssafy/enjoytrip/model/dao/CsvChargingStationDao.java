package com.ssafy.enjoytrip.model.dao;

import com.ssafy.enjoytrip.algorithm.ev.ChargingStation;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** data/ev-stations-gwangju-jeonnam.csv (statId,statNm,lat,lng,maxKw,chargers) */
@Repository
public class CsvChargingStationDao implements ChargingStationDao {

    static final String FILE = "ev-stations-gwangju-jeonnam.csv";

    private final List<ChargingStation> stations;

    public CsvChargingStationDao(CsvDataSource source) {
        List<ChargingStation> list = new ArrayList<>();
        for (String[] f : source.rows(FILE)) {
            list.add(new ChargingStation(f[0], f[1], Double.parseDouble(f[2]), Double.parseDouble(f[3]), Double.parseDouble(f[4])));
        }
        this.stations = Collections.unmodifiableList(list);
    }

    @Override
    public List<ChargingStation> findAll() {
        return stations;
    }
}
