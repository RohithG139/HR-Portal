package com.cubeworks.hr.settings;

import com.cubeworks.hr.employee.AppRole;
import com.cubeworks.hr.employee.Employee;
import com.cubeworks.hr.employee.EmployeeRepository;
import com.cubeworks.hr.employee.Grade;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Provides the lookup data needed to render the onboarding form dropdowns (A02) and the
 * IT request form (A03).
 *
 * <h2>IT option lists (D-06)</h2>
 * <p>The IT option lists (laptops, keyboard layouts, delivery methods, accessories, accounts)
 * are a fixed, seeded list served by {@link #getItLookups()}, not a settings screen
 * (FRD-08 does not list them). Changes require a Flyway migration, not a UI edit.</p>
 */
@Service
@Transactional(readOnly = true)
public class LookupService {

    private final DepartmentRepository departmentRepository;
    private final JobTitleRepository jobTitleRepository;
    private final EmployeeRepository employeeRepository;
    private final ChecklistTemplateRepository checklistTemplateRepository;

    /**
     * Constructor injection (R-CODE-1).
     */
    public LookupService(final DepartmentRepository departmentRepository,
                         final JobTitleRepository jobTitleRepository,
                         final EmployeeRepository employeeRepository,
                         final ChecklistTemplateRepository checklistTemplateRepository) {
        this.departmentRepository = departmentRepository;
        this.jobTitleRepository = jobTitleRepository;
        this.employeeRepository = employeeRepository;
        this.checklistTemplateRepository = checklistTemplateRepository;
    }

    /**
     * Returns all reference data needed to populate the onboarding form dropdowns (A02).
     *
     * @return lookup data for departments, job titles, managers, buddies, grades,
     *         application roles and active templates
     */
    public OnboardingLookupsDto getOnboardingLookups() {
        final List<IdNameDto> departments = departmentRepository.findAll().stream()
                .map(d -> new IdNameDto(d.getId(), d.getName()))
                .collect(Collectors.toList());

        final List<IdNameDto> jobTitles = jobTitleRepository.findAll().stream()
                .map(jt -> new IdNameDto(jt.getId(), jt.getName()))
                .collect(Collectors.toList());

        // Managers: employees whose app_role = MANAGER (D-10)
        final List<IdNameDto> managers = employeeRepository.findByAppRole(AppRole.MANAGER).stream()
                .map(e -> new IdNameDto(e.getId(), e.getFirstName() + " " + e.getLastName()))
                .collect(Collectors.toList());

        // Buddies: any active (non-draft) employee can serve as buddy
        final List<IdNameDto> buddies = employeeRepository.findAll().stream()
                .filter(e -> e.getIdpSubject() != null || e.getAccountStatus() != null)
                .map(e -> new IdNameDto(e.getId(), e.getFirstName() + " " + e.getLastName()))
                .collect(Collectors.toList());

        final List<String> grades = Arrays.stream(Grade.values())
                .map(Enum::name)
                .collect(Collectors.toList());

        final List<String> appRoles = Arrays.stream(AppRole.values())
                .map(Enum::name)
                .collect(Collectors.toList());

        // Active templates: not archived
        final List<IdNameDto> templates = checklistTemplateRepository.findByArchivedAtIsNull().stream()
                .map(t -> new IdNameDto(t.getId(), t.getName()))
                .collect(Collectors.toList());

        return new OnboardingLookupsDto(departments, jobTitles, managers, buddies,
                grades, appRoles, templates);
    }

    /**
     * Returns the fixed IT option lists (D-06).
     *
     * <p>These lists are seeded and do not change at runtime. They are served via the API
     * rather than hard-coded in the frontend so that they can be updated via Flyway without
     * a frontend deployment.</p>
     *
     * @return IT option lists for laptop models, keyboard layouts, delivery methods,
     *         accessories and accounts
     */
    public ItLookupsDto getItLookups() {
        return new ItLookupsDto(
                List.of("MacBook Pro 14\"", "MacBook Pro 16\"", "MacBook Air M2",
                        "Dell XPS 13", "Dell XPS 15", "Lenovo ThinkPad X1 Carbon"),
                List.of("UK", "US QWERTY", "US AZERTY", "NL", "DE"),
                List.of("OFFICE_DAY1", "SHIP_HOME"),
                List.of("Monitor", "Keyboard & mouse", "Headset", "Docking station",
                        "Phone", "Access badge"),
                List.of("Microsoft 365 / e-mail", "Slack", "VPN", "Jira/Confluence",
                        "GitHub", "Figma", "AWS console", "Docker Desktop")
        );
    }

    // ── DTO records ───────────────────────────────────────────────────────────

    /** Simple id + name pair used in all dropdown lists. */
    public record IdNameDto(Long id, String name) {}

    /** All lookup data needed to render the onboarding form (A02). */
    public record OnboardingLookupsDto(
            List<IdNameDto> departments,
            List<IdNameDto> jobTitles,
            List<IdNameDto> managers,
            List<IdNameDto> buddies,
            List<String> grades,
            List<String> appRoles,
            List<IdNameDto> templates
    ) {}

    /** Fixed IT option lists (A03, D-06). */
    public record ItLookupsDto(
            List<String> laptops,
            List<String> keyboardLayouts,
            List<String> deliveryMethods,
            List<String> accessories,
            List<String> accounts
    ) {}
}
