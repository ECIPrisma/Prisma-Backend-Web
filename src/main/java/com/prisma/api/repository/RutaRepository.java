package com.prisma.api.repository;

import com.prisma.api.entity.Ruta;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface RutaRepository extends JpaRepository<Ruta, Long> {
    Optional<Ruta> findByCodigoRuta(String codigoRuta);
    @org.springframework.data.jpa.repository.Query("SELECT DISTINCT r FROM Ruta r LEFT JOIN FETCH r.camion LEFT JOIN FETCH r.proveedor WHERE r.repartidor.id = :repartidorId AND r.fechaProgramada = :fechaProgramada")
    List<Ruta> findByRepartidorIdAndFechaProgramada(@org.springframework.data.repository.query.Param("repartidorId") Long repartidorId, @org.springframework.data.repository.query.Param("fechaProgramada") LocalDate fechaProgramada);
    List<Ruta> findByProveedorIdAndFechaProgramada(Long proveedorId, LocalDate fechaProgramada);
    List<Ruta> findByProveedorId(Long proveedorId);
    List<Ruta> findByProveedorIdOrderByFechaProgramadaDesc(Long proveedorId);
    @org.springframework.data.jpa.repository.Query("SELECT DISTINCT r FROM Ruta r LEFT JOIN FETCH r.camion LEFT JOIN FETCH r.proveedor WHERE r.repartidor.id = :repartidorId ORDER BY r.fechaProgramada DESC")
    List<Ruta> findByRepartidorIdOrderByFechaProgramadaDesc(@org.springframework.data.repository.query.Param("repartidorId") Long repartidorId);
    List<Ruta> findByRepartidorId(Long repartidorId);
}
