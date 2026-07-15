// 지속 부하 시나리오 — 시스템 거동 관측용 (커넥션 풀, TPS 천장, 락 대기 비용)
// rush(issue-coupon.js)와 역할 분리: rush = 정합성 검증, sustained = 부하 특성 관측
import http from 'k6/http';
import { check } from 'k6';
import { Counter } from 'k6/metrics';

const issued = new Counter('coupon_issued');
const rejected = new Counter('coupon_rejected');

export const options = {
    scenarios: {
        sustained: {
            executor: 'constant-vus',
            vus: 200,
            duration: '30s',
        },
    },
};

const BASE = 'http://host.docker.internal:8080';
const QUANTITY = 100000; // 매진 조기종료 방지 — 전 요청이 풀코스 트랜잭션을 타도록

export function setup() {
    const res = http.post(
        `${BASE}/api/coupons`,
        JSON.stringify({ name: `sustained-${Date.now()}`, totalQuantity: QUANTITY }),
        { headers: { 'Content-Type': 'application/json' } },
    );
    if (res.status !== 200) {
        throw new Error(`쿠폰 생성 실패: ${res.status} ${res.body}`);
    }
    const couponId = res.body.trim();
    console.log(`>>> coupon created: id=${couponId}, quantity=${QUANTITY}`);
    return { couponId };
}

export default function (data) {
    // VU가 반복 요청하므로 (VU번호, 반복번호) 조합으로 userId 유니크 보장
    const userId = __VU * 1000000 + __ITER;
    const res = http.post(
        `${BASE}/api/coupons/${data.couponId}/issue?userId=${userId}`,
        null,
    );
    if (res.status === 200) issued.add(1);
    else rejected.add(1);
    check(res, { 'not 5xx': (r) => r.status < 500 });
}