package com.training.portal.schedule;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ScheduleRepository extends JpaRepository<Schedule, String> {
    @Modifying
    @Query("delete from Schedule schedule where schedule.scheduleId = :scheduleId and schedule.updateCount = :updateCount")
    int deleteByScheduleIdAndUpdateCount(
            @Param("scheduleId") String scheduleId,
            @Param("updateCount") Integer updateCount);
}
