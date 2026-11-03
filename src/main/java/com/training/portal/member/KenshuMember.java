package com.training.portal.member;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "kenshu_member")
public class KenshuMember {

    @Id
    @Column(name = "syain_id", length = 10, nullable = false)
    private String syainId;

    @Column(name = "syain_kbn", nullable = false)
    private Short syainKbn;

    @Column(name = "name", length = 50, nullable = false)
    private String name;

    @Column(name = "name_kana", length = 100)
    private String nameKana;

    @Column(name = "password", length = 255, nullable = false)
    private String password;

    @Column(name = "screen_id", length = 100, nullable = false)
    private String screenId;

    @Column(name = "auth_version", nullable = false)
    private Long authVersion = 0L;

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

    protected KenshuMember() {
    }

    public KenshuMember(
            String syainId,
            Short syainKbn,
            String name,
            String nameKana,
            String password,
            String screenId,
            String operationUserCd) {

        this.syainId = syainId;
        this.syainKbn = syainKbn;
        this.name = name;
        this.nameKana = nameKana;
        this.password = password;
        this.screenId = screenId;
        this.createUserCd = operationUserCd;
        this.updateUserCd = operationUserCd;
    }

    @PrePersist
    private void prePersist() {
        LocalDateTime now = LocalDateTime.now();

        if (createDate == null) {
            createDate = now;
        }

        updateDate = now;
    }

    @PreUpdate
    private void preUpdate() {
        updateDate = LocalDateTime.now();
    }

    public void updateProfile(
            Short syainKbn,
            String name,
            String nameKana,
            String screenId,
            String operationUserCd) {

        if (this.syainKbn != null && !this.syainKbn.equals(syainKbn)) {
            this.authVersion = Math.addExact(this.authVersion, 1L);
        }
        this.syainKbn = syainKbn;
        this.name = name;
        this.nameKana = nameKana;
        this.screenId = screenId;
        this.updateUserCd = operationUserCd;
    }

    public void changePassword(
            String encodedPassword,
            String screenId,
            String operationUserCd) {

        this.authVersion = Math.addExact(this.authVersion, 1L);
        this.password = encodedPassword;
        this.screenId = screenId;
        this.updateUserCd = operationUserCd;
    }

    public Long getAuthVersion() { return authVersion; }

    public String getSyainId() {
        return syainId;
    }

    public Short getSyainKbn() {
        return syainKbn;
    }

    public String getName() {
        return name;
    }

    public String getNameKana() {
        return nameKana;
    }

    public String getPassword() {
        return password;
    }

    public String getScreenId() {
        return screenId;
    }

    public Integer getUpdateCount() {
        return updateCount;
    }

    public String getCreateUserCd() {
        return createUserCd;
    }

    public LocalDateTime getCreateDate() {
        return createDate;
    }

    public String getUpdateUserCd() {
        return updateUserCd;
    }

    public LocalDateTime getUpdateDate() {
        return updateDate;
    }
}
