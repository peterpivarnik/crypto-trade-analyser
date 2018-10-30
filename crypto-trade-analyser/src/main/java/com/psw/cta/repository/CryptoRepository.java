package com.psw.cta.repository;

import com.psw.cta.entity.Crypto;
import com.psw.cta.entity.CryptoType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;

@Repository
public interface CryptoRepository extends JpaRepository<Crypto, Long>, JpaSpecificationExecutor<Crypto> {

    List<Crypto> findByCreatedAtBetween(Long startDate, Long endDate);

    List<Crypto> findByCryptoType(CryptoType cryptoType);

    @Modifying
    @Query("UPDATE Crypto c SET c.nextDayMaxPrice = :maxValue WHERE c.symbol = :symbol AND c.createdAt > :date AND c.nextDayMaxPrice < :maxValue")
    void update(@Param("maxValue") BigDecimal maxValue,
                @Param("symbol") String symbol,
                @Param("date") Long date);

}
