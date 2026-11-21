package com.prisma.api.repository;

import com.prisma.api.entity.Notificacion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NotificacionRepository extends JpaRepository<Notificacion, Long> {
    List<Notificacion> findByUsuarioIdOrderByCreadoEnDesc(Long usuarioId);
    long countByUsuarioIdAndLeidaFalse(Long usuarioId);
}
