package com.training.portal.issue;

import org.springframework.data.jpa.repository.JpaRepository;

public interface IssueStatusRepository extends JpaRepository<IssueStatus, Short> {
}
