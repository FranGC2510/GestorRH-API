package com.gestorrh.api.repository;

import com.gestorrh.api.entity.Ausencia;
import com.gestorrh.api.entity.enums.EstadoAusencia;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AusenciaRepository extends JpaRepository<Ausencia, Long> {

    List<Ausencia> findByEmpleadoEmpresaIdEmpresa(Long idEmpresa);
    List<Ausencia> findByEmpleadoEmpresaIdEmpresaAndEstado(Long idEmpresa, EstadoAusencia estado);

    List<Ausencia> findByEmpleadoEmpresaIdEmpresaAndEmpleadoDepartamentoIgnoreCase(Long idEmpresa, String departamento);
    List<Ausencia> findByEmpleadoEmpresaIdEmpresaAndEmpleadoDepartamentoIgnoreCaseAndEstado(Long idEmpresa, String departamento, EstadoAusencia estado);

    List<Ausencia> findByEmpleadoIdEmpleado(Long idEmpleado);
    List<Ausencia> findByEmpleadoIdEmpleadoAndEstado(Long idEmpleado, EstadoAusencia estado);

    @Query("SELECT COUNT(a) > 0 FROM Ausencia a WHERE a.empleado.idEmpleado = :idEmpleado AND a.estado = :estado AND :fecha BETWEEN a.fechaInicio AND a.fechaFin")
    boolean tieneAusenciaAprobadaEnFecha(
            @Param("idEmpleado") Long idEmpleado,
            @Param("estado") EstadoAusencia estado,
            @Param("fecha") java.time.LocalDate fecha
    );

    @Query("SELECT a FROM Ausencia a WHERE a.empleado.idEmpleado = :idEmpleado " +
            "AND a.estado IN :estados " +
            "AND (a.fechaInicio <= :fechaFin AND a.fechaFin >= :fechaInicio)")
    List<Ausencia> findAusenciasSolapadas(
            @org.springframework.data.repository.query.Param("idEmpleado") Long idEmpleado,
            @org.springframework.data.repository.query.Param("estados") java.util.List<EstadoAusencia> estados,
            @org.springframework.data.repository.query.Param("fechaInicio") java.time.LocalDate fechaInicio,
            @org.springframework.data.repository.query.Param("fechaFin") java.time.LocalDate fechaFin
    );
}
