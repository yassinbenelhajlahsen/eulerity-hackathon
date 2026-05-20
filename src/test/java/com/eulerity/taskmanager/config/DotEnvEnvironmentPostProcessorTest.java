package com.eulerity.taskmanager.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DotEnvEnvironmentPostProcessorTest {

    @Test
    void parsesKeyValuePairsIgnoringCommentsAndBlanks(@TempDir Path tmp) throws Exception {
        Path env = tmp.resolve(".env");
        Files.writeString(env, """
                # comment
                OPENAI_API_KEY=sk-test
                FOO=bar

                export QUOTED="with spaces"
                'SINGLE'=also-quoted
                ALSO_SINGLE='another value'
                BAD_LINE_NO_EQUALS
                =EMPTY_KEY
                """);

        Map<String, Object> parsed = DotEnvEnvironmentPostProcessor.parse(env);

        assertThat(parsed).containsEntry("OPENAI_API_KEY", "sk-test");
        assertThat(parsed).containsEntry("FOO", "bar");
        assertThat(parsed).containsEntry("QUOTED", "with spaces");
        assertThat(parsed).containsEntry("ALSO_SINGLE", "another value");
        // BAD_LINE_NO_EQUALS has no '=' → skipped.
        assertThat(parsed).doesNotContainKey("BAD_LINE_NO_EQUALS");
        // '=EMPTY_KEY' has eq at index 0 → skipped (eq <= 0 guard).
        assertThat(parsed.keySet()).noneMatch(k -> k.isEmpty());
    }

    @Test
    void missingFile_returnsEmptyMap(@TempDir Path tmp) {
        Path env = tmp.resolve("does-not-exist.env");
        Map<String, Object> parsed = DotEnvEnvironmentPostProcessor.parse(env);
        assertThat(parsed).isEmpty();
    }
}
