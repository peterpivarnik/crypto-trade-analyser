package com.psw.cta.entity;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import javax.persistence.Cacheable;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.SequenceGenerator;
import javax.persistence.Table;
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
public class Crypto {

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
    @Column(name = "fifteen_minutes_max_to_current_different", updatable = false, nullable = false, precision = 20, scale = 8)
    private BigDecimal fifteenMinutesMaxToCurrentDifferent;

    @NotNull
    @Column(name = "fifteen_minutes_percentage_loss", updatable = false, nullable = false, precision = 20, scale = 8)
    private BigDecimal fifteenMinutesPercentageLoss;

    @NotNull
    @Column(name = "last_three_days_average_price", updatable = false, nullable = false, precision = 20, scale = 8)
    private BigDecimal lastThreeDaysAveragePrice;

    @NotNull
    @Column(name = "last_three_days_max_price", updatable = false, nullable = false, precision = 20, scale = 8)
    private BigDecimal lastThreeDaysMaxPrice;

    @NotNull
    @Column(name = "last_three_days_min_price", updatable = false, nullable = false, precision = 20, scale = 8)
    private BigDecimal lastThreeDaysMinPrice;

    @NotNull
    @Column(name = "last_three_days_max_min_different_percent", updatable = false, nullable = false, precision = 20, scale = 8)
    private BigDecimal lastThreeDaysMaxMinDiffPercent;

    @NotNull
    @Column(name = "volume", updatable = false, nullable = false, precision = 20, scale = 8)
    private BigDecimal volume;

    @NotNull
    @Column(name = "weight", updatable = false, nullable = false, precision = 20, scale = 8)
    private BigDecimal weight;

    @NotNull
    @Column(name = "ratio", updatable = false, nullable = false, precision = 20, scale = 8)
    private BigDecimal ratio;

    @NotNull
    @Column(name = "price_to_sell", updatable = false, nullable = false, precision = 20, scale = 8)
    private BigDecimal priceToSell;

    @NotNull
    @Column(name = "created_at", updatable = false, nullable = false, columnDefinition = "TIMESTAMP")
    private LocalDateTime createdAt;
}
