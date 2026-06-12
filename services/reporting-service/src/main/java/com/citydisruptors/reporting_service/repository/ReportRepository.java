package com.citydisruptors.reporting_service.repository;

import com.citydisruptors.reporting_service.entity.Report;
import com.citydisruptors.reporting_service.entity.ReportType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ReportRepository extends JpaRepository<Report, UUID> {

    List<Report> findByTypeOrderByCreatedAtDesc(ReportType type);
}
