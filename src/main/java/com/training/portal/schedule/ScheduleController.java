package com.training.portal.schedule;

import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.http.ResponseEntity;
import org.springframework.web.util.UriComponentsBuilder;

import jakarta.validation.Valid;
import jakarta.validation.constraints.PositiveOrZero;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.training.portal.schedule.dto.ScheduleListResponse;
import com.training.portal.schedule.dto.ScheduleMemberOptionResponse;
import com.training.portal.schedule.dto.ScheduleDetailResponse;
import com.training.portal.schedule.dto.ScheduleCreateRequest;
import com.training.portal.schedule.dto.ScheduleNonWorkingDatesResponse;
import com.training.portal.schedule.dto.ScheduleUpdateRequest;
import com.training.portal.schedule.dto.SchedulePinUpdateRequest;
import com.training.portal.schedule.dto.SchedulePinUpdateResponse;

@RestController
@RequestMapping("/api/schedules")
public class ScheduleController {
    private final ScheduleService service;

    public ScheduleController(ScheduleService service) {
        this.service = service;
    }

    @GetMapping
    public List<ScheduleListResponse> findAccessibleSchedules(Authentication authentication) {
        return service.findAccessibleSchedules(authentication.getName());
    }

    @GetMapping("/non-working-dates")
    public ScheduleNonWorkingDatesResponse getNonWorkingDates(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return new ScheduleNonWorkingDatesResponse(service.findNonWorkingDates(startDate, endDate));
    }

    @GetMapping("/member-options")
    public List<ScheduleMemberOptionResponse> findMemberOptions() {
        return service.findMemberOptions();
    }

    @GetMapping("/{scheduleId}")
    public ScheduleDetailResponse findAccessibleScheduleDetail(
            @PathVariable String scheduleId,
            Authentication authentication) {
        return service.findAccessibleScheduleDetail(scheduleId, authentication.getName());
    }

    @PostMapping
    public ResponseEntity<ScheduleDetailResponse> createSchedule(
            @Valid @RequestBody ScheduleCreateRequest request,
            Authentication authentication) {
        var response = service.createSchedule(request, authentication.getName());
        var location = UriComponentsBuilder.fromPath("/api/schedules/{scheduleId}")
                .encode().buildAndExpand(response.scheduleId()).toUri();
        return ResponseEntity.created(location).body(response);
    }

    @PutMapping("/{scheduleId}")
    public ScheduleDetailResponse updateSchedule(
            @PathVariable String scheduleId,
            @Valid @RequestBody ScheduleUpdateRequest request,
            Authentication authentication) {
        return service.updateSchedule(scheduleId, request, authentication.getName());
    }

    @PutMapping("/{scheduleId}/pin")
    public SchedulePinUpdateResponse updatePin(
            @PathVariable String scheduleId,
            @Valid @RequestBody SchedulePinUpdateRequest request,
            Authentication authentication) {
        return service.updatePin(scheduleId, request, authentication.getName());
    }

    @DeleteMapping("/{scheduleId}")
    public ResponseEntity<Void> deleteSchedule(
            @PathVariable String scheduleId,
            @RequestParam @PositiveOrZero Integer scheduleUpdateCount,
            Authentication authentication) {
        service.deleteSchedule(scheduleId, scheduleUpdateCount, authentication.getName());
        return ResponseEntity.noContent().build();
    }
}
