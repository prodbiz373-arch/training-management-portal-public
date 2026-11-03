package com.training.portal.issue;

import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "issue_submission_statuses")
public class IssueSubmissionStatus {
    @EmbeddedId
    private IssueSubmissionStatusId id;

    @Column(name = "task_status_kbn", nullable = false)
    private Short taskStatusKbn;

    @Column(name = "koshi_syain_id", length = 10)
    private String instructorEmployeeId;

    @Column(name = "completed_date")
    private LocalDate completedDate;

    @Column(name = "screen_id", length = 100, nullable = false)
    private String screenId;

    @Version
    @Column(name = "update_count", nullable = false)
    private Integer updateCount;

    @Column(name = "create_user_cd", length = 100, nullable = false)
    private String createUserCd;

    @Column(name = "create_date", nullable = false)
    private LocalDateTime createDate;

    @Column(name = "update_user_cd", length = 100, nullable = false)
    private String updateUserCd;

    @Column(name = "update_date", nullable = false)
    private LocalDateTime updateDate;

    protected IssueSubmissionStatus() {
    }

    public IssueSubmissionStatus(IssueSubmissionStatusId id, String instructorEmployeeId,
            Short taskStatusKbn, LocalDate completedDate, String operationUserCd) {
        this.id = id;
        this.instructorEmployeeId = instructorEmployeeId;
        this.taskStatusKbn = taskStatusKbn;
        this.completedDate = completedDate;
        this.screenId = "PRM01";
        this.createUserCd = operationUserCd;
        this.updateUserCd = operationUserCd;
    }

    public void update(String instructorEmployeeId, Short taskStatusKbn,
            LocalDate completedDate, String operationUserCd) {
        this.instructorEmployeeId = instructorEmployeeId;
        this.taskStatusKbn = taskStatusKbn;
        this.completedDate = completedDate;
        this.screenId = "PRM01";
        this.updateUserCd = operationUserCd;
    }

    @PrePersist
    void prePersist() {
        var now = LocalDateTime.now();
        createDate = now;
        updateDate = now;
    }

    @PreUpdate
    void preUpdate() {
        updateDate = LocalDateTime.now();
    }

    public IssueSubmissionStatusId getId() {
        return id;
    }

    public Short getTaskStatusKbn() {
        return taskStatusKbn;
    }

    public String getInstructorEmployeeId() { return instructorEmployeeId; }
    public LocalDate getCompletedDate() { return completedDate; }
    public Integer getUpdateCount() { return updateCount; }
}
