package com.gestorrh.api.repository;

import com.gestorrh.api.entity.Fichaje;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

/**
 * Repositorio para gestionar las operaciones de base de datos de la entidad {@link Fichaje}.
 * Registra y consulta las entradas y salidas de los empleados, así como sus incidencias.
 */
@Repository
public interface FichajeRepository extends JpaRepository<Fichaje, Long> {

    /**
     * Busca fichajes activos de un empleado (sin hora de salida) para una fecha específica.
     * Útil para detectar si un empleado tiene un fichaje pendiente de cerrar.
     * @param idEmpleado El ID del empleado.
     * @param fecha La fecha a consultar.
     * @return Lista de fichajes abiertos.
     */
    List<Fichaje> findByEmpleadoIdEmpleadoAndFechaAndHoraSalidaIsNull(Long idEmpleado, LocalDate fecha);

    /**
     * Recupera el historial de fichajes de un empleado en un rango de fechas.
     * @param idEmpleado El ID del empleado.
     * @param fechaInicio Fecha de inicio del periodo.
     * @param fechaFin Fecha de fin del periodo.
     * @return Lista de fichajes realizados en el periodo.
     */
    List<Fichaje> findByEmpleadoIdEmpleadoAndFechaBetween(Long idEmpleado, LocalDate fechaInicio, LocalDate fechaFin);

    /**
     * Recupera todos los fichajes de una empresa en un rango de fechas.
     * <p>
     * Utiliza JOIN FETCH para traer al empleado en la misma consulta y evitar el problema N+1.
     * </p>
     * @param idEmpresa El ID de la empresa.
     * @param fechaInicio Fecha de inicio del periodo.
     * @param fechaFin Fecha de fin del periodo.
     * @return Lista de fichajes de todos los empleados de la empresa en ese periodo.
     */
    @Query("SELECT f FROM Fichaje f " +
            "JOIN FETCH f.empleado e " +
            "WHERE e.empresa.idEmpresa = :idEmpresa " +
            "AND f.fecha BETWEEN :fechaInicio AND :fechaFin")
    List<Fichaje> findByEmpleadoEmpresaIdEmpresaAndFechaBetween(
            @Param("idEmpresa") Long idEmpresa,
            @Param("fechaInicio") LocalDate fechaInicio,
            @Param("fechaFin") LocalDate fechaFin);

    /**
     * Obtiene un ranking de los empleados con más retrasos (fichajes marcados "Retraso en la entrada").
     * @param idEmpresa El ID de la empresa.
     * @return Lista de arrays de objetos conteniendo el nombre del empleado y el número de retrasos.
     */
    @Query("SELECT f.empleado.nombre, COUNT(f) " +
            "FROM Fichaje f " +
            "WHERE f.empleado.empresa.idEmpresa = :idEmpresa " +
            "AND f.incidencias LIKE '%Retraso en la entrada%' " +
            "GROUP BY f.empleado.nombre " +
            "ORDER BY COUNT(f) DESC")
    List<Object[]> obtenerTopRetrasos(@Param("idEmpresa") Long idEmpresa);

    /**
     * Devuelve los IDs de asignación que tienen al menos un fichaje asociado,
     * filtrado por una lista de IDs candidatos.
     * Se usa antes de eliminar asignaciones al aprobar una ausencia para evitar
     * violar la restricción de clave foránea entre fichaje y asignacion_turno.
     *
     * @param ids Lista de IDs de asignación a comprobar.
     * @return Lista de IDs que tienen fichajes asociados y no pueden eliminarse.
     */
    @Query("SELECT DISTINCT f.asignacion.idAsignacion FROM Fichaje f WHERE f.asignacion.idAsignacion IN :ids")
    List<Long> findIdAsignacionesConFichajes(@Param("ids") List<Long> ids);
}
