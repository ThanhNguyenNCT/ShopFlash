package com.shopflash.modules.query.model;

/** AGGREGATE: FT.AGGREGATE chạy trên Redis. CLIENT: kéo document về Java rồi tự tính, để thấy số round-trip. */
public enum RedisMode {
    AGGREGATE, CLIENT
}
