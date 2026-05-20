package com.eulerity.taskmanager.ai;

import com.eulerity.taskmanager.ai.dto.BreakdownResponse;
import com.eulerity.taskmanager.ai.dto.SuggestRequest;
import com.eulerity.taskmanager.ai.dto.SuggestedTask;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/tasks")
public class AiTaskController {

    private final AiTaskService service;

    public AiTaskController(AiTaskService service) {
        this.service = service;
    }

    @PostMapping("/suggest")
    public SuggestedTask suggest(@Valid @RequestBody SuggestRequest req) {
        return service.suggest(req.text());
    }

    @PostMapping("/{id}/breakdown")
    public BreakdownResponse breakdown(@PathVariable long id) {
        return service.breakdown(id);
    }
}
