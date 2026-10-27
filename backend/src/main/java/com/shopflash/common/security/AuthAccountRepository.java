package com.shopflash.common.security;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Repository;

@Repository
public class AuthAccountRepository {
    private final NamedParameterJdbcTemplate jdbc;

    public AuthAccountRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<AuthAccount> findById(long userId) {
        return find("SELECT user_id, email, password_hash, full_name, phone, role, status "
                + "FROM users WHERE user_id = :value", userId);
    }

    public Optional<AuthAccount> findByEmail(String email) {
        return find("SELECT user_id, email, password_hash, full_name, phone, role, status "
                + "FROM users WHERE email = :value", email);
    }

    public void insertCustomer(String email, String passwordHash, String fullName, String phone) {
        jdbc.update("""
                INSERT INTO users (email, password_hash, full_name, phone, role, status, created_at, updated_at)
                VALUES (:email, :hash, :name, :phone, 'CUSTOMER', 'ACTIVE', SYSTIMESTAMP, SYSTIMESTAMP)
                """, new MapSqlParameterSource()
                .addValue("email", email)
                .addValue("hash", passwordHash)
                .addValue("name", fullName)
                .addValue("phone", phone));
    }

    private Optional<AuthAccount> find(String sql, Object value) {
        List<AuthAccount> rows = jdbc.query(sql, Map.of("value", value), (rs, row) ->
                new AuthAccount(rs.getLong("user_id"), rs.getString("email"), rs.getString("password_hash"),
                        rs.getString("full_name"), rs.getString("phone"),
                        rs.getString("role"), rs.getString("status")));
        return rows.stream().findFirst();
    }
}
