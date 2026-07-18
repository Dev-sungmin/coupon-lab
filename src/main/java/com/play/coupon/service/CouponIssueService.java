package com.play.coupon.service;

import com.play.coupon.domain.Coupon;
import com.play.coupon.domain.IssuedCoupon;
import com.play.coupon.repository.CouponRepository;
import com.play.coupon.repository.IssuedCouponRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;

@Service
@RequiredArgsConstructor
public class CouponIssueService {
    private final CouponRepository couponRepository;
    private final IssuedCouponRepository issuedCouponRepository;
    private final RedisTemplate<String, String> redisTemplate;
    private final RedisScript<Long> couponIssueScript;

    @Transactional
    public Long create(String name, int totalQuantity) {
        Coupon coupon = couponRepository.save(new Coupon(name, totalQuantity));
        return coupon.getId();
    }

    @Transactional
    public void issue(Long couponId, Long userId){
        Coupon coupon = couponRepository.findById(couponId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 쿠폰입니다."));

        String key = "coupon:" + couponId + ":count";
        Long result = redisTemplate.execute(
                couponIssueScript,
                Collections.singletonList(key),
                String.valueOf(coupon.getTotalQuantity())
        );

        if (result.equals(-1L)) {
            throw new IllegalStateException("쿠폰이 모두 소진되었습니다.");
        }

        issuedCouponRepository.save(new IssuedCoupon(couponId, userId));
        couponRepository.incrementIssuedQuantity(couponId);
    }
}