package com.prisma.api.repository;

import com.prisma.api.entity.ConfiguracionSistema;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ConfiguracionSistemaRepository extends JpaRepository<ConfiguracionSistema, String> {
    Optional<ConfiguracionSistema> findByClave(String clave);
    List<ConfiguracionSistema> findByCategoria(String categoria);
}
