package com.eulerity.taskmanager.task.dto;

import com.eulerity.taskmanager.task.Priority;
import com.eulerity.taskmanager.task.Status;
import com.eulerity.taskmanager.task.Task;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MutationResponseTest {

    @Test
    void of_withDemotedNull_wrapsSavedAndKeepsDemotedNull() {
        Task saved = new Task();
        saved.setId(1L);
        saved.setTitle("new high");
        saved.setPriority(Priority.HIGH);
        saved.setStatus(Status.TODO);

        MutationResponse r = MutationResponse.of(saved, null);

        assertThat(r.task()).isNotNull();
        assertThat(r.task().id()).isEqualTo(1L);
        assertThat(r.task().priority()).isEqualTo(Priority.HIGH);
        assertThat(r.demoted()).isNull();
    }

    @Test
    void of_withDemoted_wrapsBoth() {
        Task saved = new Task();
        saved.setId(2L);
        saved.setTitle("new high");
        saved.setPriority(Priority.HIGH);
        saved.setStatus(Status.TODO);

        Task demoted = new Task();
        demoted.setId(99L);
        demoted.setTitle("oldest");
        demoted.setPriority(Priority.MEDIUM);
        demoted.setStatus(Status.TODO);

        MutationResponse r = MutationResponse.of(saved, demoted);

        assertThat(r.task().id()).isEqualTo(2L);
        assertThat(r.demoted()).isNotNull();
        assertThat(r.demoted().id()).isEqualTo(99L);
        assertThat(r.demoted().priority()).isEqualTo(Priority.MEDIUM);
    }
}
