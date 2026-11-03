package com.training.portal.schedule;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.Month;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;

import com.training.portal.schedule.exception.ScheduleInvalidInputException;

/**
 * 祝日法に基づく日本の国民の祝日・振替休日・国民の休日を判定する。
 * 春分・秋分の計算式が適用できる2000年から2150年を対象とする。
 */
@Service
public class JapaneseHolidayService {
    static final int MIN_YEAR = 2000;
    static final int MAX_YEAR = 2150;

    private final ConcurrentHashMap<Integer, Set<LocalDate>> cache = new ConcurrentHashMap<>();

    public boolean isNonWorkingDay(LocalDate date) {
        validateSupported(date);
        return date.getDayOfWeek() == DayOfWeek.SATURDAY
                || date.getDayOfWeek() == DayOfWeek.SUNDAY
                || holidays(date.getYear()).contains(date);
    }

    public List<LocalDate> findNonWorkingDates(LocalDate startDate, LocalDate endDate) {
        if (startDate == null || endDate == null || startDate.isAfter(endDate)) {
            throw new ScheduleInvalidInputException();
        }
        validateSupported(startDate);
        validateSupported(endDate);
        var result = new ArrayList<LocalDate>();
        for (var date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
            if (isNonWorkingDay(date)) result.add(date);
        }
        return List.copyOf(result);
    }

    private Set<LocalDate> holidays(int year) {
        return cache.computeIfAbsent(year, this::calculateHolidays);
    }

    private Set<LocalDate> calculateHolidays(int year) {
        var nationalHolidays = nationalHolidays(year);
        var allHolidays = new HashSet<>(nationalHolidays);

        // 祝日法第3条第3項：前後が国民の祝日で挟まれた日。
        for (var date = LocalDate.of(year, 1, 2); date.isBefore(LocalDate.of(year, 12, 31)); date = date.plusDays(1)) {
            if (!nationalHolidays.contains(date)
                    && nationalHolidays.contains(date.minusDays(1))
                    && nationalHolidays.contains(date.plusDays(1))) {
                allHolidays.add(date);
            }
        }

        // 祝日法第3条第2項：日曜日の国民の祝日の後で最も近い非祝日。
        nationalHolidays.stream().filter(date -> date.getDayOfWeek() == DayOfWeek.SUNDAY).sorted()
                .forEach(date -> {
                    var substitute = date.plusDays(1);
                    if (date.getYear() >= 2007) {
                        while (allHolidays.contains(substitute)) substitute = substitute.plusDays(1);
                        allHolidays.add(substitute);
                    } else if (!allHolidays.contains(substitute)) {
                        allHolidays.add(substitute);
                    }
                });
        return Set.copyOf(allHolidays);
    }

    private Set<LocalDate> nationalHolidays(int year) {
        var result = new HashSet<LocalDate>();
        result.add(LocalDate.of(year, Month.JANUARY, 1));
        result.add(nthMonday(year, Month.JANUARY, 2));
        result.add(LocalDate.of(year, Month.FEBRUARY, 11));
        if (year <= 2018) result.add(LocalDate.of(year, Month.DECEMBER, 23));
        if (year >= 2020) result.add(LocalDate.of(year, Month.FEBRUARY, 23));
        result.add(LocalDate.of(year, Month.MARCH, vernalEquinoxDay(year)));
        result.add(LocalDate.of(year, Month.APRIL, 29));
        result.add(LocalDate.of(year, Month.MAY, 3));
        if (year >= 2007) result.add(LocalDate.of(year, Month.MAY, 4));
        result.add(LocalDate.of(year, Month.MAY, 5));

        if (year == 2019) {
            result.add(LocalDate.of(year, Month.MAY, 1));
            result.add(LocalDate.of(year, Month.OCTOBER, 22));
        }

        if (year == 2020) {
            result.add(LocalDate.of(year, Month.JULY, 23));
            result.add(LocalDate.of(year, Month.JULY, 24));
            result.add(LocalDate.of(year, Month.AUGUST, 10));
        } else if (year == 2021) {
            result.add(LocalDate.of(year, Month.JULY, 22));
            result.add(LocalDate.of(year, Month.JULY, 23));
            result.add(LocalDate.of(year, Month.AUGUST, 8));
        } else {
            result.add(nthMonday(year, Month.JULY, 3));
            if (year >= 2016) result.add(LocalDate.of(year, Month.AUGUST, 11));
            result.add(nthMonday(year, Month.OCTOBER, 2));
        }

        result.add(nthMonday(year, Month.SEPTEMBER, 3));
        result.add(LocalDate.of(year, Month.SEPTEMBER, autumnEquinoxDay(year)));
        result.add(LocalDate.of(year, Month.NOVEMBER, 3));
        result.add(LocalDate.of(year, Month.NOVEMBER, 23));
        return result;
    }

    private LocalDate nthMonday(int year, Month month, int nth) {
        return LocalDate.of(year, month, 1).with(TemporalAdjusters.dayOfWeekInMonth(nth, DayOfWeek.MONDAY));
    }

    private int vernalEquinoxDay(int year) {
        double base = year <= 2099 ? 20.8431 : 21.8510;
        return (int) Math.floor(base + 0.242194 * (year - 1980) - Math.floor((year - 1980) / 4.0));
    }

    private int autumnEquinoxDay(int year) {
        double base = year <= 2099 ? 23.2488 : 24.2488;
        return (int) Math.floor(base + 0.242194 * (year - 1980) - Math.floor((year - 1980) / 4.0));
    }

    private void validateSupported(LocalDate date) {
        if (date.getYear() < MIN_YEAR || date.getYear() > MAX_YEAR) {
            throw new ScheduleInvalidInputException();
        }
    }
}
