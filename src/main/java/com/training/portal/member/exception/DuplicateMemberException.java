package com.training.portal.member.exception;

public class DuplicateMemberException extends RuntimeException {
    public DuplicateMemberException() {
        super("社員IDが重複しています");
    }
}
