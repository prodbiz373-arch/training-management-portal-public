package com.training.portal.issue;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

public interface IssueSubmissionCommentRepository
        extends JpaRepository<IssueSubmissionComment, IssueSubmissionCommentId> {

    @Query("""
            select comment from IssueSubmissionComment comment
            where comment.id.scheduleId in :scheduleIds
            order by comment.id.scheduleId, comment.id.scheduleDateNo,
                comment.id.traineeEmployeeId, comment.id.commentNo
            """)
    List<IssueSubmissionComment> findForSchedules(@Param("scheduleIds") Collection<String> scheduleIds);

    @Query("""
            select distinct comment.commentEmployeeId from IssueSubmissionComment comment
            where comment.id.scheduleId = :scheduleId
              and comment.commentEmployeeId is not null
            """)
    List<String> findCommentEmployeeIds(@Param("scheduleId") String scheduleId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select comment from IssueSubmissionComment comment
            where comment.id.scheduleId = :scheduleId
              and comment.id.scheduleDateNo = :scheduleDateNo
              and comment.id.traineeEmployeeId = :traineeId
            order by comment.id.commentNo
            """)
    List<IssueSubmissionComment> lockSubmissionComments(
            @Param("scheduleId") String scheduleId,
            @Param("scheduleDateNo") Integer scheduleDateNo,
            @Param("traineeId") String traineeId);
}
