package com.ssafy.trip.model.dao;

import java.util.LinkedList;
import java.util.List;

import com.ssafy.trip.model.dto.TripDto;
import com.ssafy.trip.model.dto.TripSearchDto;
import com.ssafy.trip.util.TouristDestinationSAXParser;

public class TripDaoImpl implements TripDao {

	private List<TripDto> tripInfo;

	public TripDaoImpl() {
		loadData();
	}

	/**
	 * 관광지 정보를 xml 파일에서 읽어온다.
	 */
	@Override
	public void loadData() {
		TouristDestinationSAXParser parser = new TouristDestinationSAXParser();
		tripInfo = parser.getTripInfo();
	}

	@Override
	public List<TripDto> searchAll(TripSearchDto tripSearchDto) {
		List<TripDto> finds = new LinkedList<TripDto>();

		String touristDestination = tripSearchDto.getTouristDestination(); // 관광지
		String sido = tripSearchDto.getSido(); // 주소
		if (touristDestination != null) {
			for (TripDto trip : tripInfo) {
				if (trip.getTouristDestination().contains(touristDestination)) {
					finds.add(trip);
				}
			}
		} else if (sido != null) {
			for (TripDto trip : tripInfo) {
				if (trip.getStreetAddress().contains(sido)) {
					finds.add(trip);
				}
			}
		} else {
			finds = tripInfo;
		}
		return finds;
	}

	@Override
	public TripDto search(int num) {
		for (TripDto trip : tripInfo) {
			if (trip.getNum() == num) {
				return trip;
			}
		}
		return null;
	}

	public static void print(List<TripDto> trips) {
		for (TripDto trip : trips) {
			System.out.println(trip);
		}
	}
}
