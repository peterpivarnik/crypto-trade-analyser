package com.psw.cta.repository;

import com.psw.cta.entity.Crypto2H;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Repository
public interface Crypto2HRepository extends JpaRepository<Crypto2H, Long>, JpaSpecificationExecutor<Crypto2H> {

    List<Crypto2H> findByCreatedAtBetween(Long startDate, Long endDate, Sort sort);

    @Query("SELECT AVG(c.priceToSellPercentage) FROM Crypto2H c WHERE c.createdAt > :startDate  and c.createdAt < :endDate")
    Optional<Double> findAveragePriceToSellPercentage(@Param("startDate") Long startDate,
                                                      @Param("endDate") Long endDate);

    @Transactional
    @Modifying
    @Query("UPDATE Crypto2H c SET c.nextDayMaxPrice = :maxValue WHERE c.symbol = :symbol AND c.createdAt > :date AND c.createdAt < :to AND c.nextDayMaxPrice < :maxValue")
    int update(@Param("maxValue") BigDecimal maxValue,
               @Param("symbol") String symbol,
               @Param("date") Long date,
               @Param("to") Long to);

    @Query("SELECT DISTINCT c.symbol FROM Crypto2H c WHERE c.createdAt > :date")
    List<String> findUniqueSymbols(@Param("date") Long date);

    @Query("SELECT DISTINCT c.createdAt FROM Crypto2H c WHERE c.createdAt > :from and c.createdAt < :to")
    List<Long> findUniqueCreatedAt(@Param("from") Long from, @Param("to") Long to);

    @Query("SELECT COUNT(*) FROM Crypto2H c WHERE c.createdAt > :startDate  AND c.createdAt < :endDate")
    double findAllStats(@Param("startDate") Long startDate,
                        @Param("endDate") Long endDate);

    @Query("SELECT COUNT(*) FROM Crypto2H c WHERE c.createdAt > :startDate  AND c.createdAt < :endDate AND c.nextDayMaxPrice >= c.priceToSell")
    double findValidStats2H(@Param("startDate") Long startDate,
                            @Param("endDate") Long endDate);

    List<Crypto2H> findByCreatedAt(Long createdAt);
}
