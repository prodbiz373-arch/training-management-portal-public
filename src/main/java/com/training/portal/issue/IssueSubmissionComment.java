package com.training.portal.issue;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "issue_submission_statuses_comment")
public class IssueSubmissionComment {
    @EmbeddedId
    private IssueSubmissionCommentId id;

    @Column(name = "comment_syain_id", length = 10)
    private String commentEmployeeId;

    @Column(name = "comment_content", length = 200, nullable = false)
    private String content;

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

    protected IssueSubmissionComment() {
    }

    public IssueSubmissionComment(IssueSubmissionCommentId id, String authorId,
            String content, String operationUserCd) {
        this.id = id;
        this.commentEmployeeId = authorId;
        this.content = content;
        this.screenId = "PRM01";
        this.createUserCd = operationUserCd;
        this.updateUserCd = operationUserCd;
    }

    public void update(String content, String operationUserCd) {
        this.content = content;
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
    void preUpdate() { updateDate = LocalDateTime.now(); }

    public IssueSubmissionCommentId getId() { return id; }
    public String getCommentEmployeeId() { return commentEmployeeId; }
    public String getContent() { return content; }
    public Integer getUpdateCount() { return updateCount; }
    public LocalDateTime getCreateDate() { return createDate; }
    public LocalDateTime getUpdateDate() { return updateDate; }
}
