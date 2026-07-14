package com.play.coupon.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class IssuedCoupon{
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long couponId;
    private Long userId;
    private LocalDateTime issuedAt;

    public IssuedCoupon(Long couponId, Long userId){
        this.couponId = couponId;
        this.userId = userId;
        this.issuedAt = LocalDateTime.now();
    }
}