package com.play.coupon.repository;

import com.play.coupon.event.CouponIssuedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class IssuedCouponBatchRepository {

    private final JdbcTemplate jdbcTemplate;

    /** 다중 VALUES 한 문장으로 삽입하고, 실제 삽입된 행 수를 반환 (중복은 IGNORE) */
    public int bulkInsert(List<CouponIssuedEvent> events) {
        if (events.isEmpty()) return 0;

        StringBuilder sql = new StringBuilder(
                "INSERT IGNORE INTO issued_coupon (coupon_id, user_id, issued_at) VALUES ");
        List<Object> params = new ArrayList<>(events.size() * 3);

        for (int i = 0; i < events.size(); i++) {
            if (i > 0) sql.append(", ");
            sql.append("(?, ?, ?)");

            CouponIssuedEvent e = events.get(i);
            params.add(e.couponId());
            params.add(e.userId());
            params.add(Timestamp.valueOf(e.issuedAt()));
        }

        return jdbcTemplate.update(sql.toString(), params.toArray());
    }
}