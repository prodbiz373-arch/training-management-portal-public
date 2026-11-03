package com.training.portal.schedule;

import java.util.List;
import java.util.Collection;
import java.time.LocalDate;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ScheduleDetailRepository extends JpaRepository<ScheduleDetail, ScheduleDetailId> {
    @Query("""
            select detail from ScheduleDetail detail
            join fetch detail.bunrui bunrui
            join fetch detail.instructor instructor
            where detail.id.scheduleId = :scheduleId
            order by detail.scheduleDate asc, detail.id.scheduleDateNo asc
            """)
    List<ScheduleDetail> findScheduleDetails(@Param("scheduleId") String scheduleId);

    @Query("""
            select detail from ScheduleDetail detail
            join fetch detail.bunrui bunrui
            join fetch detail.instructor instructor
            where detail.id.scheduleId in :scheduleIds
            order by detail.id.scheduleId asc,
                case when detail.scheduleDate = :today then 0 else 1 end,
                detail.scheduleDate asc,
                detail.id.scheduleDateNo asc
            """)
    List<ScheduleDetail> findHomeDetails(
            @Param("scheduleIds") Collection<String> scheduleIds,
            @Param("today") LocalDate today);

    @Query("""
            select detail from ScheduleDetail detail
            join fetch detail.bunrui bunrui
            join fetch detail.instructor instructor
            where detail.id.scheduleId in :scheduleIds
              and detail.taskFlg = 1
            order by detail.scheduleDate asc, detail.id.scheduleId asc, detail.id.scheduleDateNo asc
            """)
    List<ScheduleDetail> findProgressTasks(@Param("scheduleIds") Collection<String> scheduleIds);

    @Query(value = """
            select count(*) > 0
            from issue_submission_statuses
            where schedule_id = :scheduleId and schedule_date_no = :scheduleDateNo
            """, nativeQuery = true)
    boolean hasIssueSubmissions(
            @Param("scheduleId") String scheduleId,
            @Param("scheduleDateNo") Integer scheduleDateNo);

    @Query(value = """
            select count(*) > 0
            from issue_submission_statuses
            where schedule_id = :scheduleId
            """, nativeQuery = true)
    boolean hasIssueSubmissions(@Param("scheduleId") String scheduleId);

    @Modifying
    @Query("delete from ScheduleDetail detail where detail.id.scheduleId = :scheduleId")
    int deleteByScheduleId(@Param("scheduleId") String scheduleId);
    // Share this row lock with progress writes so removal cannot race with a new submission/comment.
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select detail from ScheduleDetail detail where detail.id = :id")
    java.util.Optional<ScheduleDetail> findForTaskUpdate(@Param("id") ScheduleDetailId id);

    @Query(value = """
            select schedule_date_no from issue_submission_statuses
            where schedule_id = :scheduleId and task_status_kbn <> 1
            union
            select schedule_date_no from issue_submission_statuses_comment
            where schedule_id = :scheduleId
            """, nativeQuery = true)
    java.util.Set<Integer> findTaskRemovalBlockedDates(@Param("scheduleId") String scheduleId);

}
