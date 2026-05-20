package com.eulerity.taskmanager.meta;

import com.eulerity.taskmanager.config.SpringAiConfig.StubModeIndicator;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class MetaController {

    private final StubModeIndicator indicator;

    public MetaController(StubModeIndicator indicator) {
        this.indicator = indicator;
    }

    @GetMapping("/meta")
    public Map<String, Object> meta() {
        return Map.of("stubMode", indicator.stubMode());
    }
}
