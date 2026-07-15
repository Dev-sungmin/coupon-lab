package com.play.coupon.service;

import com.play.coupon.domain.Coupon;
import com.play.coupon.domain.IssuedCoupon;
import com.play.coupon.repository.CouponRepository;
import com.play.coupon.repository.IssuedCouponRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CouponIssueService {
    private final CouponRepository couponRepository;
    private final IssuedCouponRepository issuedCouponRepository;

    @Transactional
    public Long create(String name, int totalQuantity) {
        Coupon coupon = couponRepository.save(new Coupon(name, totalQuantity));
        return coupon.getId();
    }

    @Transactional
    public void issue(Long couponId, Long userId){
        Coupon coupon = couponRepository.findByIdWithLock(couponId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 쿠폰입니다."));

        coupon.issue();
        issuedCouponRepository.save(new IssuedCoupon(couponId, userId));
    }

}