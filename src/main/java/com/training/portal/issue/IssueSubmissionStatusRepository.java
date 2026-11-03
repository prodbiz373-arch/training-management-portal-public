package com.training.portal.issue;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;

public interface IssueSubmissionStatusRepository
        extends JpaRepository<IssueSubmissionStatus, IssueSubmissionStatusId> {

    @Query("""
            select submission from IssueSubmissionStatus submission
            where submission.id.scheduleId in :scheduleIds
            """)
    List<IssueSubmissionStatus> findForSchedules(@Param("scheduleIds") Collection<String> scheduleIds);

    @Query("""
            select submission from IssueSubmissionStatus submission
            where submission.id.scheduleId in :scheduleIds
              and submission.id.traineeEmployeeId = :traineeEmployeeId
            """)
    List<IssueSubmissionStatus> findForTrainee(
            @Param("scheduleIds") Collection<String> scheduleIds,
            @Param("traineeEmployeeId") String traineeEmployeeId);

    @Query("""
            select distinct submission.instructorEmployeeId from IssueSubmissionStatus submission
            where submission.id.scheduleId = :scheduleId
              and submission.instructorEmployeeId is not null
            """)
    List<String> findReviewerEmployeeIds(@Param("scheduleId") String scheduleId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select submission from IssueSubmissionStatus submission where submission.id = :id")
    java.util.Optional<IssueSubmissionStatus> findForUpdate(@Param("id") IssueSubmissionStatusId id);
}
