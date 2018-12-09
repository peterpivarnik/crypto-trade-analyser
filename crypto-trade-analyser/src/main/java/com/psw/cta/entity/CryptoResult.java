package com.psw.cta.entity;

import java.math.BigDecimal;

public interface CryptoResult {

    Long getCreatedAt();

    String getSymbol();

    CryptoType getCryptoType();

    BigDecimal getCurrentPrice();

    BigDecimal getPriceToSell();

    BigDecimal getPriceToSellPercentage();

}
