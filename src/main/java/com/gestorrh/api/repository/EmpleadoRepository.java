package com.gestorrh.api.repository;

import com.gestorrh.api.dto.estadisticas.DatoGraficoDTO;
import com.gestorrh.api.entity.Empleado;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Repositorio para gestionar las operaciones de base de datos de la entidad {@link Empleado}.
 * Proporciona métodos para la gestión de usuarios/empleados y estadísticas relacionadas.
 */
@Repository
public interface EmpleadoRepository extends JpaRepository<Empleado, Long> {

    /**
     * Busca un empleado por su correo electrónico.
     * Utilizado principalmente durante el proceso de autenticación.
     * @param email El correo electrónico a buscar.
     * @return Un Optional con el Empleado si existe.
     */
    Optional<Empleado> findByEmail(String email);

    /**
     * Busca todos los empleados que pertenecen a una empresa específica.
     * @param idEmpresa El ID de la empresa.
     * @return Lista de empleados de esa empresa.
     */
    List<Empleado> findByEmpresaIdEmpresa(Long idEmpresa);

    /**
     * Busca todos los empleados de una empresa en un departamento específico.
     * Usado por el rol SUPERVISOR para obtener únicamente los empleados de su ámbito.
     * @param idEmpresa El ID de la empresa.
     * @param departamento El nombre del departamento (ignora mayúsculas/minúsculas).
     * @return Lista de empleados del departamento indicado.
     */
    List<Empleado> findByEmpresaIdEmpresaAndDepartamentoIgnoreCase(Long idEmpresa, String departamento);

    /**
     * Desactiva automáticamente a los empleados cuya fecha de baja de contrato ya ha pasado.
     * @return El número de empleados que han sido desactivados.
     */
    @Transactional
    @Modifying
    @Query("UPDATE Empleado e SET e.activo = false WHERE e.activo = true AND e.fechaBajaContrato IS NOT NULL AND e.fechaBajaContrato <= CURRENT_DATE")
    int desactivarEmpleadosConContratoExpirado();

    /**
     * Obtiene el conteo de empleados agrupados por departamento para una empresa.
     * @param idEmpresa El ID de la empresa.
     * @return Lista de DTOs con el nombre del departamento y la cantidad de empleados.
     */
    @Query("SELECT new com.gestorrh.api.dto.estadisticas.DatoGraficoDTO(COALESCE(e.departamento, 'Sin asignar'), COUNT(e)) " +
            "FROM Empleado e " +
            "WHERE e.empresa.idEmpresa = :idEmpresa " +
            "GROUP BY e.departamento")
    List<DatoGraficoDTO> contarEmpleadosPorDepartamento(@Param("idEmpresa") Long idEmpresa);

    /**
     * Cuenta el número total de empleados activos de una empresa.
     * @param idEmpresa El ID de la empresa.
     * @return Cantidad total de empleados en estado activo.
     */
    @Query("SELECT COUNT(e) " +
            "FROM Empleado e " +
            "WHERE e.empresa.idEmpresa = :idEmpresa " +
            "AND e.activo = true")
    Long contarTotalEmpleadosActivos(@Param("idEmpresa") Long idEmpresa);
}
