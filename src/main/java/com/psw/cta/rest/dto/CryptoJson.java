package com.psw.cta.rest.dto;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@ToString
public class CryptoJson {

    private LocalDateTime date;
    private BigDecimal currentPrice;
    private BigDecimal priceToSell;
    private BigDecimal percentage;
}
