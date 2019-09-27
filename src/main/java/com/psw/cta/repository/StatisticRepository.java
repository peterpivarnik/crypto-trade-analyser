package com.psw.cta.repository;

import com.psw.cta.entity.Statistic;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface StatisticRepository extends JpaRepository<Statistic, Long> {

    @Query("SELECT  MAX(s.createdAt) FROM Statistic s")
    Long findMaxCreatedAt();

}
