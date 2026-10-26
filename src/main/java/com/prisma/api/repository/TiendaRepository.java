package com.prisma.api.repository;

import com.prisma.api.entity.Tienda;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TiendaRepository extends JpaRepository<Tienda, Long> {
    Optional<Tienda> findByNitOCedula(String nitOCedula);
    Optional<Tienda> findFirstByTenderoId(Long tenderoId);
    Optional<Tienda> findByTenderoId(Long tenderoId);
    List<Tienda> findAllByTenderoId(Long tenderoId);
    List<Tienda> findByCiudadAndBarrio(String ciudad, String barrio);
}
