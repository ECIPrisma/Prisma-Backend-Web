package com.prisma.api.repository;

import com.prisma.api.entity.SuscripcionTienda;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SuscripcionTiendaRepository extends JpaRepository<SuscripcionTienda, Long> {
    Optional<SuscripcionTienda> findByTiendaId(Long tiendaId);
}
