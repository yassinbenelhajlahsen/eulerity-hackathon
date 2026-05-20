package com.eulerity.taskmanager.meta;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// Pin api-key to empty so stubMode is deterministically true regardless of
// what's in the developer's shell environment.
// All OpenAI auto-configurations are excluded because they assert a non-empty
// api-key at context startup and are not needed for this test.
@SpringBootTest(properties = {
        "spring.ai.openai.api-key=",
        "spring.autoconfigure.exclude=" +
                "org.springframework.ai.model.openai.autoconfigure.OpenAiChatAutoConfiguration," +
                "org.springframework.ai.model.openai.autoconfigure.OpenAiAudioSpeechAutoConfiguration," +
                "org.springframework.ai.model.openai.autoconfigure.OpenAiAudioTranscriptionAutoConfiguration," +
                "org.springframework.ai.model.openai.autoconfigure.OpenAiImageAutoConfiguration," +
                "org.springframework.ai.model.openai.autoconfigure.OpenAiEmbeddingAutoConfiguration," +
                "org.springframework.ai.model.openai.autoconfigure.OpenAiModerationAutoConfiguration"
})
@AutoConfigureMockMvc
class MetaControllerTest {

    @Autowired MockMvc mvc;

    @Test
    void meta_withoutApiKey_reportsStubModeTrue() throws Exception {
        mvc.perform(get("/meta"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stubMode").value(true));
    }
}
