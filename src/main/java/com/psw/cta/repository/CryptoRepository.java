package com.psw.cta.repository;

import com.psw.cta.entity.Crypto;
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
public interface CryptoRepository extends JpaRepository<Crypto, Long>, JpaSpecificationExecutor<Crypto> {

    List<Crypto> findByCreatedAtBetween(Long startDate, Long endDate, Sort sort);

    @Query("SELECT AVG(c.priceToSellPercentage) FROM Crypto c WHERE c.createdAt > :startDate  and c.createdAt < :endDate")
    Optional<Double> findAveragePriceToSellPercentage(@Param("startDate") Long startDate,
                                                      @Param("endDate") Long endDate);

    @Transactional
    @Modifying
    @Query("UPDATE Crypto c SET c.nextDayMaxPrice = :maxValue WHERE c.symbol = :symbol AND c.createdAt > :from AND c.createdAt < :to AND c.nextDayMaxPrice < :maxValue")
    int updateNextDayMaxPrice(@Param("maxValue") BigDecimal maxValue,
                              @Param("symbol") String symbol,
                              @Param("from") Long from,
                              @Param("to") Long to);

    @Transactional
    @Modifying
    @Query("UPDATE Crypto c SET c.next2DayMaxPrice = :maxValue WHERE c.symbol = :symbol AND c.createdAt > :from AND c.createdAt < :to AND c.next2DayMaxPrice < :maxValue")
    int updateNext2DayMaxPrice(@Param("maxValue") BigDecimal maxValue,
                              @Param("symbol") String symbol,
                              @Param("from") Long from,
                              @Param("to") Long to);

    @Transactional
    @Modifying
    @Query("UPDATE Crypto c SET c.nextWeekMaxPrice = :maxValue WHERE c.symbol = :symbol AND c.createdAt > :from AND c.createdAt < :to AND c.nextWeekMaxPrice < :maxValue")
    int updateNextWeekMaxPrice(@Param("maxValue") BigDecimal maxValue,
                              @Param("symbol") String symbol,
                              @Param("from") Long from,
                              @Param("to") Long to);

    @Query("SELECT DISTINCT c.symbol FROM Crypto c WHERE c.createdAt > :date")
    List<String> findUniqueSymbols(@Param("date") Long date);

    @Query("SELECT DISTINCT c.createdAt FROM Crypto c WHERE c.createdAt > :from and c.createdAt < :to")
    List<Long> findUniqueCreatedAt(@Param("from") Long from, @Param("to") Long to);

    @Query("SELECT COUNT(*) FROM Crypto c WHERE c.createdAt > :startDate  AND c.createdAt < :endDate")
    double findAllStats(@Param("startDate") Long startDate,
                        @Param("endDate") Long endDate);

    @Query("SELECT COUNT(*) FROM Crypto c WHERE c.createdAt > :startDate  AND c.createdAt < :endDate AND c.nextDayMaxPrice >= c.priceToSell")
    double findValidStats1H(@Param("startDate") Long startDate,
                            @Param("endDate") Long endDate);

    List<Crypto> findByCreatedAt(Long createdAt);
}
