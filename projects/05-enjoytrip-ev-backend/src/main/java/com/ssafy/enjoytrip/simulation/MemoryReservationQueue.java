package com.ssafy.enjoytrip.simulation;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * 메모리 큐. partitioned=false 면 작업자 전원이 한 줄에서 꺼내 가서 같은 충전소 요청이 동시에 DB 에 도착한다(경합 발생).
 * partitioned=true 면 Kafka 파티션처럼 충전소 ID 해시로 줄을 나누고 줄마다 작업자 한 명이 순서대로 처리한다
 * → 같은 충전소 요청은 선착순으로 하나씩 처리되어 잠금 경합이 사라진다.
 */
public class MemoryReservationQueue implements ReservationQueue {

    private final boolean partitioned;
    private final List<BlockingQueue<ReservationRequest>> lanes = new ArrayList<>();
    private final List<Thread> workers = new ArrayList<>();
    private final int workerCount;
    private volatile boolean running;

    public MemoryReservationQueue(int workerCount, boolean partitioned) {
        this.workerCount = workerCount;
        this.partitioned = partitioned;
        int laneCount = partitioned ? workerCount : 1;
        for (int i = 0; i < laneCount; i++) {
            lanes.add(new LinkedBlockingQueue<>());
        }
    }

    @Override
    public void start(Consumer<ReservationRequest> handler) {
        running = true;
        for (int i = 0; i < workerCount; i++) {
            BlockingQueue<ReservationRequest> lane = lanes.get(partitioned ? i : 0);
            Thread t = new Thread(() -> {
                while (running) {
                    try {
                        ReservationRequest r = lane.poll(200, TimeUnit.MILLISECONDS);
                        if (r != null) {
                            handler.accept(r);
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    } catch (RuntimeException e) {
                        System.err.println("[sim] 작업자 오류: " + e.getMessage());
                    }
                }
            }, "sim-worker-" + i);
            t.setDaemon(true);
            t.start();
            workers.add(t);
        }
    }

    @Override
    public void publish(ReservationRequest request) {
        int lane = partitioned ? Math.floorMod(request.statId().hashCode(), lanes.size()) : 0;
        lanes.get(lane).add(request);
    }

    @Override
    public int depth() {
        return lanes.stream().mapToInt(BlockingQueue::size).sum();
    }

    @Override
    public String label() {
        return partitioned ? "memory·파티션" : "memory";
    }

    @Override
    public void close() {
        running = false;
        workers.forEach(Thread::interrupt);
    }
}
