package com.training.portal.schedule;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import com.training.portal.member.KenshuMember;

@Entity
@Table(name = "schedule_can_edit")
public class ScheduleCanEdit {

    @EmbeddedId
    private ScheduleCanEditId id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "schedule_id", referencedColumnName = "schedule_id", insertable = false, updatable = false)
    private Schedule schedule;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "syain_id", referencedColumnName = "syain_id", insertable = false, updatable = false)
    private KenshuMember member;

    @Column(name = "syain_kbn", nullable = false)
    private Short syainKbn;

    @Column(name = "can_edit", nullable = false)
    private Short canEdit;

    @Column(name = "pin_flg", nullable = false)
    private Short pinFlg;

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

    protected ScheduleCanEdit() {
    }

    public ScheduleCanEdit(String scheduleId, KenshuMember member, boolean canEdit, String operationUserCd) {
        this.id = new ScheduleCanEditId(scheduleId, member.getSyainId());
        this.syainKbn = member.getSyainKbn();
        this.canEdit = (short) (canEdit ? 1 : 0);
        this.pinFlg = 0;
        this.screenId = "SCD02";
        this.createUserCd = operationUserCd;
        this.updateUserCd = operationUserCd;
    }

    public void updatePermission(boolean editable, String operationUserCd) {
        this.canEdit = (short) (editable ? 1 : 0);
        this.screenId = "SCD02";
        this.updateUserCd = operationUserCd;
    }

    public void updatePin(boolean pinned, String operationUserCd) {
        this.pinFlg = (short) (pinned ? 1 : 0);
        this.screenId = "SCD01";
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

    public ScheduleCanEditId getId() {
        return id;
    }

    public Schedule getSchedule() {
        return schedule;
    }

    public KenshuMember getMember() {
        return member;
    }

    public void synchronizeMemberType(short type, String actor) {
        if (this.syainKbn != type) {
            this.syainKbn = type;
            this.screenId = "MMB01";
            this.updateUserCd = actor;
        }
    }

    public Short getSyainKbn() {
        return syainKbn;
    }

    public Short getCanEdit() {
        return canEdit;
    }

    public Short getPinFlg() {
        return pinFlg;
    }

    public Integer getUpdateCount() {
        return updateCount;
    }
}
