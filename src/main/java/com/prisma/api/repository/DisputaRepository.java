package com.prisma.api.repository;

import com.prisma.api.entity.Disputa;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DisputaRepository extends JpaRepository<Disputa, Long> {
    Optional<Disputa> findByTicketNumero(String ticketNumero);
    List<Disputa> findByEstado(String estado);
    List<Disputa> findByPedidoId(Long pedidoId);
}
