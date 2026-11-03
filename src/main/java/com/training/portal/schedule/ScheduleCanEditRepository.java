package com.training.portal.schedule;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Lock;

import jakarta.persistence.LockModeType;

public interface ScheduleCanEditRepository extends JpaRepository<ScheduleCanEdit, ScheduleCanEditId> {
    List<ScheduleCanEdit> findByIdSyainIdOrderByIdScheduleIdAsc(String syainId);


    @Query("""
            select permission from ScheduleCanEdit permission
            join fetch permission.schedule schedule
            where permission.id.scheduleId = :scheduleId
              and permission.id.syainId = :syainId
            """)
    Optional<ScheduleCanEdit> findAccessibleSchedule(
            @Param("scheduleId") String scheduleId,
            @Param("syainId") String syainId);

    @Query("""
            select permission from ScheduleCanEdit permission
            join fetch permission.member member
            where permission.id.scheduleId = :scheduleId
            order by permission.syainKbn asc, permission.id.syainId asc
            """)
    List<ScheduleCanEdit> findSchedulePermissions(@Param("scheduleId") String scheduleId);

    @Query("""
            select permission from ScheduleCanEdit permission
            join fetch permission.schedule schedule
            where permission.id.syainId = :syainId
            order by permission.pinFlg desc,
                case when schedule.startDate <= :today and schedule.endDate >= :today then 0 else 1 end,
                schedule.scheduleId asc
            """)
    List<ScheduleCanEdit> findAccessibleSchedules(
            @Param("syainId") String syainId,
            @Param("today") LocalDate today);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select permission from ScheduleCanEdit permission
            where permission.id.syainId = :syainId
            order by permission.id.scheduleId asc
            """)
    List<ScheduleCanEdit> lockUserPermissions(@Param("syainId") String syainId);

    @Query("""
            select permission from ScheduleCanEdit permission
            join fetch permission.member member
            where permission.id.scheduleId in :scheduleIds
              and member.syainKbn = 1
            order by permission.id.scheduleId asc, permission.id.syainId asc
            """)
    List<ScheduleCanEdit> findTraineePermissions(@Param("scheduleIds") Collection<String> scheduleIds);

    @Query("""
            select permission from ScheduleCanEdit permission
            join fetch permission.member member
            where permission.id.scheduleId in :scheduleIds
              and permission.canEdit = 1
            order by permission.id.scheduleId asc, permission.id.syainId asc
            """)
    List<ScheduleCanEdit> findEditablePermissions(@Param("scheduleIds") Collection<String> scheduleIds);

    @Modifying
    @Query("delete from ScheduleCanEdit permission where permission.id.scheduleId = :scheduleId")
    int deleteByScheduleId(@Param("scheduleId") String scheduleId);
}
