package com.training.portal.auth;

import org.springframework.security.core.userdetails.User;

/** Immutable authentication snapshot; credentials are erased by ProviderManager. */
public final class MemberPrincipal extends User {
    private final short memberType;
    private final long authVersion;

    public MemberPrincipal(String id, String password, short memberType, long authVersion) {
        super(id, password, User.withUsername(id).password(password)
                .roles(memberType == 2 ? "INSTRUCTOR" : "TRAINEE").build().getAuthorities());
        this.memberType = memberType;
        this.authVersion = authVersion;
    }
    public short getMemberType() { return memberType; }
    public long getAuthVersion() { return authVersion; }
    @Override public String toString() { return "MemberPrincipal[認証情報非表示]"; }
}
