package com.psw.cta.entity;

import java.math.BigDecimal;

public interface CryptoResult {

    Long getCreatedAt();

    String getSymbol();

    CryptoType getCryptoType();

    BigDecimal getCurrentPrice();

    BigDecimal getPriceToSell2h();

    BigDecimal getPriceToSell5h();

    BigDecimal getPriceToSellPercentage2h();

    BigDecimal getPriceToSellPercentage5h();

    BigDecimal getWeight2h();

    BigDecimal getWeight5h();
}
