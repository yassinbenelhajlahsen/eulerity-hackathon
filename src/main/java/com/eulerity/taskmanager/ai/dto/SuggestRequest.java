package com.eulerity.taskmanager.ai.dto;

import jakarta.validation.constraints.NotBlank;

public record SuggestRequest(@NotBlank String text) {}
