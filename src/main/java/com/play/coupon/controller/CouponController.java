package com.play.coupon.controller;

import com.play.coupon.service.CouponIssueService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/coupons")
@RequiredArgsConstructor
public class CouponController{
    private final CouponIssueService couponIssueService;

    @PostMapping
    public ResponseEntity<Long> create(@RequestBody CouponCreateRequest request){
        Long id = couponIssueService.create(request.name(), request.totalQuantity());
        return ResponseEntity.ok(id);
    }

    @PostMapping("/{couponId}/issue")
    public ResponseEntity<Void> issue(@PathVariable Long couponId, @RequestParam Long userId){
        couponIssueService.issue(couponId, userId);
        return ResponseEntity.ok().build();
    }
}
