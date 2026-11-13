package com.prisma.api.repository;

import com.prisma.api.entity.RastreoCamion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RastreoCamionRepository extends JpaRepository<RastreoCamion, Long> {

    Optional<RastreoCamion> findTopByRutaIdOrderByTimestampDesc(Long rutaId);

    Optional<RastreoCamion> findTopByCamionIdOrderByTimestampDesc(Long camionId);

    List<RastreoCamion> findByRutaIdOrderByTimestampAsc(Long rutaId);
}
