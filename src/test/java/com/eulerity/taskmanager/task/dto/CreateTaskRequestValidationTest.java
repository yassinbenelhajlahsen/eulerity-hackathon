package com.eulerity.taskmanager.task.dto;

import com.eulerity.taskmanager.task.Priority;
import com.eulerity.taskmanager.task.Status;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class CreateTaskRequestValidationTest {

    private static Validator validator;

    @BeforeAll
    static void setUp() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    @Test
    void blankTitle_failsValidation() {
        CreateTaskRequest req = new CreateTaskRequest(
                "  ", "desc", null, Priority.LOW, Status.TODO);
        Set<ConstraintViolation<CreateTaskRequest>> v = validator.validate(req);
        assertThat(v).anyMatch(c -> c.getPropertyPath().toString().equals("title"));
    }

    @Test
    void validRequest_passesValidation() {
        CreateTaskRequest req = new CreateTaskRequest(
                "Buy milk", null, null, Priority.LOW, null);
        Set<ConstraintViolation<CreateTaskRequest>> v = validator.validate(req);
        assertThat(v).isEmpty();
    }

    @Test
    void nullPriority_failsValidation() {
        CreateTaskRequest req = new CreateTaskRequest(
                "Buy milk", null, null, null, Status.TODO);
        Set<ConstraintViolation<CreateTaskRequest>> v = validator.validate(req);
        assertThat(v).anyMatch(c -> c.getPropertyPath().toString().equals("priority"));
    }
}
