package com.training.portal.common.api;

import java.util.List;
import java.util.Set;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import com.training.portal.member.exception.*;
import com.training.portal.schedule.exception.ScheduleAccessDeniedException;
import com.training.portal.schedule.exception.ScheduleNotFoundException;
import com.training.portal.schedule.exception.ScheduleInvalidInputException;
import com.training.portal.schedule.exception.ScheduleUpdateConflictException;
import com.training.portal.schedule.exception.ScheduleTraineeRequiredException;
import com.training.portal.schedule.exception.ScheduleEditableInstructorRequiredException;
import com.training.portal.schedule.exception.ScheduleCategoryInUseException;
import com.training.portal.schedule.exception.DuplicateScheduleException;
import com.training.portal.schedule.exception.ScheduleHasIssueSubmissionsException;
import com.training.portal.schedule.exception.SchedulePinLimitExceededException;
import com.training.portal.schedule.exception.SchedulePermissionMemberInUseException;
import com.training.portal.schedule.exception.ScheduleReviewerPermissionRequiredException;
import com.training.portal.progress.exception.ProgressAccessDeniedException;
import com.training.portal.progress.exception.ProgressInvalidInputException;
import com.training.portal.progress.exception.ProgressNotFoundException;
import com.training.portal.progress.exception.ProgressUpdateConflictException;

@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {
    private static final Set<String> FIELDS = Set.of("syainId", "syainKbn", "name", "nameKana", "password",
            "reviewerEmployeeId", "statusCode", "content", "updateCount");

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        var errors = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> new ApiError.FieldIssue(FIELDS.contains(error.getField()) ? error.getField() : "request",
                        validationMessage(error.getCode())))
                .distinct().sorted(java.util.Comparator.comparing(ApiError.FieldIssue::field)
                        .thenComparing(ApiError.FieldIssue::message)).toList();
        return new ResponseEntity<>(new ApiError("INVALID_INPUT", "入力内容を確認してください", errors), headers, status);
    }

    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(
            org.springframework.web.method.annotation.HandlerMethodValidationException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        if (ex.isForReturnValue()) {
            return error(500, "INTERNAL_ERROR", "処理に失敗しました");
        }
        var errors = ex.getParameterValidationResults().stream()
                .filter(org.springframework.validation.method.ParameterErrors.class::isInstance)
                .map(org.springframework.validation.method.ParameterErrors.class::cast)
                .flatMap(result -> result.getFieldErrors().stream())
                .map(field -> new ApiError.FieldIssue(FIELDS.contains(field.getField()) ? field.getField() : "request",
                        validationMessage(field.getCode())))
                .distinct().sorted(java.util.Comparator.comparing(ApiError.FieldIssue::field)
                        .thenComparing(ApiError.FieldIssue::message)).toList();
        return new ResponseEntity<>(new ApiError("INVALID_INPUT", "入力内容を確認してください", errors), headers, status);
    }

    private String validationMessage(String code) {
        if (code == null) return "入力内容が不正です";
        return switch (code) {
            case "NotBlank", "NotNull" -> "必須項目です";
            case "Size" -> "入力文字数が上限を超えています";
            case "Min", "Max" -> "指定可能な値ではありません";
            case "PositiveOrZero" -> "0以上で入力してください";
            case "BCryptInput" -> "UTF-8で72バイト以内で入力してください";
            default -> "入力内容が不正です";
        };
    }

    // Spring MVCのJSON解析・ヘッダー・メソッド検証エラーも内部情報を返さない。
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        String code = status.value() == 400 ? "INVALID_INPUT" : "HTTP_ERROR";
        String message = status.is5xxServerError() ? "処理に失敗しました" : "リクエストを確認してください";
        return new ResponseEntity<>(new ApiError(code, message, List.of()), headers, status);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<Object> invalidInput() {
        return error(400, "INVALID_INPUT", "入力内容を確認してください");
    }

    @ExceptionHandler(MemberNotFoundException.class)
    ResponseEntity<Object> notFound() {
        return error(404, "MEMBER_NOT_FOUND", "メンバーが存在しません");
    }

    @ExceptionHandler(DuplicateMemberException.class)
    ResponseEntity<Object> duplicate() {
        return error(409, "MEMBER_DUPLICATE", "社員IDが重複しています");
    }

    @ExceptionHandler(MemberRoleChangeNotAllowedException.class)
    ResponseEntity<Object> memberRoleChangeNotAllowed(MemberRoleChangeNotAllowedException exception) {
        return error(409, "MEMBER_ROLE_CHANGE_NOT_ALLOWED", exception.getMessage());
    }

    @ExceptionHandler(MemberUpdateConflictException.class)
    ResponseEntity<Object> conflict() {
        return error(409, "MEMBER_UPDATE_CONFLICT", "メンバーが更新されています。再読み込みしてください");
    }

    @ExceptionHandler(MemberReferencedException.class)
    ResponseEntity<Object> referenced() {
        return error(409, "MEMBER_REFERENCED", "参照されているためメンバーを削除できません");
    }

    @ExceptionHandler(MemberSelfDeleteNotAllowedException.class)
    ResponseEntity<Object> selfDelete() {
        return error(409, "MEMBER_SELF_DELETE_NOT_ALLOWED", "ログイン中の本人は削除できません");
    }

    @ExceptionHandler(ScheduleNotFoundException.class)
    ResponseEntity<Object> scheduleNotFound() {
        return error(404, "SCHEDULE_NOT_FOUND", "スケジュールが存在しません");
    }

    @ExceptionHandler(DuplicateScheduleException.class)
    ResponseEntity<Object> scheduleDuplicate() {
        return error(409, "SCHEDULE_DUPLICATE", "スケジュールIDが重複しています");
    }

    @ExceptionHandler(ScheduleAccessDeniedException.class)
    ResponseEntity<Object> scheduleAccessDenied() {
        return error(403, "ACCESS_DENIED", "アクセス権限がありません");
    }

    @ExceptionHandler(com.training.portal.schedule.exception.ScheduleTaskRemovalBlockedException.class)
    ResponseEntity<Object> scheduleTaskRemovalBlocked() {
        return error(400, "INVALID_INPUT", "未提出以外の提出状況、またはコメントが登録されているため、課題を解除できません。");
    }

    @ExceptionHandler(ScheduleInvalidInputException.class)
    ResponseEntity<Object> scheduleInvalidInput() {
        return error(400, "INVALID_INPUT", "入力内容を確認してください");
    }

    @ExceptionHandler(ScheduleUpdateConflictException.class)
    ResponseEntity<Object> scheduleUpdateConflict() {
        return error(409, "SCHEDULE_UPDATE_CONFLICT", "他のユーザーによって更新されています。最新の情報を再読み込みしてください。");
    }

    @ExceptionHandler(ScheduleTraineeRequiredException.class)
    ResponseEntity<Object> scheduleTraineeRequired() {
        return error(400, "INVALID_INPUT", "新人は1人以上登録する必要があります。");
    }

    @ExceptionHandler(ScheduleEditableInstructorRequiredException.class)
    ResponseEntity<Object> scheduleEditableInstructorRequired() {
        return error(400, "INVALID_INPUT", "編集権限を持つ講師を1人以上登録する必要があります。");
    }

    @ExceptionHandler(SchedulePermissionMemberInUseException.class)
    ResponseEntity<Object> schedulePermissionMemberInUse() {
        return error(400, "INVALID_INPUT", "進捗・課題管理でレビュー者として登録、またはコメントを記載しているため削除できません");
    }

    @ExceptionHandler(ScheduleReviewerPermissionRequiredException.class)
    ResponseEntity<Object> scheduleReviewerPermissionRequired() {
        return error(400, "INVALID_INPUT", "進捗・課題管理でレビュー者として登録されているため参照のみに変更できません");
    }

    @ExceptionHandler(ScheduleCategoryInUseException.class)
    ResponseEntity<Object> scheduleCategoryInUse() {
        return error(400, "INVALID_INPUT", "この分類はスケジュール内容で使用されているため削除できません。");
    }

    @ExceptionHandler(ScheduleHasIssueSubmissionsException.class)
    ResponseEntity<Object> scheduleHasIssueSubmissions() {
        return error(400, "INVALID_INPUT", "課題提出状況が登録されているため、このスケジュールは削除できません。");
    }

    @ExceptionHandler(SchedulePinLimitExceededException.class)
    ResponseEntity<Object> schedulePinLimitExceeded() {
        return error(400, "INVALID_INPUT", "ピン留めできるスケジュールは2件までです。");
    }

    @ExceptionHandler(ProgressNotFoundException.class)
    ResponseEntity<Object> progressNotFound() {
        return error(404, "PROGRESS_NOT_FOUND", "課題が存在しません");
    }

    @ExceptionHandler(ProgressAccessDeniedException.class)
    ResponseEntity<Object> progressAccessDenied() {
        return error(403, "ACCESS_DENIED", "アクセス権限がありません");
    }

    @ExceptionHandler(ProgressInvalidInputException.class)
    ResponseEntity<Object> progressInvalidInput() {
        return error(400, "INVALID_INPUT", "入力内容を確認してください");
    }

    @ExceptionHandler(ProgressUpdateConflictException.class)
    ResponseEntity<Object> progressUpdateConflict() {
        return error(409, "PROGRESS_UPDATE_CONFLICT", "他のユーザーによって更新されています。最新の情報を再読み込みしてください。");
    }

    @ExceptionHandler(org.springframework.security.core.AuthenticationException.class)
    ResponseEntity<Object> loginFailed() {
        return error(401, "LOGIN_FAILED", "社員IDまたはパスワードが正しくありません");
    }

    @ExceptionHandler(org.springframework.security.access.AccessDeniedException.class)
    ResponseEntity<Object> accessDenied() {
        return error(403, "ACCESS_DENIED", "アクセス権限がありません");
    }

    @ExceptionHandler(com.training.portal.auth.LoginAttemptLimiter.Limited.class)
    ResponseEntity<Object> loginLimited(com.training.portal.auth.LoginAttemptLimiter.Limited exception) {
        return ResponseEntity.status(429).header("Retry-After", Long.toString(exception.retryAfter()))
                .body(new ApiError("LOGIN_RATE_LIMITED", "しばらく待ってから再試行してください", List.of()));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<Object> unexpected() {
        return error(500, "INTERNAL_ERROR", "処理に失敗しました");
    }

    private ResponseEntity<Object> error(int status, String code, String message) {
        return ResponseEntity.status(status).body(new ApiError(code, message, List.of()));
    }
}
