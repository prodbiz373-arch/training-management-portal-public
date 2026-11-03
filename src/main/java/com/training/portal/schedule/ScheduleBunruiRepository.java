package com.training.portal.schedule;

import java.util.List;
import java.util.Collection;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ScheduleBunruiRepository extends JpaRepository<ScheduleBunrui, ScheduleBunruiId> {
    List<ScheduleBunrui> findByIdScheduleIdOrderByIdBnruiNoAsc(String scheduleId);

    @Query("""
            select category from ScheduleBunrui category
            where category.id.scheduleId in :scheduleIds
            order by category.id.scheduleId asc, category.id.bnruiNo asc
            """)
    List<ScheduleBunrui> findForSchedules(@Param("scheduleIds") Collection<String> scheduleIds);

    @Modifying
    @Query("delete from ScheduleBunrui category where category.id.scheduleId = :scheduleId")
    int deleteByScheduleId(@Param("scheduleId") String scheduleId);
}
