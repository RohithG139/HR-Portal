package com.cubeworks.hr.repository.workspace;

import com.cubeworks.hr.model.workspace.ManagerChangeTask;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Spring Data repository for {@link ManagerChangeTask} (FRD-01 Wave 3, Transfer manager).
 */
public interface ManagerChangeTaskRepository extends JpaRepository<ManagerChangeTask, ManagerChangeTask.Key> {

    /**
     * Returns every ticked-task link for a given manager change — read by the scheduler when
     * applying a change, to find which {@code engine_task} rows need their owner reassigned.
     *
     * @param changeId the owning {@link com.cubeworks.hr.model.workspace.ManagerChange} id
     * @return the linked task rows
     */
    List<ManagerChangeTask> findByChangeId(Long changeId);
}
