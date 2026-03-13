package com.gestorrh.api.repository;

import com.gestorrh.api.entity.AsignacionTurno;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface AsignacionTurnoRepository extends JpaRepository<AsignacionTurno, Long> {

    List<AsignacionTurno> findByEmpleadoIdEmpleadoAndFecha(Long idEmpleado, LocalDate fecha);

    List<AsignacionTurno> findByEmpleadoEmpresaIdEmpresaAndEmpleadoDepartamentoIgnoreCase(Long idEmpresa, String departamento);

    List<AsignacionTurno> findByEmpleadoEmpresaIdEmpresa(Long idEmpresa);

    List<AsignacionTurno> findByEmpleadoIdEmpleado(Long idEmpleado);
}
