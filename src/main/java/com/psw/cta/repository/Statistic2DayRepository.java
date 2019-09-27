package com.psw.cta.repository;

import com.psw.cta.entity.Statistic;
import com.psw.cta.entity.Statistic2Day;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface Statistic2DayRepository extends JpaRepository<Statistic2Day, Long> {

    @Query("SELECT  MAX(s.createdAt) FROM Statistic2Day s")
    Long findMaxCreatedAt();

}
