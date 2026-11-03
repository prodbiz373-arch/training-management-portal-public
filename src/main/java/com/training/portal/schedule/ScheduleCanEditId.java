package com.training.portal.schedule;

import java.io.Serializable;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

@Embeddable
public class ScheduleCanEditId implements Serializable {
    private static final long serialVersionUID = 1L;

    @Column(name = "schedule_id", length = 10, nullable = false)
    private String scheduleId;

    @Column(name = "syain_id", length = 10, nullable = false)
    private String syainId;

    protected ScheduleCanEditId() {
    }

    public ScheduleCanEditId(String scheduleId, String syainId) {
        this.scheduleId = scheduleId;
        this.syainId = syainId;
    }

    public String getScheduleId() {
        return scheduleId;
    }

    public String getSyainId() {
        return syainId;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof ScheduleCanEditId that)) return false;
        return Objects.equals(scheduleId, that.scheduleId) && Objects.equals(syainId, that.syainId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(scheduleId, syainId);
    }
}
