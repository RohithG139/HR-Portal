package com.cubeworks.hr.settings;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Spring Data repository for {@link JobTitle}.
 *
 * <p>The derived finder {@link #findByName} supports duplicate-name validation before
 * an INSERT touches the DB unique constraint.</p>
 */
public interface JobTitleRepository extends JpaRepository<JobTitle, Long> {

    /**
     * Finds a job title by its unique name.
     *
     * @param name the exact job title name to look up (case-sensitive)
     * @return the matching job title, if any
     */
    Optional<JobTitle> findByName(String name);
}
