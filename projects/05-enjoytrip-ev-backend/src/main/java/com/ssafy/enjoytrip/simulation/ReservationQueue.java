package com.ssafy.enjoytrip.simulation;

import java.util.function.Consumer;

/**
 * 예약 요청을 받아 두었다가 작업자에게 나눠 주는 큐.
 * 요청이 한꺼번에 몰려도 웹 요청 스레드가 DB 를 직접 두드리지 않고, 정해진 수의 작업자가 감당할 수 있는 속도로 꺼내 처리한다.
 * 구현: 메모리(LinkedBlockingQueue) / Kafka(토픽 + 컨슈머 그룹).
 */
public interface ReservationQueue extends AutoCloseable {

    /** 작업자를 띄우고 handler 로 요청을 처리하기 시작한다. */
    void start(Consumer<ReservationRequest> handler);

    void publish(ReservationRequest request);

    /** 아직 처리되지 않은 요청 수 (추정치). */
    int depth();

    /** 화면에 보여줄 이름: memory, memory·파티션, kafka */
    String label();

    @Override
    void close();
}
