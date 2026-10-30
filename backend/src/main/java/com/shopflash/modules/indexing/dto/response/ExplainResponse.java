package com.shopflash.modules.indexing.dto.response;

import java.util.List;

/** Kế hoạch thực thi (Oracle DBMS_XPLAN) hoặc cây truy vấn + profile (Redis FT.EXPLAIN, FT.PROFILE). */
public record ExplainResponse(String source, String variant, String statement, List<String> lines) {
}
