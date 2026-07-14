import http from 'k6/http';
import { check } from 'k6';
import { Counter } from 'k6/metrics';

const issued = new Counter('coupon_issued');
const rejected = new Counter('coupon_rejected');

export const options = {
    scenarios: {
        rush: {
            executor: 'per-vu-iterations',
            vus: 500,
            iterations: 1,
            maxDuration: '1m',
        },
    },
};

const BASE = 'http://host.docker.internal:8080';
const QUANTITY = 100;

export function setup() {
    const res = http.post(
        `${BASE}/api/coupons`,
        JSON.stringify({ name: `run-${Date.now()}`, totalQuantity: QUANTITY }),
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
    const res = http.post(
        `${BASE}/api/coupons/${data.couponId}/issue?userId=${__VU}`,
        null,
    );
    if (res.status === 200) issued.add(1);
    else rejected.add(1);
    check(res, { 'not 5xx': (r) => r.status < 500 });
}