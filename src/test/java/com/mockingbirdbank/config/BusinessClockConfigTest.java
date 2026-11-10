package com.mockingbirdbank.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZoneId;
import org.junit.jupiter.api.Test;

class BusinessClockConfigTest {

    @Test
    void businessClockRunsOnNewYorkTime() {
        assertThat(new BusinessClockConfig().businessClock().getZone())
                .isEqualTo(ZoneId.of("America/New_York"));
    }
}
