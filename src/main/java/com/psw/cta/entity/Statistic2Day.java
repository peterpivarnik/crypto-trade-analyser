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
@Table(schema = "public", name = "statistic_2day")
@Cacheable
@Getter
@Setter
@ToString
@EqualsAndHashCode
public class Statistic2Day {

    @Id
    @NotNull
    @SequenceGenerator(name = "generator__seq_statistic_2day_id",
                       schema = "public",
                       sequenceName = "seq_statistic_2day_id")
    @GeneratedValue(strategy = AUTO, generator = "generator__seq_statistic_2day_id")
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

}
