package com.ssafy.enjoytrip.controller;

import com.ssafy.enjoytrip.model.dto.TripDtos.PlanRequest;
import com.ssafy.enjoytrip.model.dto.TripDtos.PlanResponse;
import com.ssafy.enjoytrip.model.service.TripPlanService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 여행 계획 API: 관광지 방문 순서(#1) + 구간별 충전 계획(#2) */
@RestController
@RequestMapping("/api/trips")
public class TripController {

    private final TripPlanService service;

    public TripController(TripPlanService service) {
        this.service = service;
    }

    /**
     * 요청 예: {"start":{"name":"광주송정역","lat":35.1378,"lng":126.7932},
     *          "attractionIds":["128834","126730"],"returnToStart":true,"startSoc":60}
     */
    @PostMapping("/plan")
    public PlanResponse plan(@RequestBody PlanRequest request) {
        return service.plan(request);
    }
}
