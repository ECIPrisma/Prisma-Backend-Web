package com.prisma.api.repository;

import com.prisma.api.entity.SuscripcionProveedor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SuscripcionProveedorRepository extends JpaRepository<SuscripcionProveedor, Long> {
    Optional<SuscripcionProveedor> findByProveedorId(Long proveedorId);
}
