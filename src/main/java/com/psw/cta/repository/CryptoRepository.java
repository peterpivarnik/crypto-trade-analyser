package com.psw.cta.repository;

import com.psw.cta.entity.Crypto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface CryptoRepository extends JpaRepository<Crypto, Long>, JpaSpecificationExecutor<Crypto> {
}
