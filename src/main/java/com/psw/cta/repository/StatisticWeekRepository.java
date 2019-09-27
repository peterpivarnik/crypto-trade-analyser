package com.psw.cta.repository;

import com.psw.cta.entity.StatisticWeek;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface StatisticWeekRepository extends JpaRepository<StatisticWeek, Long> {

    @Query("SELECT  MAX(s.createdAt) FROM StatisticWeek s")
    Long findMaxCreatedAt();

}
