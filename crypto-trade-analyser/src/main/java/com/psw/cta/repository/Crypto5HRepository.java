package com.psw.cta.repository;

import com.psw.cta.entity.Crypto5H;
import com.psw.cta.entity.CryptoType;
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
public interface Crypto5HRepository extends JpaRepository<Crypto5H, Long>, JpaSpecificationExecutor<Crypto5H> {

    List<Crypto5H> findByCreatedAtBetween(Long startDate, Long endDate, Sort sort);

    @Query("SELECT AVG(c.priceToSellPercentage) FROM Crypto5H c WHERE c.cryptoType = :cryptoType AND c.createdAt > :startDate  and created_at < :endDate")
    Optional<Double> findAveragePriceToSellPercentage(@Param("cryptoType") CryptoType cryptoType,
                                                      @Param("startDate") Long startDate,
                                                      @Param("endDate") Long endDate);

    @Transactional
    @Modifying
    @Query("UPDATE Crypto5H c SET c.nextDayMaxPrice = :maxValue WHERE c.symbol = :symbol AND c.createdAt > :date c.createdAt < :to AND c.nextDayMaxPrice < :maxValue")
    int update(@Param("maxValue") BigDecimal maxValue,
               @Param("symbol") String symbol,
               @Param("date") Long date,
               @Param("to") Long to);

    @Query("SELECT DISTINCT c.symbol FROM Crypto5H c WHERE c.createdAt > :date")
    List<String> findUniqueSymbols(@Param("date") Long date);

    @Query("SELECT DISTINCT c.createdAt FROM Crypto5H c WHERE c.createdAt > :from and c.createdAt < :to")
    List<Long> findUniqueCreatedAt(@Param("from") Long from, @Param("to") Long to);

    @Query("SELECT COUNT(*) FROM Crypto5H c WHERE c.cryptoType = :cryptoType AND c.createdAt > :startDate  AND c.createdAt < :endDate")
    double findAllStats(@Param("cryptoType") CryptoType cryptoType,
                        @Param("startDate") Long startDate,
                        @Param("endDate") Long endDate);

    @Query("SELECT COUNT(*) FROM Crypto5H c WHERE c.cryptoType = :cryptoType AND c.createdAt > :startDate  AND c.createdAt < :endDate AND c.nextDayMaxPrice >= c.priceToSell")
    double findValidStats5H(@Param("cryptoType") CryptoType cryptoType,
                            @Param("startDate") Long startDate,
                            @Param("endDate") Long endDate);

    List<Crypto5H> findByCreatedAt(Long createdAt);
}
