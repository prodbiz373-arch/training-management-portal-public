package com.training.portal.schedule;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.sql.SQLException;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.training.portal.member.KenshuMember;
import com.training.portal.member.KenshuMemberRepository;
import com.training.portal.issue.IssueSubmissionStatusRepository;
import com.training.portal.issue.IssueSubmissionCommentRepository;
import com.training.portal.schedule.dto.ScheduleListResponse;
import com.training.portal.schedule.dto.ScheduleMemberOptionResponse;
import com.training.portal.schedule.dto.ScheduleDetailResponse;
import com.training.portal.schedule.dto.ScheduleCreateRequest;
import com.training.portal.schedule.dto.ScheduleUpdateRequest;
import com.training.portal.schedule.dto.SchedulePinUpdateRequest;
import com.training.portal.schedule.dto.SchedulePinUpdateResponse;
import com.training.portal.schedule.exception.DuplicateScheduleException;
import com.training.portal.schedule.exception.ScheduleAccessDeniedException;
import com.training.portal.schedule.exception.ScheduleInvalidInputException;
import com.training.portal.schedule.exception.ScheduleNotFoundException;
import com.training.portal.schedule.exception.ScheduleUpdateConflictException;
import com.training.portal.schedule.exception.ScheduleTraineeRequiredException;
import com.training.portal.schedule.exception.ScheduleEditableInstructorRequiredException;
import com.training.portal.schedule.exception.ScheduleCategoryInUseException;
import com.training.portal.schedule.exception.ScheduleHasIssueSubmissionsException;
import com.training.portal.schedule.exception.SchedulePinLimitExceededException;
import com.training.portal.schedule.exception.SchedulePermissionMemberInUseException;
import com.training.portal.schedule.exception.ScheduleReviewerPermissionRequiredException;

@Service
@Transactional(readOnly = true)
public class ScheduleService {
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Tokyo");
    private final ScheduleCanEditRepository permissions;
    private final ScheduleRepository schedules;
    private final ScheduleBunruiRepository categories;
    private final ScheduleDetailRepository details;
    private final KenshuMemberRepository members;
    private final EntityManager entityManager;
    private final JapaneseHolidayService japaneseHolidays;
    private final IssueSubmissionStatusRepository issueSubmissions;
    private final IssueSubmissionCommentRepository issueComments;

    public ScheduleService(
            ScheduleCanEditRepository permissions,
            ScheduleRepository schedules,
            ScheduleBunruiRepository categories,
            ScheduleDetailRepository details,
            KenshuMemberRepository members,
            EntityManager entityManager,
            JapaneseHolidayService japaneseHolidays,
            IssueSubmissionStatusRepository issueSubmissions,
            IssueSubmissionCommentRepository issueComments) {
        this.permissions = permissions;
        this.schedules = schedules;
        this.categories = categories;
        this.details = details;
        this.members = members;
        this.entityManager = entityManager;
        this.japaneseHolidays = japaneseHolidays;
        this.issueSubmissions = issueSubmissions;
        this.issueComments = issueComments;
    }

    public List<LocalDate> findNonWorkingDates(LocalDate startDate, LocalDate endDate) {
        return japaneseHolidays.findNonWorkingDates(startDate, endDate);
    }

    public List<ScheduleMemberOptionResponse> findMemberOptions() {
        return members.findAllByOrderBySyainIdAsc().stream()
                .map(ScheduleMemberOptionResponse::from)
                .toList();
    }

    public List<ScheduleListResponse> findAccessibleSchedules(String syainId) {
        return permissions.findAccessibleSchedules(syainId, LocalDate.now(BUSINESS_ZONE)).stream()
                .map(ScheduleListResponse::from)
                .toList();
    }

    @Transactional
    public SchedulePinUpdateResponse updatePin(
            String scheduleId,
            SchedulePinUpdateRequest request,
            String operationSyainId) {
        if (!schedules.existsById(scheduleId)) throw new ScheduleNotFoundException();

        // 利用者の全権限行を同じ順序でロックし、別スケジュールへの同時操作でも上限2件を維持する。
        var userPermissions = permissions.lockUserPermissions(operationSyainId);
        var permission = userPermissions.stream()
                .filter(row -> row.getId().getScheduleId().equals(scheduleId))
                .findFirst()
                .orElseThrow(ScheduleAccessDeniedException::new);
        if (!Objects.equals(request.permissionUpdateCount(), permission.getUpdateCount())) {
            throw new ScheduleUpdateConflictException();
        }

        boolean currentPinned = permission.getPinFlg() == 1;
        if (currentPinned == request.pinned()) {
            return new SchedulePinUpdateResponse(scheduleId, currentPinned, permission.getUpdateCount());
        }
        if (request.pinned() && userPermissions.stream().filter(row -> row.getPinFlg() == 1).count() >= 2) {
            throw new SchedulePinLimitExceededException();
        }

        try {
            permission.updatePin(request.pinned(), operationSyainId);
            permissions.flush();
            return new SchedulePinUpdateResponse(scheduleId, request.pinned(), permission.getUpdateCount());
        } catch (OptimisticLockingFailureException | jakarta.persistence.OptimisticLockException ex) {
            throw new ScheduleUpdateConflictException();
        }
    }

    public ScheduleDetailResponse findAccessibleScheduleDetail(String scheduleId, String syainId) {
        var permission = permissions.findAccessibleSchedule(scheduleId, syainId)
                .orElseThrow(() -> schedules.existsById(scheduleId)
                        ? new ScheduleAccessDeniedException()
                        : new ScheduleNotFoundException());
        var usage = findPermissionUsage(scheduleId);
        return ScheduleDetailResponse.from(
                permission,
                permissions.findSchedulePermissions(scheduleId),
                categories.findByIdScheduleIdOrderByIdBnruiNoAsc(scheduleId),
                details.findScheduleDetails(scheduleId),
                usage.reviewerEmployeeIds(),
                usage.commentEmployeeIds(), details.findTaskRemovalBlockedDates(scheduleId));
    }

    @Transactional
    public ScheduleDetailResponse createSchedule(ScheduleCreateRequest request, String operationSyainId) {
        members.findById(operationSyainId).orElseThrow(ScheduleAccessDeniedException::new);
        if (schedules.existsById(request.scheduleId())) throw new DuplicateScheduleException();
        if (request.startDate().isAfter(request.endDate())) throw new ScheduleInvalidInputException();
        if (request.excludeHolidays()) {
            japaneseHolidays.findNonWorkingDates(request.startDate(), request.endDate());
        }

        var requestedPermissions = unique(request.permissions(), ScheduleCreateRequest.Permission::employeeId);
        requestedPermissions.putIfAbsent(operationSyainId,
                new ScheduleCreateRequest.Permission(operationSyainId, true));
        var requestedCategories = unique(request.categories(), ScheduleCreateRequest.Category::categoryNo);
        var requestedDetails = unique(request.details(), ScheduleCreateRequest.Detail::scheduleDateNo);
        validateCategoryNames(requestedCategories.values().stream()
                .map(ScheduleCreateRequest.Category::categoryName).toList());

        var memberIds = new HashSet<>(requestedPermissions.keySet());
        requestedDetails.values().forEach(row -> memberIds.add(row.instructorEmployeeId()));
        var memberMap = members.findAllById(memberIds).stream()
                .collect(Collectors.toMap(KenshuMember::getSyainId, Function.identity()));
        if (memberMap.size() != memberIds.size()) throw new ScheduleInvalidInputException();
        validateCreatePermissions(operationSyainId, requestedPermissions, memberMap);
        validateCreateDetails(request, requestedPermissions, requestedCategories, requestedDetails, memberMap);

        try {
            schedules.saveAndFlush(new Schedule(request.scheduleId(), request.scheduleName().trim(),
                    request.startDate(), request.endDate(), request.excludeHolidays(), operationSyainId));
            permissions.saveAll(requestedPermissions.values().stream()
                    .map(row -> new ScheduleCanEdit(request.scheduleId(), memberMap.get(row.employeeId()),
                            row.canEdit(), operationSyainId))
                    .toList());
            permissions.flush();
            categories.saveAll(requestedCategories.values().stream()
                    .map(row -> new ScheduleBunrui(request.scheduleId(), row.categoryNo(),
                            row.categoryName().trim(), operationSyainId))
                    .toList());
            categories.flush();
            details.saveAll(requestedDetails.values().stream()
                    .map(row -> new ScheduleDetail(request.scheduleId(), row.scheduleDateNo(), row.categoryNo(),
                            row.instructorEmployeeId(), normalizeContent(row.content()), row.taskFlg(), row.scheduleDate(),
                            operationSyainId))
                    .toList());
            details.flush();
            entityManager.clear();
            return findAccessibleScheduleDetail(request.scheduleId(), operationSyainId);
        } catch (DataIntegrityViolationException ex) {
            if (hasSqlState(ex, "23505")) throw new DuplicateScheduleException();
            throw new ScheduleInvalidInputException();
        }
    }

    private void validateCreatePermissions(
            String operationSyainId,
            Map<String, ScheduleCreateRequest.Permission> requestedPermissions,
            Map<String, KenshuMember> memberMap) {
        var actorPermission = requestedPermissions.get(operationSyainId);
        if (actorPermission == null || !actorPermission.canEdit()) throw new ScheduleInvalidInputException();
        boolean hasTrainee = requestedPermissions.keySet().stream()
                .map(memberMap::get)
                .anyMatch(member -> member.getSyainKbn() == 1);
        if (!hasTrainee) throw new ScheduleTraineeRequiredException();
        validateEditableInstructorRequired(requestedPermissions.keySet(),
                id -> requestedPermissions.get(id).canEdit(), memberMap);
    }

    private void validateCreateDetails(
            ScheduleCreateRequest request,
            Map<String, ScheduleCreateRequest.Permission> requestedPermissions,
            Map<Short, ScheduleCreateRequest.Category> requestedCategories,
            Map<Integer, ScheduleCreateRequest.Detail> requestedDetails,
            Map<String, KenshuMember> memberMap) {
        for (var row : requestedDetails.values()) {
            validateTaskContent(row.content(), row.taskFlg());
            if (!requestedCategories.containsKey(row.categoryNo())) throw new ScheduleInvalidInputException();
            var instructor = memberMap.get(row.instructorEmployeeId());
            if (instructor == null || instructor.getSyainKbn() != 2
                    || !requestedPermissions.containsKey(instructor.getSyainId())) {
                throw new ScheduleInvalidInputException();
            }
            if (row.scheduleDate().isBefore(request.startDate()) || row.scheduleDate().isAfter(request.endDate())) {
                throw new ScheduleInvalidInputException();
            }
            if (request.excludeHolidays() && japaneseHolidays.isNonWorkingDay(row.scheduleDate())) {
                throw new ScheduleInvalidInputException();
            }
        }
    }

    @Transactional
    public ScheduleDetailResponse updateSchedule(
            String scheduleId,
            ScheduleUpdateRequest request,
            String operationSyainId) {
        var schedule = schedules.findById(scheduleId).orElseThrow(ScheduleNotFoundException::new);
        var actorPermission = permissions.findAccessibleSchedule(scheduleId, operationSyainId)
                .orElseThrow(ScheduleAccessDeniedException::new);
        if (actorPermission.getCanEdit() != 1) throw new ScheduleAccessDeniedException();
        if (!Objects.equals(request.scheduleUpdateCount(), schedule.getUpdateCount())) {
            throw new ScheduleUpdateConflictException();
        }

        var currentPermissions = permissions.findSchedulePermissions(scheduleId);
        var currentCategories = categories.findByIdScheduleIdOrderByIdBnruiNoAsc(scheduleId);
        var currentDetails = details.findScheduleDetails(scheduleId);
        var requestedPermissions = unique(request.permissions(), ScheduleUpdateRequest.Permission::employeeId);
        var deletedPermissions = unique(request.deletedPermissions(), ScheduleUpdateRequest.DeletedPermission::employeeId);
        var requestedCategories = unique(request.categories(), ScheduleUpdateRequest.Category::categoryNo);
        var deletedCategories = unique(request.deletedCategories(), ScheduleUpdateRequest.DeletedCategory::categoryNo);
        var requestedDetails = unique(request.details(), ScheduleUpdateRequest.Detail::scheduleDateNo);

        validateProgressPermissionUsage(scheduleId, currentPermissions, requestedPermissions, deletedPermissions);
        validatePermissionRequest(operationSyainId, currentPermissions, requestedPermissions, deletedPermissions);
        var memberMap = loadAndValidateMembers(requestedPermissions, requestedDetails);
        validateTraineeRequired(requestedPermissions, memberMap);
        validateEditableInstructorRequired(requestedPermissions.keySet(),
                id -> requestedPermissions.get(id).canEdit(), memberMap);
        validateDetailInstructors(requestedPermissions, requestedDetails, memberMap);
        validateCategoryRequest(currentCategories, requestedCategories, deletedCategories);
        validateDetailRequest(schedule, currentDetails, requestedDetails, requestedCategories, deletedCategories, memberMap);

        try {
            entityManager.lock(schedule, LockModeType.OPTIMISTIC);
            schedule.rename(request.scheduleName());
            schedule.markUpdated(operationSyainId);
            syncPermissions(scheduleId, operationSyainId, currentPermissions,
                    requestedPermissions, deletedPermissions, memberMap);
            syncCategories(scheduleId, operationSyainId, currentCategories, requestedCategories);
            syncDetails(operationSyainId, currentDetails, requestedDetails);
            // 日別内容が保持する読み取り用の分類関連をdetachしてから、付け替え元の分類を削除する。
            entityManager.clear();
            deleteCategories(scheduleId, deletedCategories);
            entityManager.flush();
            entityManager.clear();
            return findAccessibleScheduleDetail(scheduleId, operationSyainId);
        } catch (OptimisticLockingFailureException | jakarta.persistence.OptimisticLockException ex) {
            throw new ScheduleUpdateConflictException();
        } catch (DataIntegrityViolationException ex) {
            if (hasSqlState(ex, "23505")) throw new ScheduleUpdateConflictException();
            throw new ScheduleInvalidInputException();
        }
    }

    @Transactional
    public void deleteSchedule(String scheduleId, int scheduleUpdateCount, String operationSyainId) {
        var schedule = schedules.findById(scheduleId).orElseThrow(ScheduleNotFoundException::new);
        var actorPermission = permissions.findAccessibleSchedule(scheduleId, operationSyainId)
                .orElseThrow(ScheduleAccessDeniedException::new);
        if (actorPermission.getCanEdit() != 1) throw new ScheduleAccessDeniedException();
        if (!Objects.equals(scheduleUpdateCount, schedule.getUpdateCount())) {
            throw new ScheduleUpdateConflictException();
        }
        if (details.hasIssueSubmissions(scheduleId)) {
            throw new ScheduleHasIssueSubmissionsException();
        }

        try {
            details.deleteByScheduleId(scheduleId);
            categories.deleteByScheduleId(scheduleId);
            permissions.deleteByScheduleId(scheduleId);
            // bulk delete済みの権限Entityをdetachし、scheduleの更新カウンタをDELETE条件にして競合を検出する。
            entityManager.clear();
            if (schedules.deleteByScheduleIdAndUpdateCount(scheduleId, scheduleUpdateCount) != 1) {
                throw new ScheduleUpdateConflictException();
            }
        } catch (OptimisticLockingFailureException | jakarta.persistence.OptimisticLockException ex) {
            throw new ScheduleUpdateConflictException();
        } catch (DataIntegrityViolationException ex) {
            // 事前確認後に提出状況が登録された競合でも、FK例外を内部エラーとして公開しない。
            throw new ScheduleHasIssueSubmissionsException();
        }
    }

    private void validatePermissionRequest(
            String operationSyainId,
            List<ScheduleCanEdit> current,
            Map<String, ScheduleUpdateRequest.Permission> requested,
            Map<String, ScheduleUpdateRequest.DeletedPermission> deleted) {
        var actor = requested.get(operationSyainId);
        if (actor == null || !actor.canEdit() || deleted.containsKey(operationSyainId)) {
            throw new ScheduleInvalidInputException();
        }
        var currentMap = current.stream().collect(Collectors.toMap(row -> row.getId().getSyainId(), Function.identity()));
        for (var entry : currentMap.entrySet()) {
            var next = requested.get(entry.getKey());
            var removed = deleted.get(entry.getKey());
            if ((next == null) == (removed == null)) throw new ScheduleInvalidInputException();
            int expected = next != null ? next.updateCount() : removed.updateCount();
            if (!Objects.equals(expected, entry.getValue().getUpdateCount())) throw new ScheduleUpdateConflictException();
        }
        for (var entry : requested.entrySet()) {
            if (!currentMap.containsKey(entry.getKey()) && entry.getValue().updateCount() != 0) {
                throw new ScheduleInvalidInputException();
            }
        }
        if (deleted.keySet().stream().anyMatch(id -> !currentMap.containsKey(id))) {
            throw new ScheduleInvalidInputException();
        }
    }

    private void validateProgressPermissionUsage(
            String scheduleId,
            List<ScheduleCanEdit> current,
            Map<String, ScheduleUpdateRequest.Permission> requested,
            Map<String, ScheduleUpdateRequest.DeletedPermission> deleted) {
        var usage = findPermissionUsage(scheduleId);
        if (deleted.keySet().stream().anyMatch(id -> usage.reviewerEmployeeIds().contains(id)
                || usage.commentEmployeeIds().contains(id))) {
            throw new SchedulePermissionMemberInUseException();
        }

        var currentMap = current.stream()
                .collect(Collectors.toMap(row -> row.getId().getSyainId(), Function.identity()));
        boolean reviewerDowngraded = usage.reviewerEmployeeIds().stream().anyMatch(id -> {
            var currentPermission = currentMap.get(id);
            var nextPermission = requested.get(id);
            return currentPermission != null && currentPermission.getCanEdit() == 1
                    && nextPermission != null && !nextPermission.canEdit();
        });
        if (reviewerDowngraded) throw new ScheduleReviewerPermissionRequiredException();
    }

    private PermissionUsage findPermissionUsage(String scheduleId) {
        return new PermissionUsage(
                new HashSet<>(issueSubmissions.findReviewerEmployeeIds(scheduleId)),
                new HashSet<>(issueComments.findCommentEmployeeIds(scheduleId)));
    }

    private record PermissionUsage(Set<String> reviewerEmployeeIds, Set<String> commentEmployeeIds) {
    }

    private Map<String, KenshuMember> loadAndValidateMembers(
            Map<String, ScheduleUpdateRequest.Permission> requestedPermissions,
            Map<Integer, ScheduleUpdateRequest.Detail> requestedDetails) {
        var ids = new HashSet<>(requestedPermissions.keySet());
        requestedDetails.values().forEach(detail -> ids.add(detail.instructorEmployeeId()));
        var result = members.findAllById(ids).stream()
                .collect(Collectors.toMap(KenshuMember::getSyainId, Function.identity()));
        if (result.size() != ids.size()) throw new ScheduleInvalidInputException();
        return result;
    }

    private void validateDetailInstructors(
            Map<String, ScheduleUpdateRequest.Permission> requestedPermissions,
            Map<Integer, ScheduleUpdateRequest.Detail> requestedDetails,
            Map<String, KenshuMember> memberMap) {
        var finalPermissionIds = requestedPermissions.keySet();
        for (var detail : requestedDetails.values()) {
            var instructor = memberMap.get(detail.instructorEmployeeId());
            if (instructor.getSyainKbn() != 2 || !finalPermissionIds.contains(instructor.getSyainId())) {
                throw new ScheduleInvalidInputException();
            }
        }
    }

    private void validateTraineeRequired(
            Map<String, ScheduleUpdateRequest.Permission> requestedPermissions,
            Map<String, KenshuMember> memberMap) {
        boolean hasTrainee = requestedPermissions.keySet().stream()
                .map(memberMap::get)
                .anyMatch(member -> member != null && member.getSyainKbn() == 1);
        if (!hasTrainee) throw new ScheduleTraineeRequiredException();
    }

    private void validateEditableInstructorRequired(
            Set<String> permissionIds,
            Predicate<String> canEdit,
            Map<String, KenshuMember> memberMap) {
        boolean hasEditableInstructor = permissionIds.stream()
                .anyMatch(id -> canEdit.test(id)
                        && memberMap.get(id) != null
                        && memberMap.get(id).getSyainKbn() == 2);
        if (!hasEditableInstructor) throw new ScheduleEditableInstructorRequiredException();
    }

    private void validateCategoryNames(List<String> names) {
        if (names.stream().map(String::trim).distinct().count() != names.size()) {
            throw new ScheduleInvalidInputException();
        }
    }

    private void validateCategoryRequest(
            List<ScheduleBunrui> current,
            Map<Short, ScheduleUpdateRequest.Category> requested,
            Map<Short, ScheduleUpdateRequest.DeletedCategory> deleted) {
        if (requested.size() > 99) throw new ScheduleInvalidInputException();
        validateCategoryNames(requested.values().stream()
                .map(ScheduleUpdateRequest.Category::categoryName).toList());
        var currentMap = current.stream().collect(Collectors.toMap(row -> row.getId().getBnruiNo(), Function.identity()));
        for (var entry : currentMap.entrySet()) {
            var next = requested.get(entry.getKey());
            var removed = deleted.get(entry.getKey());
            if ((next == null) == (removed == null)) throw new ScheduleInvalidInputException();
            int expected = next != null ? next.updateCount() : removed.updateCount();
            if (!Objects.equals(expected, entry.getValue().getUpdateCount())) throw new ScheduleUpdateConflictException();
        }
        for (var entry : requested.entrySet()) {
            if (!currentMap.containsKey(entry.getKey()) && entry.getValue().updateCount() != 0) {
                throw new ScheduleInvalidInputException();
            }
        }
        if (deleted.keySet().stream().anyMatch(no -> !currentMap.containsKey(no))) {
            throw new ScheduleInvalidInputException();
        }
    }

    private void validateDetailRequest(
            Schedule schedule,
            List<ScheduleDetail> current,
            Map<Integer, ScheduleUpdateRequest.Detail> requested,
            Map<Short, ScheduleUpdateRequest.Category> requestedCategories,
            Map<Short, ScheduleUpdateRequest.DeletedCategory> deletedCategories,
            Map<String, KenshuMember> memberMap) {
        var currentMap = current.stream().collect(Collectors.toMap(row -> row.getId().getScheduleDateNo(), Function.identity()));
        current.stream().sorted(java.util.Comparator.comparing(row -> row.getId().getScheduleDateNo()))
                .forEach(row -> entityManager.refresh(row, LockModeType.PESSIMISTIC_WRITE));
        var blockedDates = details.findTaskRemovalBlockedDates(schedule.getScheduleId());
        for (var row : current) {
            var next = requested.get(row.getId().getScheduleDateNo());
            if (row.getTaskFlg() == 1 && blockedDates.contains(row.getId().getScheduleDateNo())
                    && (next == null || !Boolean.TRUE.equals(next.taskFlg())
                        || next.content() == null || next.content().isBlank())) {
                throw new com.training.portal.schedule.exception.ScheduleTaskRemovalBlockedException();
            }
        }
        if (!currentMap.keySet().equals(requested.keySet())) throw new ScheduleInvalidInputException();
        for (var entry : currentMap.entrySet()) {
            var row = entry.getValue();
            var next = requested.get(entry.getKey());
            if (next.taskFlg() == null) throw new ScheduleInvalidInputException();
            validateTaskContent(next.content(), next.taskFlg());
            if (!Objects.equals(next.updateCount(), row.getUpdateCount())) throw new ScheduleUpdateConflictException();
            if (!requestedCategories.containsKey(next.categoryNo())) {
                if (deletedCategories.containsKey(next.categoryNo())) throw new ScheduleCategoryInUseException();
                throw new ScheduleInvalidInputException();
            }
            var instructor = memberMap.get(next.instructorEmployeeId());
            if (instructor == null || instructor.getSyainKbn() != 2) throw new ScheduleInvalidInputException();
            if (row.getScheduleDate().isBefore(schedule.getStartDate())
                    || row.getScheduleDate().isAfter(schedule.getEndDate())) {
                throw new ScheduleInvalidInputException();
            }
        }
    }

    private void syncPermissions(
            String scheduleId,
            String operationSyainId,
            List<ScheduleCanEdit> current,
            Map<String, ScheduleUpdateRequest.Permission> requested,
            Map<String, ScheduleUpdateRequest.DeletedPermission> deleted,
            Map<String, KenshuMember> memberMap) {
        var currentMap = current.stream().collect(Collectors.toMap(row -> row.getId().getSyainId(), Function.identity()));
        for (var next : requested.values()) {
            var row = currentMap.get(next.employeeId());
            if (row == null) {
                permissions.save(new ScheduleCanEdit(scheduleId, memberMap.get(next.employeeId()), next.canEdit(), operationSyainId));
            } else if ((row.getCanEdit() == 1) != next.canEdit()) {
                row.updatePermission(next.canEdit(), operationSyainId);
            }
        }
        deleted.keySet().forEach(id -> permissions.delete(currentMap.get(id)));
        permissions.flush();
    }

    private void syncCategories(
            String scheduleId,
            String operationSyainId,
            List<ScheduleBunrui> current,
            Map<Short, ScheduleUpdateRequest.Category> requested) {
        var currentMap = current.stream().collect(Collectors.toMap(row -> row.getId().getBnruiNo(), Function.identity()));
        for (var next : requested.values()) {
            var row = currentMap.get(next.categoryNo());
            if (row == null) {
                categories.save(new ScheduleBunrui(scheduleId, next.categoryNo(), next.categoryName(), operationSyainId));
            } else if (!Objects.equals(row.getBnruiName(), next.categoryName())) {
                row.rename(next.categoryName(), operationSyainId);
            }
        }
        categories.flush();
        // 削除はschedule_detailsの分類変更をflushした後に行う。
    }

    private static String normalizeContent(String content) {
        return content == null || content.isBlank() ? null : content.strip();
    }

    private static void validateTaskContent(String content, boolean task) {
        if (task && normalizeContent(content) == null) throw new ScheduleInvalidInputException();
    }

    private void syncDetails(
            String operationSyainId,
            List<ScheduleDetail> current,
            Map<Integer, ScheduleUpdateRequest.Detail> requested) {
        for (var row : current) {
            var next = requested.get(row.getId().getScheduleDateNo());
            if (!Objects.equals(row.getBnruiNo(), next.categoryNo())
                    || !Objects.equals(row.getKoshiSyainId(), next.instructorEmployeeId())
                    || !Objects.equals(row.getScheduleContent(), normalizeContent(next.content()))
                    || ((row.getTaskFlg() == 1) != next.taskFlg())) {
                row.updateContent(next.categoryNo(), next.instructorEmployeeId(), normalizeContent(next.content()), next.taskFlg(), operationSyainId);
            }
        }
        details.flush();
    }

    private void deleteCategories(
            String scheduleId,
            Map<Short, ScheduleUpdateRequest.DeletedCategory> deleted) {
        deleted.keySet().forEach(no -> categories.deleteById(new ScheduleBunruiId(scheduleId, no)));
        categories.flush();
    }

    private <K, V> Map<K, V> unique(List<V> values, Function<V, K> key) {
        var result = new HashMap<K, V>();
        for (var value : values) {
            if (result.put(key.apply(value), value) != null) throw new ScheduleInvalidInputException();
        }
        return result;
    }

    private boolean hasSqlState(Throwable error, String state) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLException sqlException && state.equals(sqlException.getSQLState())) return true;
        }
        return false;
    }
}
