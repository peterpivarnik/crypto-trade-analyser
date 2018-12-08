package com.psw.cta.repository;

import com.psw.cta.entity.Crypto;
import com.psw.cta.entity.CryptoType;
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

    List<Crypto> findByCreatedAtBetween(Long startDate, Long endDate);

    @Query("SELECT AVG(c.priceToSellPercentage1h) FROM Crypto c WHERE c.cryptoType = :cryptoType AND c.createdAt > :startDate  and created_at < :endDate")
    Optional<Double> findAveragePriceToSellPercentage1h(@Param("cryptoType") CryptoType cryptoType,
                                                        @Param("startDate") Long startDate,
                                                        @Param("endDate") Long endDate);

    @Query("SELECT AVG(c.priceToSellPercentage2h) FROM Crypto c WHERE c.cryptoType = :cryptoType AND c.createdAt > :startDate  and created_at < :endDate")
    Optional<Double> findAveragePriceToSellPercentage2h(@Param("cryptoType") CryptoType cryptoType,
                                                            @Param("startDate") Long startDate,
                                                            @Param("endDate") Long endDate);

    @Query("SELECT AVG(c.priceToSellPercentage5h) FROM Crypto c WHERE c.cryptoType = :cryptoType AND c.createdAt > :startDate  and created_at < :endDate")
    Optional<Double> findAveragePriceToSellPercentage5h(@Param("cryptoType") CryptoType cryptoType,
                                                            @Param("startDate") Long startDate,
                                                            @Param("endDate") Long endDate);

    @Transactional
    @Modifying
    @Query("UPDATE Crypto c SET c.nextDayMaxPrice = :maxValue WHERE c.symbol = :symbol AND c.createdAt > :date AND c.nextDayMaxPrice < :maxValue")
    int update(@Param("maxValue") BigDecimal maxValue,
               @Param("symbol") String symbol,
               @Param("date") Long date);

    @Query("SELECT DISTINCT c.symbol FROM Crypto c WHERE c.createdAt > :date")
    List<String> findUniqueSymbols(@Param("date") Long date);

    @Query("SELECT DISTINCT c.createdAt FROM Crypto c WHERE c.createdAt > :from and c.createdAt < :to")
    List<Long> findUniqueCreatedAt(@Param("from") Long from, @Param("to") Long to);

    @Query("SELECT COUNT(*) FROM Crypto c WHERE c.cryptoType = :cryptoType AND c.createdAt > :startDate  AND c.createdAt < :endDate")
    double findAllStats(@Param("cryptoType") CryptoType cryptoType,
                        @Param("startDate") Long startDate,
                        @Param("endDate") Long endDate);

    @Query("SELECT COUNT(*) FROM Crypto c WHERE c.cryptoType = :cryptoType AND c.createdAt > :startDate  AND c.createdAt < :endDate AND c.nextDayMaxPrice >= c.priceToSell1h")
    double findValidStats1H(@Param("cryptoType") CryptoType cryptoType,
                            @Param("startDate") Long startDate,
                            @Param("endDate") Long endDate);

    @Query("SELECT COUNT(*) FROM Crypto c WHERE c.cryptoType = :cryptoType AND c.createdAt > :startDate  AND c.createdAt < :endDate AND c.nextDayMaxPrice >= c.priceToSell2h")
    double findValidStats2H(@Param("cryptoType") CryptoType cryptoType,
                            @Param("startDate") Long startDate,
                            @Param("endDate") Long endDate);

    @Query("SELECT COUNT(*) FROM Crypto c WHERE c.cryptoType = :cryptoType AND c.createdAt > :startDate  AND c.createdAt < :endDate AND c.nextDayMaxPrice >= c.priceToSell5h")
    double findValidStats5H(@Param("cryptoType") CryptoType cryptoType,
                            @Param("startDate") Long startDate,
                            @Param("endDate") Long endDate);

    List<Crypto> findByCreatedAt(Long createdAt);
}
