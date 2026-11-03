package com.training.portal.issue;

import org.hibernate.annotations.Immutable;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Immutable
@Table(name = "issue_statuses")
public class IssueStatus {
    @Id
    @Column(name = "task_status_kbn", nullable = false)
    private Short taskStatusKbn;

    @Column(name = "task_status_name", length = 20, nullable = false)
    private String taskStatusName;

    protected IssueStatus() {
    }

    public Short getTaskStatusKbn() {
        return taskStatusKbn;
    }

    public String getTaskStatusName() {
        return taskStatusName;
    }
}
