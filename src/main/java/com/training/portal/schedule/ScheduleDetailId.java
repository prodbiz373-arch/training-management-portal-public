package com.training.portal.schedule;

import java.io.Serializable;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

@Embeddable
public class ScheduleDetailId implements Serializable {
    @Column(name = "schedule_id", length = 10, nullable = false)
    private String scheduleId;

    @Column(name = "schedule_date_no", nullable = false)
    private Integer scheduleDateNo;

    protected ScheduleDetailId() {
    }

    public ScheduleDetailId(String scheduleId, Integer scheduleDateNo) {
        this.scheduleId = scheduleId;
        this.scheduleDateNo = scheduleDateNo;
    }

    public String getScheduleId() {
        return scheduleId;
    }

    public Integer getScheduleDateNo() {
        return scheduleDateNo;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof ScheduleDetailId that)) return false;
        return Objects.equals(scheduleId, that.scheduleId) && Objects.equals(scheduleDateNo, that.scheduleDateNo);
    }

    @Override
    public int hashCode() {
        return Objects.hash(scheduleId, scheduleDateNo);
    }
}
