package com.prisma.api.repository;

import com.prisma.api.entity.Producto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProductoRepository extends JpaRepository<Producto, Long> {
    List<Producto> findByProveedorId(Long proveedorId);
    List<Producto> findByProveedorIdAndEstado(Long proveedorId, String estado);
    List<Producto> findByProveedorIdAndEstadoNot(Long proveedorId, String estado);
    List<Producto> findByCategoriaIdAndEstado(Long categoriaId, String estado);
    Optional<Producto> findByProveedorIdAndSku(Long proveedorId, String sku);
}
