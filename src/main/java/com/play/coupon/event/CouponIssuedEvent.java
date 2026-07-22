package com.play.coupon.event;

import java.time.LocalDateTime;

public record CouponIssuedEvent(
        Long couponId,
        Long userId,
        LocalDateTime issuedAt
){}