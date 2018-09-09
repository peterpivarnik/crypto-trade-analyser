package com.psw.cta.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public interface CryptoResult {

    LocalDateTime getDate();

    String getSymbol();

    CryptoType getCryptoType();

    BigDecimal getCurrentPrice();

    BigDecimal getPriceToSell2h();

    BigDecimal getPriceToSell5h();

    BigDecimal getPriceToSell10h();

    BigDecimal getPriceToSell24h();

    BigDecimal getPriceToSellPercentage2h();

    BigDecimal getPriceToSellPercentage5h();

    BigDecimal getPriceToSellPercentage10h();

    BigDecimal getPriceToSellPercentage24h();

    BigDecimal getWeight2h();

    BigDecimal getWeight5h();

    BigDecimal getWeight10h();

    BigDecimal getWeight24h();
}
