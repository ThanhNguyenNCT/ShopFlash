package com.shopflash.modules.transaction.security;

import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** Chỉ tra danh tính từ email đã gắn với token phía server; không nhận userId từ client. */
@Repository
public class TransactionCustomerRepository {
    private final NamedParameterJdbcTemplate jdbc;

    public TransactionCustomerRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public TransactionCustomer findActiveCustomer(String email) {
        List<TransactionCustomer> rows = jdbc.query("""
                SELECT user_id, email, role FROM users
                WHERE email = :email AND role = 'CUSTOMER' AND status = 'ACTIVE'
                """, Map.of("email", email),
                (rs, row) -> new TransactionCustomer(rs.getLong("user_id"), rs.getString("email"), rs.getString("role")));
        return rows.isEmpty() ? null : rows.getFirst();
    }
}
