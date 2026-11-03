package com.training.portal.member.exception;

public class MemberNotFoundException extends RuntimeException {
    public MemberNotFoundException() {
        super("メンバーが存在しません");
    }
}
