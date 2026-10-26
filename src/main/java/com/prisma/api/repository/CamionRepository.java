package com.prisma.api.repository;

import com.prisma.api.entity.Camion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CamionRepository extends JpaRepository<Camion, Long> {
    List<Camion> findByProveedorId(Long proveedorId);
}
