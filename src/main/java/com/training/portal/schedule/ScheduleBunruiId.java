package com.training.portal.schedule;

import java.io.Serializable;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

@Embeddable
public class ScheduleBunruiId implements Serializable {
    @Column(name = "schedule_id", length = 10, nullable = false)
    private String scheduleId;

    @Column(name = "bnrui_no", nullable = false)
    private Short bnruiNo;

    protected ScheduleBunruiId() {
    }

    public ScheduleBunruiId(String scheduleId, Short bnruiNo) {
        this.scheduleId = scheduleId;
        this.bnruiNo = bnruiNo;
    }

    public String getScheduleId() {
        return scheduleId;
    }

    public Short getBnruiNo() {
        return bnruiNo;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof ScheduleBunruiId that)) return false;
        return Objects.equals(scheduleId, that.scheduleId) && Objects.equals(bnruiNo, that.bnruiNo);
    }

    @Override
    public int hashCode() {
        return Objects.hash(scheduleId, bnruiNo);
    }
}
