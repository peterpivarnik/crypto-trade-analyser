package com.psw.cta.repository;

import com.psw.cta.entity.Crypto;
import com.psw.cta.entity.CryptoType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface CryptoRepository extends JpaRepository<Crypto, Long>, JpaSpecificationExecutor<Crypto> {

    @Query("SELECT c FROM Crypto c WHERE c.createdAt > :dateTime")
    List<Crypto> findLastDayCryptos(@Param("dateTime") LocalDateTime dateTime);

    @Query("SELECT c FROM Crypto c ORDER BY c.createdAt DESC")
    List<Crypto> findOrderedCryptos();

    @Query("SELECT c FROM Crypto c WHERE c.createdAt = :dateTime")
    List<Crypto> findLastCryptos(@Param("dateTime") LocalDateTime dateTime);

    List<Crypto> findByCreatedAtBetween(LocalDateTime startDate, LocalDateTime endDate);

    List<Crypto> findByCryptoType(CryptoType cryptoType);

    @Query("UPDATE Crypto c SET c.nextDayMaxPrice = :maxValue WHERE c.symbol = :symbol AND c.createdAt > :date AND c.nextDayMaxPrice < :maxValue")
    void update(@Param("maxValue") BigDecimal maxValue,
                @Param("symbol") String symbol,
                @Param("date") LocalDateTime date);

}
