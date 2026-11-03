package com.training.portal.member.exception;

public class MemberUpdateConflictException extends RuntimeException {
    public MemberUpdateConflictException() {
        super("メンバーが更新されています。再読み込みしてください");
    }
}
