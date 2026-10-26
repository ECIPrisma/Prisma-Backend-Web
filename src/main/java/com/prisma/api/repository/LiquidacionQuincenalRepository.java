package com.prisma.api.repository;

import com.prisma.api.entity.LiquidacionQuincenal;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LiquidacionQuincenalRepository extends JpaRepository<LiquidacionQuincenal, Long> {
    List<LiquidacionQuincenal> findByProveedorIdOrderByGeneradoEnDesc(Long proveedorId);
}
