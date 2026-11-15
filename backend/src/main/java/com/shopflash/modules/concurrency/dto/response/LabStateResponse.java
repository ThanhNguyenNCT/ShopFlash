package com.shopflash.modules.concurrency.dto.response;

/** Bộ đếm hiện tại; oracle hoặc redis là null nếu chưa reset lần nào. */
public record LabStateResponse(Oracle oracle, Redis redis, boolean running) {

    public record Oracle(long allocated, long sold, long version) {
    }

    public record Redis(long allocated, long remaining, long version) {
    }
}
