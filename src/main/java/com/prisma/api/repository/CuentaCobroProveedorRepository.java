package com.prisma.api.repository;

import com.prisma.api.entity.CuentaCobroProveedor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CuentaCobroProveedorRepository extends JpaRepository<CuentaCobroProveedor, Long> {
    Optional<CuentaCobroProveedor> findByProveedorId(Long proveedorId);
    boolean existsByProveedorId(Long proveedorId);
}
