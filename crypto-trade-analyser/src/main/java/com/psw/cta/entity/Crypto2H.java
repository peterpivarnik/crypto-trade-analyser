package com.psw.cta.entity;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import javax.persistence.*;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;

import static javax.persistence.GenerationType.AUTO;

@Entity
@Table(schema = "public", name = "crypto_2H")
@Cacheable
@Getter
@Setter
@ToString
@EqualsAndHashCode
public class Crypto2H implements CryptoResult {

    @Id
    @NotNull
    @SequenceGenerator(name = "generator__seq_crypto_id", schema = "public", sequenceName = "seq_crypto_2H_id")
    @GeneratedValue(strategy = AUTO, generator = "generator__seq_crypto_id")
    @Column(name = "id", updatable = false, nullable = false)
    private Long id;

    @NotNull
    @Column(name = "symbol", updatable = false, nullable = false, length = 10)
    private String symbol;

    @NotNull
    @Column(name = "current_price", updatable = false, nullable = false, precision = 20, scale = 8)
    private BigDecimal currentPrice;

    @NotNull
    @Column(name = "volume", updatable = false, nullable = false, precision = 20, scale = 8)
    private BigDecimal volume;

    @NotNull
    @Column(name = "sum_diff_percent_1day", updatable = true, nullable = false, precision = 20, scale = 8)
    private BigDecimal sumDiffsPerc1Day;

    @NotNull
    @Column(name = "sum_diff_percent", updatable = false, nullable = false, precision = 20, scale = 8)
    private BigDecimal sumDiffsPerc;

    @NotNull
    @Column(name = "price_to_sell", updatable = false, nullable = false, precision = 20, scale = 8)
    private BigDecimal priceToSell;

    @NotNull
    @Column(name = "price_to_sell_percentage", updatable = false, nullable = false, precision = 20, scale = 8)
    private BigDecimal priceToSellPercentage;

    @NotNull
    @Column(name = "weight", updatable = false, nullable = false, precision = 20, scale = 8)
    private BigDecimal weight;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "crypto_type", updatable = false, nullable = false, precision = 20, scale = 8)
    private CryptoType cryptoType;

    @NotNull
    @Column(name = "created_at", updatable = false, nullable = false)
    private Long createdAt;

    @NotNull
    @Column(name = "next_day_max_price", updatable = true, nullable = false, precision = 20, scale = 8)
    private BigDecimal nextDayMaxPrice;
}
