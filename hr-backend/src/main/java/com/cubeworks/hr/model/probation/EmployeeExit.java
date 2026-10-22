package com.cubeworks.hr.model.probation;

import com.cubeworks.hr.common.BaseEntity;
import com.cubeworks.hr.employee.Employee;
import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * {@code hr.employee_exit} (V13), mapped only as far as FRD-02 needs it: HR's approval of an
 * "End employment" probation outcome writes one row with {@code exit_type=PROBATION_NOT_PASSED}
 * and {@code initiated_by_role=SYSTEM} (FRD-02 §4.7). The rest of the exit lifecycle (exit
 * interview, closing, the other exit types) belongs to FRD-07, which extends this mapping.
 */
@Entity
@Table(name = "employee_exit")
public class EmployeeExit extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @Column(name = "exit_type", length = 20, nullable = false)
    private String exitType;

    /** Null when the system initiated the exit (DB CHECK with {@link #initiatedByRole} = SYSTEM). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "initiated_by")
    private Employee initiatedBy;

    @Column(name = "initiated_by_role", length = 10, nullable = false)
    private String initiatedByRole;

    @Column(name = "notice_date", nullable = false)
    private LocalDate noticeDate;

    @Column(name = "last_working_day", nullable = false)
    private LocalDate lastWorkingDay;

    @Column(name = "status", length = 12, nullable = false)
    private String status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approved_by")
    private Employee approvedBy;

    @Column(name = "approved_at")
    private OffsetDateTime approvedAt;

    /** Required no-arg constructor for JPA. */
    public EmployeeExit() {}

    public Employee getEmployee() { return employee; }
    public void setEmployee(final Employee employee) { this.employee = employee; }

    public String getExitType() { return exitType; }
    public void setExitType(final String exitType) { this.exitType = exitType; }

    public Employee getInitiatedBy() { return initiatedBy; }
    public void setInitiatedBy(final Employee initiatedBy) { this.initiatedBy = initiatedBy; }

    public String getInitiatedByRole() { return initiatedByRole; }
    public void setInitiatedByRole(final String initiatedByRole) { this.initiatedByRole = initiatedByRole; }

    public LocalDate getNoticeDate() { return noticeDate; }
    public void setNoticeDate(final LocalDate noticeDate) { this.noticeDate = noticeDate; }

    public LocalDate getLastWorkingDay() { return lastWorkingDay; }
    public void setLastWorkingDay(final LocalDate lastWorkingDay) { this.lastWorkingDay = lastWorkingDay; }

    public String getStatus() { return status; }
    public void setStatus(final String status) { this.status = status; }

    public Employee getApprovedBy() { return approvedBy; }
    public void setApprovedBy(final Employee approvedBy) { this.approvedBy = approvedBy; }

    public OffsetDateTime getApprovedAt() { return approvedAt; }
    public void setApprovedAt(final OffsetDateTime approvedAt) { this.approvedAt = approvedAt; }
}
