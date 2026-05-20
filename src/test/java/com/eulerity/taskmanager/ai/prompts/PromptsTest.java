package com.eulerity.taskmanager.ai.prompts;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class PromptsTest {

    @Test
    void suggestSystem_includesTodaysDateSoModelCanResolveRelativePhrases() {
        LocalDate today = LocalDate.of(2026, 5, 20);
        String prompt = Prompts.suggestSystem(today);

        assertThat(prompt).contains("2026-05-20");
        assertThat(prompt).containsIgnoringCase("relative date");
    }

    @Test
    void suggestSystem_stillContainsSentinelInstructions() {
        String prompt = Prompts.suggestSystem(LocalDate.now());
        assertThat(prompt).contains(Prompts.USER_INPUT_BEGIN);
        assertThat(prompt).contains(Prompts.USER_INPUT_END);
    }

    @Test
    void suggestSystem_instructsModelToNullDueDateForMalformedInput() {
        String prompt = Prompts.suggestSystem(LocalDate.now());
        assertThat(prompt).containsIgnoringCase("null");
        assertThat(prompt).containsIgnoringCase("malformed");
    }
}
