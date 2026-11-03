package com.training.portal.schedule;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JapaneseHolidayServiceTest {
    private final JapaneseHolidayService holidays = new JapaneseHolidayService();

    @Test
    void calculatesSportsDayWeekendsAndOrdinaryWeekdayIn2026() {
        assertThat(holidays.isNonWorkingDay(LocalDate.of(2026, 10, 10))).isTrue();
        assertThat(holidays.isNonWorkingDay(LocalDate.of(2026, 10, 11))).isTrue();
        assertThat(holidays.isNonWorkingDay(LocalDate.of(2026, 10, 12))).isTrue();
        assertThat(holidays.isNonWorkingDay(LocalDate.of(2026, 10, 13))).isFalse();
    }

    @Test
    void calculatesSubstituteHolidayAndCitizensHoliday() {
        assertThat(holidays.isNonWorkingDay(LocalDate.of(2026, 5, 6))).isTrue();
        assertThat(holidays.isNonWorkingDay(LocalDate.of(2026, 9, 22))).isTrue();
        assertThat(holidays.isNonWorkingDay(LocalDate.of(2027, 3, 22))).isTrue();
    }

    @Test
    void returnsAllNonWorkingDatesForRequestedRange() {
        assertThat(holidays.findNonWorkingDates(
                LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 16)))
                .containsExactly(
                        LocalDate.of(2026, 10, 10),
                        LocalDate.of(2026, 10, 11),
                        LocalDate.of(2026, 10, 12));
    }
}
