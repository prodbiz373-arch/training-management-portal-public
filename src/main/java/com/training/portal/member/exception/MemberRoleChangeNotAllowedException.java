package com.training.portal.member.exception;
public class MemberRoleChangeNotAllowedException extends RuntimeException {
    public MemberRoleChangeNotAllowedException() {
        super("スケジュールの人数条件、担当講師の参照、または提出履歴があるため、社員区分を変更できません");
    }
}
