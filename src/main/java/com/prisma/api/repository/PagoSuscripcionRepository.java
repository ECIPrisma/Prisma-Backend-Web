package com.prisma.api.repository;

import com.prisma.api.entity.PagoSuscripcion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PagoSuscripcionRepository extends JpaRepository<PagoSuscripcion, Long> {
    Optional<PagoSuscripcion> findByReferencia(String referencia);
    List<PagoSuscripcion> findByProveedorIdOrderByCreadoEnDescIdDesc(Long proveedorId);
}
