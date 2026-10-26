package com.prisma.api.repository;

import com.prisma.api.entity.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {
    Page<AuditLog> findAllByOrderByTimestampDesc(Pageable pageable);
    List<AuditLog> findByAgenteIdOrderByTimestampDesc(Long agenteId);
    List<AuditLog> findByRecursoAfectadoTipoAndRecursoAfectadoId(String recursoTipo, String recursoId);
}
