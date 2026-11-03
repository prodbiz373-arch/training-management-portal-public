package com.training.portal.member.exception;

public class MemberSelfDeleteNotAllowedException extends RuntimeException {
    public MemberSelfDeleteNotAllowedException() {
        super("ログイン中の本人は削除できません");
    }
}
