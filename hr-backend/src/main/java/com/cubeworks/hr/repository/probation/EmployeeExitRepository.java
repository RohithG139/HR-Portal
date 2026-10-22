package com.cubeworks.hr.repository.probation;

import com.cubeworks.hr.model.probation.EmployeeExit;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data repository for {@link EmployeeExit} (FRD-02 writes PROBATION_NOT_PASSED only). */
public interface EmployeeExitRepository extends JpaRepository<EmployeeExit, Long> {

    /** Whether the employee already has an active exit (DB unique index {@code ux_exit_active}). */
    boolean existsByEmployeeIdAndStatusNot(Long employeeId, String status);
}
