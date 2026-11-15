package com.shopflash.modules.concurrency.repository.oracle;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import java.util.Optional;

import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import com.shopflash.modules.concurrency.lab.AttemptOutcome;
import com.shopflash.modules.concurrency.lab.LabErrorCodes;
import com.shopflash.modules.concurrency.lab.LabStrategy;

/**
 * Bộ đếm Oracle của Concurrency Lab: một dòng trong cc_lab_items. Mỗi phương thức mua là một chiến lược, một
 * lượt mua 1 suất. Các chiến lược nhiều câu lệnh (NAIVE, PESSIMISTIC, NOWAIT, SERIALIZABLE) chạy trong một
 * transaction; OPTIMISTIC và ATOMIC dùng auto-commit, mỗi câu lệnh tự commit.
 */
@Repository
public class LabOracleCounter {

    public static final long ITEM_ID = 1;

    private static final int ORA_SERIALIZE = 8177;

    private static final String SELECT_PLAIN =
            "SELECT allocated, sold, version FROM cc_lab_items WHERE lab_item_id = :id";
    private static final String SELECT_FOR_UPDATE =
            "SELECT allocated, sold, version FROM cc_lab_items WHERE lab_item_id = :id FOR UPDATE";
    private static final String SELECT_FOR_UPDATE_NOWAIT =
            "SELECT allocated, sold, version FROM cc_lab_items WHERE lab_item_id = :id FOR UPDATE NOWAIT";

    public record Row(long allocated, long sold, long version) {
    }

    private final NamedParameterJdbcTemplate jdbc;
    private final TransactionTemplate readCommitted;
    private final TransactionTemplate serializable;

    public LabOracleCounter(NamedParameterJdbcTemplate jdbc, PlatformTransactionManager transactions) {
        this.jdbc = jdbc;
        this.readCommitted = new TransactionTemplate(transactions);
        this.readCommitted.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        this.serializable = new TransactionTemplate(transactions);
        this.serializable.setIsolationLevel(TransactionDefinition.ISOLATION_SERIALIZABLE);
    }

    /** Tạo hoặc đặt lại dòng bộ đếm: allocated = quota, sold = 0. */
    public void reset(int quota) {
        jdbc.update("""
                MERGE INTO cc_lab_items t
                USING (SELECT :id AS lab_item_id FROM dual) s
                ON (t.lab_item_id = s.lab_item_id)
                WHEN MATCHED THEN UPDATE SET t.allocated = :quota, t.sold = 0, t.version = 0,
                     t.updated_at = SYSTIMESTAMP
                WHEN NOT MATCHED THEN INSERT (lab_item_id, allocated, sold, version)
                     VALUES (:id, :quota, 0, 0)
                """, Map.of("id", ITEM_ID, "quota", quota));
    }

    public Optional<Row> state() {
        return jdbc.query(SELECT_PLAIN, Map.of("id", ITEM_ID), LabOracleCounter::row).stream().findFirst();
    }

    /** Một lượt mua theo chiến lược Oracle. */
    public AttemptOutcome buy(LabStrategy strategy) {
        return switch (strategy) {
            case ORACLE_NAIVE -> readCommitted.execute(s -> checkThenWrite(SELECT_PLAIN));
            case ORACLE_PESSIMISTIC -> readCommitted.execute(s -> checkThenWrite(SELECT_FOR_UPDATE));
            case ORACLE_NOWAIT -> readCommitted.execute(s -> checkThenWrite(SELECT_FOR_UPDATE_NOWAIT));
            case ORACLE_OPTIMISTIC -> optimistic();
            case ORACLE_ATOMIC -> atomic();
            case ORACLE_SERIALIZABLE -> serializableWithRetry();
            default -> throw new IllegalArgumentException("Không phải chiến lược Oracle: " + strategy);
        };
    }

    /**
     * Đọc sold, kiểm tra ở Java, rồi ghi giá trị đã tính sẵn. Không khóa thì hai lượt cùng đọc một giá trị và
     * ghi đè nhau (mất cập nhật). selectSql là một trong ba câu SELECT hằng ở trên.
     */
    private AttemptOutcome checkThenWrite(String selectSql) {
        Row row = read(selectSql);
        if (row.sold() >= row.allocated()) {
            return AttemptOutcome.soldOut(0);
        }
        jdbc.update("""
                UPDATE cc_lab_items SET sold = :sold, version = version + 1, updated_at = SYSTIMESTAMP
                WHERE lab_item_id = :id
                """, Map.of("sold", row.sold() + 1, "id", ITEM_ID));
        return AttemptOutcome.success(0);
    }

    /** Đọc version, ghi kèm điều kiện version không đổi; 0 dòng thì có lượt khác đã ghi trước, thử lại. */
    private AttemptOutcome optimistic() {
        for (int retries = 0; retries <= LabStrategy.MAX_RETRIES; retries++) {
            Row row = read(SELECT_PLAIN);
            if (row.sold() >= row.allocated()) {
                return AttemptOutcome.soldOut(retries);
            }
            int updated = jdbc.update("""
                    UPDATE cc_lab_items SET sold = :sold, version = :version + 1, updated_at = SYSTIMESTAMP
                    WHERE lab_item_id = :id AND version = :version
                    """, Map.of("sold", row.sold() + 1, "version", row.version(), "id", ITEM_ID));
            if (updated == 1) {
                return AttemptOutcome.success(retries);
            }
        }
        return AttemptOutcome.error("RETRY_EXHAUSTED", LabStrategy.MAX_RETRIES);
    }

    /** Kiểm tra và ghi trong một câu UPDATE: Oracle khóa dòng rồi đánh giá lại điều kiện trên bản mới nhất. */
    private AttemptOutcome atomic() {
        int updated = jdbc.update("""
                UPDATE cc_lab_items SET sold = sold + 1, version = version + 1, updated_at = SYSTIMESTAMP
                WHERE lab_item_id = :id AND sold + 1 <= allocated
                """, Map.of("id", ITEM_ID));
        return updated == 1 ? AttemptOutcome.success(0) : AttemptOutcome.soldOut(0);
    }

    /** Như NAIVE nhưng ở mức SERIALIZABLE: ghi đè lên bản đã đổi sau khi transaction bắt đầu thì ORA-08177. */
    private AttemptOutcome serializableWithRetry() {
        for (int retries = 0; retries <= LabStrategy.MAX_RETRIES; retries++) {
            try {
                AttemptOutcome outcome = serializable.execute(s -> checkThenWrite(SELECT_PLAIN));
                return new AttemptOutcome(outcome.result(), retries, outcome.errorCode());
            } catch (DataAccessException e) {
                if (LabErrorCodes.oracleCode(e) != ORA_SERIALIZE) {
                    throw e;
                }
            }
        }
        return AttemptOutcome.error("ORA-08177", LabStrategy.MAX_RETRIES);
    }

    private Row read(String selectSql) {
        return jdbc.queryForObject(selectSql, Map.of("id", ITEM_ID), LabOracleCounter::row);
    }

    private static Row row(ResultSet rs, int rowNum) throws SQLException {
        return new Row(rs.getLong("allocated"), rs.getLong("sold"), rs.getLong("version"));
    }
}
