package com.gestorrh.api.service;

import com.gestorrh.api.dto.PeticionTurnoDTO;
import com.gestorrh.api.dto.RespuestaTurnoDTO;
import com.gestorrh.api.entity.Empresa;
import com.gestorrh.api.entity.Turno;
import com.gestorrh.api.repository.EmpresaRepository;
import com.gestorrh.api.repository.TurnoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Servicio encargado de la lógica de negocio para la gestión del catálogo de Turnos.
 */
@Service
@RequiredArgsConstructor
public class TurnoService {

    private final TurnoRepository turnoRepository;
    private final EmpresaRepository empresaRepository;

    /**
     * Crea un nuevo tipo de turno para la empresa autenticada.
     */
    @Transactional
    public RespuestaTurnoDTO crearTurno(PeticionTurnoDTO peticion) {

        validarHoras(peticion);

        Empresa empresaLogueada = obtenerEmpresaAutenticada();

        Turno nuevoTurno = Turno.builder()
                .empresa(empresaLogueada)
                .descripcion(peticion.getDescripcion())
                .horaInicio(peticion.getHoraInicio())
                .horaFin(peticion.getHoraFin())
                .build();

        nuevoTurno = turnoRepository.save(nuevoTurno);

        return mapearARespuesta(nuevoTurno);
    }

    /**
     * Obtiene todos los turnos pertenecientes a la empresa autenticada.
     */
    @Transactional(readOnly = true)
    public List<RespuestaTurnoDTO> obtenerTurnosDeEmpresa() {

        Empresa empresaLogueada = obtenerEmpresaAutenticada();

        List<Turno> turnos = turnoRepository.findByEmpresaIdEmpresa(empresaLogueada.getIdEmpresa());

        return turnos.stream()
                .map(this::mapearARespuesta)
                .collect(Collectors.toList());
    }

    /**
     * Actualiza la información de un turno existente.
     */
    @Transactional
    public RespuestaTurnoDTO actualizarTurno(Long idTurno, PeticionTurnoDTO peticion) {

        validarHoras(peticion);
        Empresa empresaLogueada = obtenerEmpresaAutenticada();

        Turno turno = turnoRepository.findById(idTurno)
                .orElseThrow(() -> new RuntimeException("Turno no encontrado con ID: " + idTurno));

        if (!turno.getEmpresa().getIdEmpresa().equals(empresaLogueada.getIdEmpresa())) {
            throw new RuntimeException("Acceso denegado: Este turno no pertenece a tu empresa.");
        }

        turno.setDescripcion(peticion.getDescripcion());
        turno.setHoraInicio(peticion.getHoraInicio());
        turno.setHoraFin(peticion.getHoraFin());

        turno = turnoRepository.save(turno);

        return mapearARespuesta(turno);
    }

    /**
     * Elimina físicamente un turno de la base de datos.
     */
    @Transactional
    public void eliminarTurno(Long idTurno) {

        Empresa empresaLogueada = obtenerEmpresaAutenticada();

        Turno turno = turnoRepository.findById(idTurno)
                .orElseThrow(() -> new RuntimeException("Turno no encontrado con ID: " + idTurno));

        if (!turno.getEmpresa().getIdEmpresa().equals(empresaLogueada.getIdEmpresa())) {
            throw new RuntimeException("Acceso denegado: Este turno no pertenece a tu empresa.");
        }

        turnoRepository.delete(turno);
    }

    // MÉTODOS PRIVADOS AUXILIARES

    private Empresa obtenerEmpresaAutenticada() {
        String correoEmpresaAuth = SecurityContextHolder.getContext().getAuthentication().getName();
        return empresaRepository.findByEmail(correoEmpresaAuth)
                .orElseThrow(() -> new RuntimeException("Empresa no encontrada en el sistema"));
    }

    private void validarHoras(PeticionTurnoDTO peticion) {
        if (!peticion.getHoraInicio().isBefore(peticion.getHoraFin())) {
            throw new RuntimeException("Regla de negocio violada: La hora de inicio debe ser estrictamente anterior a la hora de fin.");
        }
    }

    private RespuestaTurnoDTO mapearARespuesta(Turno turno) {
        return RespuestaTurnoDTO.builder()
                .idTurno(turno.getIdTurno())
                .descripcion(turno.getDescripcion())
                .horaInicio(turno.getHoraInicio())
                .horaFin(turno.getHoraFin())
                .build();
    }
}
