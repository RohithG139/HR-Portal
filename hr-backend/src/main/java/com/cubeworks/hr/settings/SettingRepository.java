package com.cubeworks.hr.settings;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Spring Data repository for {@link Setting}.
 *
 * <p>The primary key is the {@code code} string, so {@code findById("BENCH_CHECK")} is
 * the standard point-lookup. Category-scoped listing supports the settings screens which
 * show timers and task-due settings in separate UI sections.</p>
 */
public interface SettingRepository extends JpaRepository<Setting, String> {

    /**
     * Returns all settings in a given category, e.g. all TASK_DUE settings for the
     * "Task due dates" section of the Settings screen.
     *
     * @param category the category to filter by
     * @return all settings with the specified category, in no guaranteed order
     */
    List<Setting> findByCategory(SettingCategory category);
}
