package com.training.portal.schedule;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;

@Entity
@Table(name = "schedule_bunrui")
public class ScheduleBunrui {
    @EmbeddedId
    private ScheduleBunruiId id;

    @Column(name = "bnrui_name", length = 30, nullable = false)
    private String bnruiName;

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

    protected ScheduleBunrui() {
    }

    public ScheduleBunrui(String scheduleId, Short bnruiNo, String name, String operationUserCd) {
        this.id = new ScheduleBunruiId(scheduleId, bnruiNo);
        this.bnruiName = name;
        this.screenId = "SCD02";
        this.createUserCd = operationUserCd;
        this.updateUserCd = operationUserCd;
    }

    public void rename(String name, String operationUserCd) {
        this.bnruiName = name;
        this.screenId = "SCD02";
        this.updateUserCd = operationUserCd;
    }

    @PrePersist
    private void prePersist() {
        var now = LocalDateTime.now();
        if (createDate == null) createDate = now;
        updateDate = now;
    }

    @PreUpdate
    private void preUpdate() {
        updateDate = LocalDateTime.now();
    }

    public ScheduleBunruiId getId() {
        return id;
    }

    public String getBnruiName() {
        return bnruiName;
    }

    public Integer getUpdateCount() {
        return updateCount;
    }
}
