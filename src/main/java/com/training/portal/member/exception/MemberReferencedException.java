package com.training.portal.member.exception;

public class MemberReferencedException extends RuntimeException {
    public MemberReferencedException() {
        super("参照されているためメンバーを削除できません");
    }
}
