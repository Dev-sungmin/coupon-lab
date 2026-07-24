package com.play.coupon.consumer;

import com.play.coupon.config.KafkaConfig;
import com.play.coupon.event.CouponIssuedEvent;
import com.play.coupon.repository.CouponRepository;
import com.play.coupon.repository.IssuedCouponBatchRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class CouponIssuedConsumer {

    private final IssuedCouponBatchRepository issuedCouponBatchRepository;
    private final CouponRepository couponRepository;

    @KafkaListener(topics = KafkaConfig.COUPON_ISSUED_TOPIC)
    @Transactional
    public void consume(List<CouponIssuedEvent> events) {
        if (events.isEmpty()) return;

        // 쿠폰별로 묶어서 배치 처리
        Map<Long, List<CouponIssuedEvent>> byCoupon = events.stream()
                .collect(Collectors.groupingBy(CouponIssuedEvent::couponId));

        for (Map.Entry<Long, List<CouponIssuedEvent>> entry : byCoupon.entrySet()) {
            Long couponId = entry.getKey();
            List<CouponIssuedEvent> couponEvents = entry.getValue();

            int inserted = issuedCouponBatchRepository.bulkInsert(couponEvents);
            if (inserted > 0) {
                couponRepository.incrementIssuedQuantityBy(couponId, inserted);
            }

            if (inserted < couponEvents.size()) {
                log.warn("중복 이벤트 제외: couponId={}, 수신={}, 삽입={}",
                        couponId, couponEvents.size(), inserted);
            }
        }

        log.info("배치 처리 완료: {}건 수신", events.size());
    }
}