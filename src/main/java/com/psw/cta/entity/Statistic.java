package com.psw.cta.entity;

import javax.persistence.*;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDateTime;

import static javax.persistence.GenerationType.AUTO;

@Entity
@Table(schema = "public", name = "statistic")
@Cacheable
public class Statistic {

    @Id
    @NotNull
    @SequenceGenerator(name = "generator__seq_statistic_id", schema = "public", sequenceName = "seq_statistic_id")
    @GeneratedValue(strategy = AUTO, generator = "generator__seq_statistic_id")
    @Column(name = "id", updatable = false, nullable = false)
    private Long id;

    @NotNull
    @Column(name = "created_at", updatable = false, nullable = false)
    private Long createdAt;

    @NotNull
    @Column(name = "created_at_date", updatable = false, nullable = false)
    private LocalDateTime createdAtDate;

    @NotNull
    @Column(name = "success_rate", updatable = false, nullable = false, precision = 20, scale = 8)
    private BigDecimal successRate;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

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

    public BigDecimal getSuccessRate() {
        return successRate;
    }

    public void setSuccessRate(BigDecimal successRate) {
        this.successRate = successRate;
    }
}
