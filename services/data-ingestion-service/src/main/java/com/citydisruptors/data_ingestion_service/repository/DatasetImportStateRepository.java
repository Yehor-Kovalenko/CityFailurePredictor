package com.citydisruptors.data_ingestion_service.repository;

import com.citydisruptors.data_ingestion_service.entity.DatasetImportState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface DatasetImportStateRepository extends JpaRepository<DatasetImportState, Long> {
    Optional<DatasetImportState> findByFileName(String fileName);
}
