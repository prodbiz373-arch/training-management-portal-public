package com.training.portal.schedule.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import com.training.portal.schedule.ScheduleCanEdit;
import com.training.portal.schedule.ScheduleDetail;

public record ScheduleDetailResponse(
        String scheduleId,
        String scheduleName,
        LocalDate startDate,
        LocalDate endDate,
        boolean excludeHolidays,
        boolean canEdit,
        boolean pinned,
        Integer scheduleUpdateCount,
        Integer permissionUpdateCount,
        List<Permission> permissions,
        List<Category> categories,
        List<Detail> details) {

    public static ScheduleDetailResponse from(
            ScheduleCanEdit currentPermission,
            List<ScheduleCanEdit> permissions,
            List<com.training.portal.schedule.ScheduleBunrui> categories,
            List<ScheduleDetail> details,
            Set<String> reviewerEmployeeIds,
            Set<String> commentEmployeeIds,
            Set<Integer> taskRemovalBlockedDates) {
        var schedule = currentPermission.getSchedule();
        return new ScheduleDetailResponse(
                schedule.getScheduleId(),
                schedule.getScheduleName(),
                schedule.getStartDate(),
                schedule.getEndDate(),
                currentPermission.getSchedule().getNoholFlg() == 1,
                currentPermission.getCanEdit() == 1,
                currentPermission.getPinFlg() == 1,
                schedule.getUpdateCount(),
                currentPermission.getUpdateCount(),
                permissions.stream().map(permission -> Permission.from(
                        permission, reviewerEmployeeIds, commentEmployeeIds)).toList(),
                categories.stream().map(category -> new Category(
                        category.getId().getBnruiNo(), category.getBnruiName(), category.getUpdateCount())).toList(),
                details.stream().map(detail -> Detail.from(detail, taskRemovalBlockedDates)).toList());
    }

    public record Permission(
            String employeeId,
            String name,
            String nameKana,
            short syainKbn,
            boolean canEdit,
            boolean pinned,
            Integer updateCount,
            boolean usedAsReviewer,
            boolean hasComments) {
        private static Permission from(
                ScheduleCanEdit permission,
                Set<String> reviewerEmployeeIds,
                Set<String> commentEmployeeIds) {
            var member = permission.getMember();
            return new Permission(
                    permission.getId().getSyainId(),
                    member.getName(),
                    member.getNameKana(),
                    permission.getSyainKbn(),
                    permission.getCanEdit() == 1,
                    permission.getPinFlg() == 1,
                    permission.getUpdateCount(),
                    reviewerEmployeeIds.contains(permission.getId().getSyainId()),
                    commentEmployeeIds.contains(permission.getId().getSyainId()));
        }
    }

    public record Category(short categoryNo, String categoryName, Integer updateCount) {
    }

    public record Detail(
            Integer scheduleDateNo,
            LocalDate scheduleDate,
            short categoryNo,
            String categoryName,
            String instructorEmployeeId,
            String instructorName,
            String content,
            boolean taskFlg,
            boolean taskRemovalBlocked,
            Integer updateCount) {
        private static Detail from(ScheduleDetail detail, Set<Integer> blockedDates) {
            return new Detail(
                    detail.getId().getScheduleDateNo(),
                    detail.getScheduleDate(),
                    detail.getBnruiNo(),
                    detail.getBunrui().getBnruiName(),
                    detail.getKoshiSyainId(),
                    detail.getInstructor().getName(),
                    detail.getScheduleContent(),
                    detail.getTaskFlg() == 1,
                    blockedDates.contains(detail.getId().getScheduleDateNo()),
                    detail.getUpdateCount());
        }
    }
}
