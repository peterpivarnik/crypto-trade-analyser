package com.psw.cta.repository;

import com.psw.cta.entity.Statistic;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface StatisticRepository extends JpaRepository<Statistic, Long>, JpaSpecificationExecutor<Statistic> {

    @Query("SELECT  MAX(s.createdAt) FROM Statistic s")
    Long findMaxCreatedAt();

}
