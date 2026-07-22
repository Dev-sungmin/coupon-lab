package com.play.coupon.service;

import com.play.coupon.config.KafkaConfig;
import com.play.coupon.domain.Coupon;
import com.play.coupon.event.CouponIssuedEvent;
import com.play.coupon.repository.CouponRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class CouponIssueService {
    private final CouponRepository couponRepository;
    private final RedisTemplate<String, String> redisTemplate;
    private final RedisScript<Long> couponIssueScript;
    private final KafkaTemplate<String, CouponIssuedEvent> kafkaTemplate;

    @Transactional
    public Long create(String name, int totalQuantity) {
        Coupon coupon = couponRepository.save(new Coupon(name, totalQuantity));
        redisTemplate.opsForValue().set("coupon:" + coupon.getId() + ":total", String.valueOf(totalQuantity));
        return coupon.getId();
    }

    @Transactional
    public void issue(Long couponId, Long userId){
        List<String> keys = List.of(
                "coupon:" + couponId + ":count",
                "coupon:" + couponId + ":total"
        );
        Long result = redisTemplate.execute(couponIssueScript, keys);

        if(result.equals(-2L)){
            throw new IllegalArgumentException("존재하지 않는 쿠폰입니다.");
        }
        if(result.equals(-1L)){
            throw new IllegalStateException("쿠폰이 모두 소진되었습니다.");
        }

        CouponIssuedEvent event = new CouponIssuedEvent(couponId, userId, LocalDateTime.now());
        try{
            kafkaTemplate.send(KafkaConfig.COUPON_ISSUED_TOPIC, String.valueOf(couponId), event)
                    .get(3, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.error("Kafka 발급 이벤트 전송 실패: couponId={}, userId={}", couponId, userId, e);
            throw new IllegalStateException("발급 처리에 실패했습니다.", e);
        }
    }
}