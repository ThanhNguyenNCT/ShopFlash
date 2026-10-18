package com.shopflash.modules.storage.dto.response;

import java.util.List;

/** Hai đơn vị đo khác nhau, chỉ dùng để trình bày Storage Lab. */
public record StorageFootprintResponse(long oracleAllocatedBytes, long redisDocumentKeyBytes,
        long redisDocuments, List<OracleSegment> oracleSegments) {
    public record OracleSegment(String name, String type, long bytes) {
    }
}
