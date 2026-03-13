package com.gestorrh.api.repository;

import com.gestorrh.api.entity.Turno;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TurnoRepository extends JpaRepository<Turno, Long> {

    /**
     * Recupera todos los turnos configurados por una empresa en concreto.
     * Esencial para mantener el aislamiento Multi-Tenant.
     */
    List<Turno> findByEmpresaIdEmpresa(Long idEmpresa);
}
