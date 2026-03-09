package com.gestorrh.api.repository;

import com.gestorrh.api.entity.Empleado;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repositorio para gestionar las operaciones de base de datos de la entidad Empleado.
 */
@Repository
public interface EmpleadoRepository extends JpaRepository<Empleado, Long> {

    /**
     * Busca un empleado por su correo electrónico (Para el Login).
     * @param email El correo electrónico a buscar.
     * @return Un Optional con el Empleado si existe.
     */
    Optional<Empleado> findByEmail(String email);

    /**
     * Navegación de relaciones.
     * Busca todos los empleados que pertenecen a una empresa en concreto.
     * * @param idEmpresa El ID de la empresa.
     * @return Lista de empleados de esa empresa.
     */
    List<Empleado> findByEmpresaIdEmpresa(Long idEmpresa);
}
