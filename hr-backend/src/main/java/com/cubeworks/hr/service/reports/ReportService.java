package com.cubeworks.hr.service.reports;

import com.cubeworks.hr.dto.reports.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.sql.Types;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Service that executes all FRD-08 report queries (C31–C47).
 *
 * <h2>Design decisions</h2>
 * <ul>
 *   <li>Uses {@link NamedParameterJdbcTemplate} rather than JPA/JPQL because reports
 *       read from PostgreSQL views whose aggregate SQL is too complex for JPQL, and
 *       no JPA entities are needed for read-only projections.</li>
 *   <li>Every report method returns a typed {@code List<T>} that is the single source
 *       used by both the JSON endpoint and the Excel export — the same rows are always
 *       identical on-screen and in the download (spec §7, no separate export code path).</li>
 *   <li>Sort parameters are mapped through an allow-list map before being interpolated
 *       into the SQL string, preventing SQL injection via an arbitrary column name.</li>
 * </ul>
 *
 * <h2>Schema prefix</h2>
 * <p>All queries use the {@code hr.} prefix on table/view names because the
 * {@code JdbcTemplate} connection does not set {@code search_path = hr} automatically
 * (unlike Flyway migrations, which Flyway sets before running).</p>
 */
@Service
public class ReportService {

    private static final Logger log = LoggerFactory.getLogger(ReportService.class);

    private final NamedParameterJdbcTemplate jdbc;

    /**
     * Constructor injection (R-CODE-1).
     *
     * @param jdbc the named-parameter JDBC template for safe parameterised queries
     */
    public ReportService(final NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // ── Allow-listed sort-column maps (prevent SQL injection) ─────────────────

    /**
     * Bench report: camelCase param name → SQL column reference.
     * The default sort is {@code bench_since ASC} (longest on bench first).
     */
    private static final Map<String, String> BENCH_SORT = Map.of(
            "employeeName", "b.employee_name",
            "benchSince",   "b.bench_since",
            "daysOnBench",  "b.days_on_bench"
    );

    /**
     * Allocation report: camelCase param name → SQL column reference.
     */
    private static final Map<String, String> ALLOC_SORT = Map.of(
            "employeeName",       "va.employee_name",
            "totalAllocationPct", "va.total_allocation_pct",
            "allocationStatus",   "va.allocation_status"
    );

    /**
     * Appraisal status report: camelCase param name → SQL column reference.
     */
    private static final Map<String, String> APR_SORT = Map.of(
            "employeeName", "vas.employee_name"
    );

    /**
     * PIP report: camelCase param name → SQL column reference.
     */
    private static final Map<String, String> PIP_SORT = Map.of(
            "employeeName", "vp.employee_name",
            "status",       "vp.status",
            "startDate",    "vp.start_date"
    );

    /**
     * Onboarding report: camelCase param name → SQL column reference.
     */
    private static final Map<String, String> ONB_SORT = Map.of(
            "employeeName", "e.first_name || ' ' || e.last_name",
            "startDate",    "c.start_date"
    );

    // ── Summary (C31) ─────────────────────────────────────────────────────────

    /**
     * C31 — Composes the four KPI tiles for the reports overview screen (S7).
     *
     * <p>Each number is computed by the same underlying query method as the
     * corresponding detail report, so summary and detail are always consistent.</p>
     *
     * @return summary DTO with headcount, bench, appraisal completion and asset counts
     */
    public SummaryDto getSummary() {
        final long headcount = countHeadcount();
        final long bench = countBench();
        final BigDecimal appraisalRate = computeAppraisalCompletionRate();
        final long assets = countUnreturnedAssets();
        final long headcountChange = countHeadcountChangeVsLastMonth();
        final long billable = countBillableStaff();
        final Integer roundYear = findOpenAppraisalRoundYear();
        final long assetExEmployees = countUnreturnedAssetExEmployees();
        log.debug("Summary: headcount={} bench={} appraisalRate={} assets={} headcountChange={} "
                        + "billable={} roundYear={} assetExEmployees={}",
                headcount, bench, appraisalRate, assets, headcountChange, billable, roundYear,
                assetExEmployees);
        return new SummaryDto(headcount, bench, appraisalRate, assets,
                headcountChange, billable, roundYear, assetExEmployees);
    }

    /**
     * Net headcount change over the last month: joiners (first contract starts in the window)
     * minus leavers (non-rejected exit whose last working day falls in the window).
     *
     * @return joiners minus leavers in {@code (today - 1 month, today]}; may be negative
     */
    private long countHeadcountChangeVsLastMonth() {
        final Long result = jdbc.queryForObject("""
                SELECT
                  (SELECT count(*) FROM hr.employee e
                   WHERE (SELECT min(c.start_date) FROM hr.contract c WHERE c.employee_id = e.id)
                         BETWEEN (current_date - interval '1 month')::date + 1 AND current_date)
                  -
                  (SELECT count(DISTINCT ee.employee_id) FROM hr.employee_exit ee
                   WHERE ee.status <> 'REJECTED'
                     AND ee.last_working_day BETWEEN (current_date - interval '1 month')::date + 1
                                                 AND current_date)
                """, Map.of(), Long.class);
        return result != null ? result : 0L;
    }

    /**
     * Billable staff — the allocation pool used by the Project Allocation report
     * ({@code v_employee_allocation}: everyone not ARCHIVED or OFFBOARDING).
     *
     * @return number of employees in the allocation pool
     */
    private long countBillableStaff() {
        final Long result = jdbc.queryForObject(
                "SELECT count(*) FROM hr.v_employee_allocation", Map.of(), Long.class);
        return result != null ? result : 0L;
    }

    /**
     * Year of the open appraisal round, taken from its review period end.
     *
     * @return the year, or {@code null} when no round is open
     */
    private Integer findOpenAppraisalRoundYear() {
        return jdbc.queryForObject(
                "SELECT max(extract(year FROM review_period_end))::int "
                        + "FROM hr.appraisal_round WHERE status = 'OPEN'",
                Map.of(), Integer.class);
    }

    /**
     * Distinct employees holding unreturned assets whose last working day has passed.
     *
     * @return number of ex-employees with unreturned assets
     */
    private long countUnreturnedAssetExEmployees() {
        final Long result = jdbc.queryForObject(
                "SELECT count(DISTINCT employee_id) FROM hr.v_unreturned_assets "
                        + "WHERE last_working_day < current_date",
                Map.of(), Long.class);
        return result != null ? result : 0L;
    }

    /**
     * Count of non-ARCHIVED employees — shared by getSummary and headcount query.
     *
     * @return total headcount
     */
    private long countHeadcount() {
        final Long result = jdbc.queryForObject(
                "SELECT count(*) FROM hr.employee WHERE stage <> 'ARCHIVED'",
                Map.of(), Long.class);
        return result != null ? result : 0L;
    }

    /**
     * Count of bench employees — shared by getSummary and the bench detail query.
     *
     * @return number of employees with no active project assignment
     */
    private long countBench() {
        final Long result = jdbc.queryForObject(
                "SELECT count(*) FROM hr.v_bench", Map.of(), Long.class);
        return result != null ? result : 0L;
    }

    /**
     * Appraisal completion rate for the current open round.
     * Returns 0 when there is no open round or no appraisals.
     *
     * @return percentage of finalised appraisals, scale 1
     */
    private BigDecimal computeAppraisalCompletionRate() {
        final Map<String, Object> row = jdbc.queryForMap(
                "SELECT count(*) filter (where a.status = 'FINALISED') as finalised, "
                        + "count(*) as total "
                        + "FROM hr.appraisal a "
                        + "JOIN hr.appraisal_round ar ON ar.id = a.round_id AND ar.status = 'OPEN'",
                Map.of());
        final long total     = toLong(row.get("total"));
        final long finalised = toLong(row.get("finalised"));
        if (total == 0) {
            return BigDecimal.ZERO.setScale(1, RoundingMode.HALF_UP);
        }
        return BigDecimal.valueOf(finalised * 100.0 / total)
                .setScale(1, RoundingMode.HALF_UP);
    }

    /**
     * Count of unreturned assets — shared by getSummary and the unreturned assets detail query.
     *
     * @return number of assets in RETURN_DUE or NOT_RETURNED status
     */
    private long countUnreturnedAssets() {
        final Long result = jdbc.queryForObject(
                "SELECT count(*) FROM hr.v_unreturned_assets", Map.of(), Long.class);
        return result != null ? result : 0L;
    }

    // ── Headcount (C32) ───────────────────────────────────────────────────────

    /**
     * C32 — Headcount report: one row per department with joiners, leavers and
     * contract-type breakdown.
     *
     * <p>This is the single source used by both the JSON endpoint and the export (spec §7).</p>
     *
     * @param departmentId optional filter — when non-null, returns only that department's row
     * @return list of headcount rows ordered by department name
     */
    public List<HeadcountReportRow> getHeadcount(final Long departmentId) {
        // addValue with explicit Types.BIGINT so PostgreSQL JDBC can resolve the null type
        // (PSQLException: could not determine data type of parameter $1 otherwise)
        final MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("departmentId", departmentId, Types.BIGINT);

        final String sql = """
                SELECT
                  d.id AS department_id,
                  d.name AS department_name,
                  count(DISTINCT e.id) FILTER (WHERE e.stage <> 'ARCHIVED') AS employee_count,
                  count(DISTINCT e.id) FILTER (
                    WHERE e.stage <> 'ARCHIVED'
                      AND EXISTS (
                        SELECT 1 FROM hr.contract c2
                        WHERE c2.employee_id = e.id
                          AND c2.is_current
                          AND extract(year FROM c2.start_date) = extract(year FROM current_date)
                      )
                  ) AS joiners_this_year,
                  count(DISTINCT ee.id) FILTER (
                    WHERE extract(year FROM ee.last_working_day) = extract(year FROM current_date)
                      AND ee.status <> 'REJECTED'
                  ) AS leavers_this_year,
                  count(DISTINCT e.id) FILTER (
                    WHERE e.stage <> 'ARCHIVED'
                      AND EXISTS (
                        SELECT 1 FROM hr.contract c2
                        WHERE c2.employee_id = e.id
                          AND c2.is_current
                          AND c2.contract_type = 'PERMANENT'
                      )
                  ) AS permanent_count,
                  count(DISTINCT e.id) FILTER (
                    WHERE e.stage <> 'ARCHIVED'
                      AND EXISTS (
                        SELECT 1 FROM hr.contract c2
                        WHERE c2.employee_id = e.id
                          AND c2.is_current
                          AND c2.contract_type = 'FIXED_TERM'
                      )
                  ) AS fixed_term_count
                FROM hr.department d
                LEFT JOIN hr.employee e ON e.department_id = d.id
                LEFT JOIN hr.employee_exit ee ON ee.employee_id = e.id
                WHERE (:departmentId IS NULL OR d.id = :departmentId)
                GROUP BY d.id, d.name
                ORDER BY d.name
                """;

        return jdbc.query(sql, params, (rs, rowNum) -> new HeadcountReportRow(
                rs.getLong("department_id"),
                rs.getString("department_name"),
                rs.getLong("employee_count"),
                rs.getLong("joiners_this_year"),
                rs.getLong("leavers_this_year"),
                rs.getLong("permanent_count"),
                rs.getLong("fixed_term_count")
        ));
    }

    // ── Bench (C34) ───────────────────────────────────────────────────────────

    /**
     * C34 — Bench report: employees with no active project assignment.
     *
     * <p>Reads {@code v_bench}.  Filters and search are applied as parameterised predicates;
     * the sort column is validated against {@link #BENCH_SORT} before interpolation.</p>
     *
     * @param managerId optional filter — when non-null, limits to employees under that manager
     * @param q         optional free-text search on employee name (case-insensitive)
     * @param sort      allowed: {@code employeeName}, {@code benchSince}, {@code daysOnBench}
     *                  (default: {@code benchSince})
     * @param dir       {@code asc} (default) or {@code desc}
     * @return list of bench rows
     */
    public List<BenchReportRow> getBench(final Long managerId, final String q,
                                          final String sort, final String dir) {
        final String sortCol = safeSort(sort, BENCH_SORT, "b.bench_since");
        final String sortDir = safeDir(dir);

        // Explicit JDBC types prevent PSQLException when either param is null
        final MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("managerId", managerId, Types.BIGINT)
                .addValue("q", q, Types.VARCHAR);

        // N+1 prevention: v_bench uses correlated subqueries internally — no further JOINs needed
        final String sql = "SELECT b.employee_id, b.employee_name, b.job_title, "
                + "b.manager_id, b.manager_name, b.bench_since, b.days_on_bench, b.last_project_name "
                + "FROM hr.v_bench b "
                + "WHERE (:managerId IS NULL OR b.manager_id = :managerId) "
                + "  AND (:q IS NULL OR b.employee_name ILIKE '%' || :q || '%') "
                + "ORDER BY " + sortCol + " " + sortDir + " NULLS LAST";

        return jdbc.query(sql, params, (rs, rowNum) -> new BenchReportRow(
                rs.getLong("employee_id"),
                rs.getString("employee_name"),
                rs.getString("job_title"),
                nullableLong(rs, "manager_id"),
                rs.getString("manager_name"),
                toLocalDate(rs.getDate("bench_since")),
                nullableInt(rs, "days_on_bench"),
                rs.getString("last_project_name")
        ));
    }

    // ── Allocation (C36) ──────────────────────────────────────────────────────

    /**
     * C36 — Project Allocation report: current allocation per active employee.
     *
     * <p>Reads {@code v_employee_allocation}.</p>
     *
     * @param status optional filter on {@code allocation_status}
     *               (ON_BENCH / PARTLY_ALLOCATED / FULLY_ALLOCATED / OVER_ALLOCATED)
     * @param q      optional free-text search on employee name
     * @param sort   allowed: {@code employeeName}, {@code totalAllocationPct}, {@code allocationStatus}
     * @param dir    {@code asc} or {@code desc}
     * @return list of allocation rows
     */
    public List<AllocationReportRow> getAllocation(final String status, final String q,
                                                    final String sort, final String dir) {
        final String sortCol = safeSort(sort, ALLOC_SORT, "va.employee_name");
        final String sortDir = safeDir(dir);

        // Explicit JDBC types prevent PSQLException when either param is null
        final MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("status", status, Types.VARCHAR)
                .addValue("q", q, Types.VARCHAR);

        final String sql = "SELECT va.employee_id, va.employee_name, va.job_title, "
                + "va.total_allocation_pct, va.allocation_status, va.projects "
                + "FROM hr.v_employee_allocation va "
                + "WHERE (:status IS NULL OR va.allocation_status = :status) "
                + "  AND (:q IS NULL OR va.employee_name ILIKE '%' || :q || '%') "
                + "ORDER BY " + sortCol + " " + sortDir;

        return jdbc.query(sql, params, (rs, rowNum) -> new AllocationReportRow(
                rs.getLong("employee_id"),
                rs.getString("employee_name"),
                rs.getString("job_title"),
                rs.getInt("total_allocation_pct"),
                rs.getString("allocation_status"),
                rs.getString("projects")
        ));
    }

    // ── Appraisal status (C38) ────────────────────────────────────────────────

    /**
     * C38 — Appraisal Status report: per-employee review progress in the open round.
     *
     * <p>Reads {@code v_appraisal_status}.  Returns an empty list when no round is open.</p>
     *
     * @param managerId optional filter on the employee's line manager
     * @param result    optional filter on the appraisal outcome (PROMOTED/NO_CHANGE/UNDER_PERFORMING)
     * @param q         optional free-text search on employee name
     * @param sort      allowed: {@code employeeName}
     * @param dir       {@code asc} or {@code desc}
     * @return list of appraisal status rows
     */
    public List<AppraisalStatusReportRow> getAppraisalStatus(final Long managerId,
                                                               final String result,
                                                               final String q,
                                                               final String sort,
                                                               final String dir) {
        final String sortCol = safeSort(sort, APR_SORT, "vas.employee_name");
        final String sortDir = safeDir(dir);

        // Explicit JDBC types prevent PSQLException when any param is null
        final MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("managerId", managerId, Types.BIGINT)
                .addValue("result", result, Types.VARCHAR)
                .addValue("q", q, Types.VARCHAR);

        final String sql = "SELECT vas.appraisal_id, vas.employee_id, vas.employee_name, "
                + "vas.manager_id, vas.manager_name, vas.self_status, "
                + "vas.peers_submitted, vas.peers_total, vas.manager_review_status, "
                + "vas.manager_review_outcome, vas.client_feedback_avg, vas.client_feedback_count, "
                + "vas.result, vas.appraisal_status "
                + "FROM hr.v_appraisal_status vas "
                + "WHERE (:managerId IS NULL OR vas.manager_id = :managerId) "
                + "  AND (:result IS NULL OR vas.result = :result) "
                + "  AND (:q IS NULL OR vas.employee_name ILIKE '%' || :q || '%') "
                + "ORDER BY " + sortCol + " " + sortDir;

        return jdbc.query(sql, params, (rs, rowNum) -> new AppraisalStatusReportRow(
                rs.getLong("appraisal_id"),
                rs.getLong("employee_id"),
                rs.getString("employee_name"),
                nullableLong(rs, "manager_id"),
                rs.getString("manager_name"),
                rs.getString("self_status"),
                rs.getLong("peers_submitted"),
                rs.getLong("peers_total"),
                rs.getString("manager_review_status"),
                rs.getString("manager_review_outcome"),
                toBigDecimal(rs, "client_feedback_avg"),
                rs.getLong("client_feedback_count"),
                rs.getString("result"),
                rs.getString("appraisal_status")
        ));
    }

    // ── PIPs (C40) ────────────────────────────────────────────────────────────

    /**
     * C40 — PIPs report: active and pending improvement plans.
     *
     * <p>Reads {@code v_pip}.</p>
     *
     * @param status optional filter on PIP status
     * @param sort   allowed: {@code employeeName}, {@code status}, {@code startDate}
     * @param dir    {@code asc} or {@code desc}
     * @return list of PIP rows
     */
    public List<PipReportRow> getPips(final String status, final String sort, final String dir) {
        final String sortCol = safeSort(sort, PIP_SORT, "vp.start_date");
        final String sortDir = safeDir(dir);

        // Explicit JDBC type prevents PSQLException when status is null
        final MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("status", status, Types.VARCHAR);

        final String sql = "SELECT vp.pip_id, vp.employee_id, vp.employee_name, "
                + "vp.manager_id, vp.manager_name, vp.status, vp.start_date, "
                + "vp.final_review_date, vp.days_left, vp.extension_count "
                + "FROM hr.v_pip vp "
                + "WHERE (:status IS NULL OR vp.status = :status) "
                + "ORDER BY " + sortCol + " " + sortDir + " NULLS LAST";

        return jdbc.query(sql, params, (rs, rowNum) -> new PipReportRow(
                rs.getLong("pip_id"),
                rs.getLong("employee_id"),
                rs.getString("employee_name"),
                nullableLong(rs, "manager_id"),
                rs.getString("manager_name"),
                rs.getString("status"),
                toLocalDate(rs.getDate("start_date")),
                toLocalDate(rs.getDate("final_review_date")),
                nullableInt(rs, "days_left"),
                rs.getLong("extension_count")
        ));
    }

    // ── Onboarding (C42) ──────────────────────────────────────────────────────

    /**
     * C42 — Onboarding report: employees currently in IN_PROGRESS onboarding.
     *
     * <p>Reads {@code onboarding}, {@code checklist_item} and {@code checklist_template}
     * directly (no view needed — the aggregate is simple enough to compute inline).</p>
     *
     * @param sort allowed: {@code employeeName}, {@code startDate}
     * @param dir  {@code asc} or {@code desc}
     * @return list of onboarding rows
     */
    public List<OnboardingReportRow> getOnboarding(final String sort, final String dir) {
        // JOIN FETCH-equivalent: single query with GROUP BY avoiding N+1 per-employee queries
        final String sortCol = safeSort(sort, ONB_SORT, "c.start_date");
        final String sortDir = safeDir(dir);

        final String sql = "SELECT "
                + "  e.id AS employee_id, "
                + "  (e.first_name || ' ' || e.last_name) AS employee_name, "
                + "  c.start_date, "
                + "  ct.name AS template_name, "
                + "  o.it_status, "
                + "  count(ci.id) AS total_items, "
                + "  count(ci.id) FILTER (WHERE ci.status = 'DONE') AS done_items, "
                + "  count(ci.id) FILTER (WHERE ci.status = 'OPEN') AS open_items "
                + "FROM hr.onboarding o "
                + "JOIN hr.employee e ON e.id = o.employee_id "
                + "JOIN hr.checklist_template ct ON ct.id = o.template_id "
                + "LEFT JOIN hr.contract c ON c.employee_id = e.id AND c.is_current "
                + "LEFT JOIN hr.checklist_item ci ON ci.employee_id = e.id AND ci.phase = 'ONBOARDING' "
                + "WHERE o.status = 'IN_PROGRESS' "
                + "GROUP BY e.id, e.first_name, e.last_name, c.start_date, ct.name, o.it_status "
                + "ORDER BY " + sortCol + " " + sortDir + " NULLS LAST";

        return jdbc.query(sql, Map.of(), (rs, rowNum) -> {
            final long totalItems = rs.getLong("total_items");
            final long doneItems  = rs.getLong("done_items");
            final long openItems  = rs.getLong("open_items");
            final int pct = totalItems == 0 ? 0
                    : (int) Math.round((double) doneItems / totalItems * 100.0);
            return new OnboardingReportRow(
                    rs.getLong("employee_id"),
                    rs.getString("employee_name"),
                    toLocalDate(rs.getDate("start_date")),
                    rs.getString("template_name"),
                    pct,
                    rs.getString("it_status"),
                    openItems
            );
        });
    }

    // ── Contracts & Probation (C44) ───────────────────────────────────────────

    /**
     * C44 — Contracts &amp; Probation report: events in the next 90 days.
     *
     * <p>Reads {@code v_upcoming_dates}.  The default sort is by event date ascending.</p>
     *
     * @param type optional filter on {@code event_type} (PROBATION / CONTRACT / PIP)
     * @return list of upcoming date rows
     */
    public List<ContractsProbationReportRow> getContractsProbation(final String type) {
        // Explicit JDBC type prevents PSQLException when type is null (UNION ALL view)
        final MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("type", type, Types.VARCHAR);

        final String sql = "SELECT vud.employee_id, vud.employee_name, "
                + "vud.event_type, vud.event_date, vud.days_until, "
                + "vud.owner_role, vud.event_status "
                + "FROM hr.v_upcoming_dates vud "
                + "WHERE (:type IS NULL OR vud.event_type = :type) "
                + "ORDER BY vud.event_date ASC";

        return jdbc.query(sql, params, (rs, rowNum) -> new ContractsProbationReportRow(
                rs.getLong("employee_id"),
                rs.getString("employee_name"),
                rs.getString("event_type"),
                toLocalDate(rs.getDate("event_date")),
                rs.getInt("days_until"),
                rs.getString("owner_role"),
                rs.getString("event_status")
        ));
    }

    // ── Unreturned assets (C46) ───────────────────────────────────────────────

    /**
     * C46 — Unreturned Assets report: assets in RETURN_DUE or NOT_RETURNED status.
     *
     * <p>Reads {@code v_unreturned_assets}.  The default sort is by last working day
     * (most urgent first); assets without an exit record appear last.</p>
     *
     * @param status optional filter on asset status (RETURN_DUE / NOT_RETURNED)
     * @return list of unreturned asset rows
     */
    public List<UnreturnedAssetsReportRow> getUnreturnedAssets(final String status) {
        // Explicit JDBC type prevents PSQLException when status is null
        final MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("status", status, Types.VARCHAR);

        final String sql = "SELECT vua.asset_id, vua.asset_number, vua.asset_description, "
                + "vua.employee_id, vua.employee_name, vua.last_working_day, vua.asset_status "
                + "FROM hr.v_unreturned_assets vua "
                + "WHERE (:status IS NULL OR vua.asset_status = :status) "
                + "ORDER BY vua.last_working_day ASC NULLS LAST";

        return jdbc.query(sql, params, (rs, rowNum) -> new UnreturnedAssetsReportRow(
                rs.getLong("asset_id"),
                rs.getString("asset_number"),
                rs.getString("asset_description"),
                nullableLong(rs, "employee_id"),
                rs.getString("employee_name"),
                toLocalDate(rs.getDate("last_working_day")),
                rs.getString("asset_status")
        ));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Validates the sort direction to prevent SQL injection; defaults to ASC.
     *
     * @param dir the raw direction string from the query parameter
     * @return {@code "DESC"} when the input is {@code "desc"} (case-insensitive), {@code "ASC"} otherwise
     */
    private static String safeDir(final String dir) {
        return "desc".equalsIgnoreCase(dir) ? "DESC" : "ASC";
    }

    /**
     * Validates the sort column against an allow-list, guarding against both SQL injection
     * and {@link NullPointerException} when the query parameter is absent.
     *
     * <p>{@link java.util.Map#of} returns an immutable map whose {@code getOrDefault} throws
     * {@link NullPointerException} when the key argument is {@code null}.  This wrapper
     * checks for null before delegating to the map.</p>
     *
     * @param sort       the raw sort parameter from the request (may be null)
     * @param allowList  map of camelCase param names to safe SQL column expressions
     * @param defaultCol the SQL column to use when {@code sort} is null or not in the allow-list
     * @return a safe SQL column expression
     */
    private static String safeSort(final String sort, final Map<String, String> allowList,
                                    final String defaultCol) {
        if (sort == null) {
            return defaultCol;
        }
        return allowList.getOrDefault(sort, defaultCol);
    }

    /**
     * Converts a nullable {@link java.sql.Date} to {@link LocalDate}.
     *
     * @param sqlDate the JDBC date, possibly null
     * @return the LocalDate, or null if the input is null
     */
    private static LocalDate toLocalDate(final Date sqlDate) {
        return sqlDate != null ? sqlDate.toLocalDate() : null;
    }

    /**
     * Reads a nullable {@code bigint} column from the current ResultSet row.
     *
     * @param rs         the result set
     * @param columnName the column to read
     * @return the Long value, or null if the SQL value is NULL
     * @throws java.sql.SQLException on JDBC error
     */
    private static Long nullableLong(final java.sql.ResultSet rs,
                                      final String columnName) throws java.sql.SQLException {
        final long value = rs.getLong(columnName);
        return rs.wasNull() ? null : value;
    }

    /**
     * Reads a nullable {@code int} column from the current ResultSet row.
     *
     * @param rs         the result set
     * @param columnName the column to read
     * @return the Integer value, or null if the SQL value is NULL
     * @throws java.sql.SQLException on JDBC error
     */
    private static Integer nullableInt(final java.sql.ResultSet rs,
                                        final String columnName) throws java.sql.SQLException {
        final int value = rs.getInt(columnName);
        return rs.wasNull() ? null : value;
    }

    /**
     * Reads a nullable {@code numeric/decimal} column from the current ResultSet row.
     *
     * @param rs         the result set
     * @param columnName the column to read
     * @return the BigDecimal value, or null if the SQL value is NULL
     * @throws java.sql.SQLException on JDBC error
     */
    private static BigDecimal toBigDecimal(final java.sql.ResultSet rs,
                                            final String columnName) throws java.sql.SQLException {
        return rs.getBigDecimal(columnName);
    }

    /**
     * Converts any numeric object from a JDBC queryForMap result to {@code long}.
     *
     * @param obj the object from the result map (may be Integer, Long, BigDecimal, etc.)
     * @return the long value, or 0 if null
     */
    private static long toLong(final Object obj) {
        if (obj == null) return 0L;
        if (obj instanceof Number n) return n.longValue();
        return 0L;
    }
}
