package com.psw.cta.rest.dto;

import com.psw.cta.entity.CryptoType;
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
    private String symbol;
    private CryptoType cryptoType;
    private BigDecimal currentPrice;
    private BigDecimal priceToSell2h;
    private BigDecimal priceToSell5h;
    private BigDecimal priceToSell10h;
    private BigDecimal priceToSell24h;
    private BigDecimal priceToSellPercentage2h;
    private BigDecimal priceToSellPercentage5h;
    private BigDecimal priceToSellPercentage10h;
    private BigDecimal priceToSellPercentage24h;
    private BigDecimal weight2h;
    private BigDecimal weight5h;
    private BigDecimal weight10h;
    private BigDecimal weight24h;


}
