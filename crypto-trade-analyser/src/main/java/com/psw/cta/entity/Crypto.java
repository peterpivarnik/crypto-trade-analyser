package com.psw.cta.entity;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import javax.persistence.*;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDateTime;

import static javax.persistence.GenerationType.AUTO;

@Entity
@Table(schema = "public", name = "crypto")
@Cacheable
@Getter
@Setter
@ToString
@EqualsAndHashCode
public class Crypto implements CryptoResult {

    @Id
    @NotNull
    @SequenceGenerator(name = "generator__seq_crypto_id", schema = "public", sequenceName = "seq_crypto_id")
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
    @Column(name = "sum_diff_percent_2h", updatable = false, nullable = false, precision = 20, scale = 8)
    private BigDecimal sumDiffsPerc2h;

    @NotNull
    @Column(name = "sum_diff_percent_5h", updatable = false, nullable = false, precision = 20, scale = 8)
    private BigDecimal sumDiffsPerc5h;

    @NotNull
    @Column(name = "sum_diff_percent_10h", updatable = false, nullable = false, precision = 20, scale = 8)
    private BigDecimal sumDiffsPerc10h;

    @NotNull
    @Column(name = "sum_diff_percent_24h", updatable = false, nullable = false, precision = 20, scale = 8)
    private BigDecimal sumDiffsPerc24h;

    @NotNull
    @Column(name = "price_to_sell_2h", updatable = false, nullable = false, precision = 20, scale = 8)
    private BigDecimal priceToSell2h;

    @NotNull
    @Column(name = "price_to_sell_5h", updatable = false, nullable = false, precision = 20, scale = 8)
    private BigDecimal priceToSell5h;

    @NotNull
    @Column(name = "price_to_sell_10h", updatable = false, nullable = false, precision = 20, scale = 8)
    private BigDecimal priceToSell10h;

    @NotNull
    @Column(name = "price_to_sell_24h", updatable = false, nullable = false, precision = 20, scale = 8)
    private BigDecimal priceToSell24h;

    @NotNull
    @Column(name = "price_to_sell_percentage_2h", updatable = false, nullable = false, precision = 20, scale = 8)
    private BigDecimal priceToSellPercentage2h;

    @NotNull
    @Column(name = "price_to_sell_percentage_5h", updatable = false, nullable = false, precision = 20, scale = 8)
    private BigDecimal priceToSellPercentage5h;

    @NotNull
    @Column(name = "price_to_sell_percentage_10h", updatable = false, nullable = false, precision = 20, scale = 8)
    private BigDecimal priceToSellPercentage10h;

    @NotNull
    @Column(name = "price_to_sell_percentage_24h", updatable = false, nullable = false, precision = 20, scale = 8)
    private BigDecimal priceToSellPercentage24h;

    @NotNull
    @Column(name = "weight_2h", updatable = false, nullable = false, precision = 20, scale = 8)
    private BigDecimal weight2h;

    @NotNull
    @Column(name = "weight_5h", updatable = false, nullable = false, precision = 20, scale = 8)
    private BigDecimal weight5h;

    @NotNull
    @Column(name = "weight_10h", updatable = false, nullable = false, precision = 20, scale = 8)
    private BigDecimal weight10h;

    @NotNull
    @Column(name = "weight_24h", updatable = false, nullable = false, precision = 20, scale = 8)
    private BigDecimal weight24h;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "crypto_type", updatable = false, nullable = false, precision = 20, scale = 8)
    private CryptoType cryptoType;

    @NotNull
    @Column(name = "created_at", updatable = false, nullable = false, columnDefinition = "TIMESTAMP")
    private LocalDateTime createdAt;

    @NotNull
    @Column(name = "next_day_max_price", updatable = true, nullable = false, precision = 20, scale = 8)
    private BigDecimal nextDayMaxPrice;
}
