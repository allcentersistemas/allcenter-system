package com.allcenter.modulesystem.repository;

import com.allcenter.modulesystem.model.BackupRun;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BackupRunRepository extends JpaRepository<BackupRun, Long> {

    List<BackupRun> findTop50ByOrderByStartedAtDesc();
}
