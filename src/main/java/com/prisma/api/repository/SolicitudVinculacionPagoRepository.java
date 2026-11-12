package com.prisma.api.repository;

import com.prisma.api.entity.SolicitudVinculacionPago;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SolicitudVinculacionPagoRepository extends JpaRepository<SolicitudVinculacionPago, Long> {
    Optional<SolicitudVinculacionPago> findByState(String state);
}
