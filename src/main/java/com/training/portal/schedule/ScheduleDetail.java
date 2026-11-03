package com.training.portal.schedule;

import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinColumns;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.PrePersist;

import com.training.portal.member.KenshuMember;

@Entity
@Table(name = "schedule_details")
public class ScheduleDetail {
    @EmbeddedId
    private ScheduleDetailId id;

    @Column(name = "bnrui_no", nullable = false)
    private Short bnruiNo;

    @Column(name = "koshi_syain_id", length = 10, nullable = false)
    private String koshiSyainId;

    @Column(name = "schedule_content", length = 50)
    private String scheduleContent;

    @Column(name = "task_flg", nullable = false)
    private Short taskFlg;

    @Column(name = "schedule_date", nullable = false)
    private LocalDate scheduleDate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumns({
            @JoinColumn(name = "schedule_id", referencedColumnName = "schedule_id", insertable = false, updatable = false),
            @JoinColumn(name = "bnrui_no", referencedColumnName = "bnrui_no", insertable = false, updatable = false)
    })
    private ScheduleBunrui bunrui;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "koshi_syain_id", referencedColumnName = "syain_id", insertable = false, updatable = false)
    private KenshuMember instructor;

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

    protected ScheduleDetail() {
    }

    public ScheduleDetail(String scheduleId, Integer scheduleDateNo, Short categoryNo,
            String instructorId, String content, boolean task, LocalDate scheduleDate,
            String operationUserCd) {
        this.id = new ScheduleDetailId(scheduleId, scheduleDateNo);
        this.bnruiNo = categoryNo;
        this.koshiSyainId = instructorId;
        this.scheduleContent = content;
        this.taskFlg = (short) (task ? 1 : 0);
        this.scheduleDate = scheduleDate;
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

    public void updateContent(Short categoryNo, String instructorId, String content,
            boolean task, String operationUserCd) {
        this.bnruiNo = categoryNo;
        this.koshiSyainId = instructorId;
        this.scheduleContent = content;
        this.taskFlg = (short) (task ? 1 : 0);
        this.screenId = "SCD02";
        this.updateUserCd = operationUserCd;
    }

    @PreUpdate
    private void preUpdate() {
        updateDate = LocalDateTime.now();
    }

    public ScheduleDetailId getId() { return id; }
    public Short getBnruiNo() { return bnruiNo; }
    public String getKoshiSyainId() { return koshiSyainId; }
    public String getScheduleContent() { return scheduleContent; }
    public Short getTaskFlg() { return taskFlg; }
    public LocalDate getScheduleDate() { return scheduleDate; }
    public ScheduleBunrui getBunrui() { return bunrui; }
    public KenshuMember getInstructor() { return instructor; }
    public Integer getUpdateCount() { return updateCount; }
}
