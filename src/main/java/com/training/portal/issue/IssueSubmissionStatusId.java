package com.training.portal.issue;

import java.io.Serializable;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

@Embeddable
public class IssueSubmissionStatusId implements Serializable {
    @Column(name = "schedule_id", length = 10, nullable = false)
    private String scheduleId;

    @Column(name = "schedule_date_no", nullable = false)
    private Integer scheduleDateNo;

    @Column(name = "sinjin_syain_id", length = 10, nullable = false)
    private String traineeEmployeeId;

    protected IssueSubmissionStatusId() {
    }

    public IssueSubmissionStatusId(String scheduleId, Integer scheduleDateNo, String traineeEmployeeId) {
        this.scheduleId = scheduleId;
        this.scheduleDateNo = scheduleDateNo;
        this.traineeEmployeeId = traineeEmployeeId;
    }

    public String getScheduleId() {
        return scheduleId;
    }

    public Integer getScheduleDateNo() {
        return scheduleDateNo;
    }

    public String getTraineeEmployeeId() {
        return traineeEmployeeId;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof IssueSubmissionStatusId that)) return false;
        return Objects.equals(scheduleId, that.scheduleId)
                && Objects.equals(scheduleDateNo, that.scheduleDateNo)
                && Objects.equals(traineeEmployeeId, that.traineeEmployeeId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(scheduleId, scheduleDateNo, traineeEmployeeId);
    }
}
