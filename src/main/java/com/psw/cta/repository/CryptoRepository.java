package com.psw.cta.repository;

import com.psw.cta.entity.Crypto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface CryptoRepository extends JpaRepository<Crypto, Long>, JpaSpecificationExecutor<Crypto> {

    @Query("SELECT c from Crypto c where c.createdAt > :dateTime")
    List<Crypto> findNewest(@Param("dateTime") LocalDateTime dateTime);
}
