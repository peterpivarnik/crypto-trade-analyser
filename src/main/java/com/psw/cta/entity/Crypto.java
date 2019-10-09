package com.psw.cta.entity;

import javax.persistence.*;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDateTime;

import static javax.persistence.GenerationType.AUTO;

@Entity
@Table(schema = "public", name = "crypto")
@Cacheable
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
    @Column(name = "sum_diff_percent", updatable = false, nullable = false, precision = 20, scale = 8)
    private BigDecimal sumDiffsPerc;

    @NotNull
    @Column(name = "sum_diff_percent10h", updatable = false, nullable = false, precision = 20, scale = 8)
    private BigDecimal sumDiffsPerc10h;

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
    @Column(name = "created_at", updatable = false, nullable = false)
    private Long createdAt;

    @NotNull
    @Column(name = "created_at_date", updatable = false, nullable = false)
    private LocalDateTime createdAtDate;

    @NotNull
    @Column(name = "next_day_max_price", nullable = false, precision = 20, scale = 8)
    private BigDecimal nextDayMaxPrice;

    @NotNull
    @Column(name = "next_2day_max_price", nullable = false, precision = 20, scale = 8)
    private BigDecimal next2DayMaxPrice;

    @NotNull
    @Column(name = "next_week_max_price", nullable = false, precision = 20, scale = 8)
    private BigDecimal nextWeekMaxPrice;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    @Override
    public String getSymbol() {
        return symbol;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    @Override
    public BigDecimal getCurrentPrice() {
        return currentPrice;
    }

    public void setCurrentPrice(BigDecimal currentPrice) {
        this.currentPrice = currentPrice;
    }

    public BigDecimal getVolume() {
        return volume;
    }

    public void setVolume(BigDecimal volume) {
        this.volume = volume;
    }

    public BigDecimal getSumDiffsPerc() {
        return sumDiffsPerc;
    }

    public void setSumDiffsPerc(BigDecimal sumDiffsPerc) {
        this.sumDiffsPerc = sumDiffsPerc;
    }

    public BigDecimal getSumDiffsPerc10h() {
        return sumDiffsPerc10h;
    }

    public void setSumDiffsPerc10h(BigDecimal sumDiffsPerc10h) {
        this.sumDiffsPerc10h = sumDiffsPerc10h;
    }

    @Override
    public BigDecimal getPriceToSell() {
        return priceToSell;
    }

    public void setPriceToSell(BigDecimal priceToSell) {
        this.priceToSell = priceToSell;
    }

    @Override
    public BigDecimal getPriceToSellPercentage() {
        return priceToSellPercentage;
    }

    public void setPriceToSellPercentage(BigDecimal priceToSellPercentage) {
        this.priceToSellPercentage = priceToSellPercentage;
    }

    public BigDecimal getWeight() {
        return weight;
    }

    public void setWeight(BigDecimal weight) {
        this.weight = weight;
    }

    @Override
    public Long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Long createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getCreatedAtDate() {
        return createdAtDate;
    }

    public void setCreatedAtDate(LocalDateTime createdAtDate) {
        this.createdAtDate = createdAtDate;
    }

    public BigDecimal getNextDayMaxPrice() {
        return nextDayMaxPrice;
    }

    public void setNextDayMaxPrice(BigDecimal nextDayMaxPrice) {
        this.nextDayMaxPrice = nextDayMaxPrice;
    }

    public BigDecimal getNext2DayMaxPrice() {
        return next2DayMaxPrice;
    }

    public void setNext2DayMaxPrice(BigDecimal next2DayMaxPrice) {
        this.next2DayMaxPrice = next2DayMaxPrice;
    }

    public BigDecimal getNextWeekMaxPrice() {
        return nextWeekMaxPrice;
    }

    public void setNextWeekMaxPrice(BigDecimal nextWeekMaxPrice) {
        this.nextWeekMaxPrice = nextWeekMaxPrice;
    }
}
