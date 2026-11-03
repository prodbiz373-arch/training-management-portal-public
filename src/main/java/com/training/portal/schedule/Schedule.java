package com.training.portal.schedule;

import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.PrePersist;

@Entity
@Table(name = "schedule")
public class Schedule {

    @Id
    @Column(name = "schedule_id", length = 10, nullable = false)
    private String scheduleId;

    @Column(name = "schedule_name", length = 40, nullable = false)
    private String scheduleName;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(name = "nohol_flg", nullable = false)
    private Short noholFlg;

    @Version
    @Column(name = "update_count", nullable = false)
    private Integer updateCount;

    @Column(name = "screen_id", length = 100, nullable = false)
    private String screenId;

    @Column(name = "create_user_cd", length = 100, nullable = false)
    private String createUserCd;

    @Column(name = "create_date", nullable = false)
    private LocalDateTime createDate;

    @Column(name = "update_user_cd", length = 100, nullable = false)
    private String updateUserCd;

    @Column(name = "update_date", nullable = false)
    private LocalDateTime updateDate;

    protected Schedule() {
    }

    public Schedule(String scheduleId, String scheduleName, LocalDate startDate, LocalDate endDate,
            boolean excludeHolidays, String operationUserCd) {
        this.scheduleId = scheduleId;
        this.scheduleName = scheduleName;
        this.startDate = startDate;
        this.endDate = endDate;
        this.noholFlg = (short) (excludeHolidays ? 1 : 0);
        this.screenId = "SCD02";
        this.createUserCd = operationUserCd;
        this.updateUserCd = operationUserCd;
    }

    @PrePersist
    private void prePersist() {
        var now = LocalDateTime.now();
        createDate = now;
        updateDate = now;
    }

    public void rename(String name) {
        this.scheduleName = name;
    }

    public void markUpdated(String operationUserCd) {
        this.screenId = "SCD02";
        this.updateUserCd = operationUserCd;
    }

    @PreUpdate
    private void preUpdate() {
        updateDate = LocalDateTime.now();
    }

    public String getScheduleId() {
        return scheduleId;
    }

    public String getScheduleName() {
        return scheduleName;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public Short getNoholFlg() {
        return noholFlg;
    }

    public Integer getUpdateCount() {
        return updateCount;
    }
}
