package com.play.coupon.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Coupon{

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String name;
    private int totalQuantity;
    private int issuedQuantity;

    public Coupon(String name, int totalQuantity){
        this.name = name;
        this.totalQuantity = totalQuantity;
        this.issuedQuantity = 0;
    }

    public boolean isSoldOut() {
        return issuedQuantity >= totalQuantity;
    }

    public void issue(){
        if(isSoldOut()){
            throw new IllegalStateException("쿠폰이 모두 소진되었습니다.");
        }
        this.issuedQuantity++;
    }
}
