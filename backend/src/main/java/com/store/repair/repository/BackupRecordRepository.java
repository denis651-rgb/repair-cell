package com.store.repair.repository;

import com.store.repair.domain.BackupEstado;
import com.store.repair.domain.BackupRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BackupRecordRepository extends JpaRepository<BackupRecord, Long> {

    Page<BackupRecord> findAllByOrderByGeneradoEnDesc(Pageable pageable);

    long countByEstado(BackupEstado estado);

    List<BackupRecord> findTop20ByEstadoOrderByGeneradoEnAsc(BackupEstado estado);

    Optional<BackupRecord> findTopByOrderByGeneradoEnDesc();
}
