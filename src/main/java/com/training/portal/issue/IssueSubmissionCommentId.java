package com.training.portal.issue;

import java.io.Serializable;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

@Embeddable
public class IssueSubmissionCommentId implements Serializable {
    @Column(name = "schedule_id", length = 10, nullable = false)
    private String scheduleId;

    @Column(name = "schedule_date_no", nullable = false)
    private Integer scheduleDateNo;

    @Column(name = "sinjin_syain_id", length = 10, nullable = false)
    private String traineeEmployeeId;

    @Column(name = "comment_no", nullable = false)
    private Long commentNo;

    protected IssueSubmissionCommentId() {
    }

    public IssueSubmissionCommentId(String scheduleId, Integer scheduleDateNo,
            String traineeEmployeeId, Long commentNo) {
        this.scheduleId = scheduleId;
        this.scheduleDateNo = scheduleDateNo;
        this.traineeEmployeeId = traineeEmployeeId;
        this.commentNo = commentNo;
    }

    public String getScheduleId() { return scheduleId; }
    public Integer getScheduleDateNo() { return scheduleDateNo; }
    public String getTraineeEmployeeId() { return traineeEmployeeId; }
    public Long getCommentNo() { return commentNo; }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof IssueSubmissionCommentId that)) return false;
        return Objects.equals(scheduleId, that.scheduleId)
                && Objects.equals(scheduleDateNo, that.scheduleDateNo)
                && Objects.equals(traineeEmployeeId, that.traineeEmployeeId)
                && Objects.equals(commentNo, that.commentNo);
    }

    @Override
    public int hashCode() {
        return Objects.hash(scheduleId, scheduleDateNo, traineeEmployeeId, commentNo);
    }
}
