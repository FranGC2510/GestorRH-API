package com.gestorrh.api.service;

import com.gestorrh.api.dto.turno.PeticionTurnoDTO;
import com.gestorrh.api.dto.turno.RespuestaTurnoDTO;
import com.gestorrh.api.entity.Empresa;
import com.gestorrh.api.entity.Turno;
import com.gestorrh.api.repository.EmpresaRepository;
import com.gestorrh.api.repository.TurnoRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.gestorrh.api.repository.EmpleadoRepository;

import java.time.Duration;
import java.time.LocalTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Servicio encargado de la gestión integral del catálogo de turnos dentro del sistema GestorRH.
 * <p>
 * Proporciona la lógica de negocio necesaria para la creación, consulta, actualización y
 * eliminación de los diferentes tipos de turnos que una empresa puede configurar.
 * El catálogo de turnos permite a la empresa definir sus propios horarios operativos (mañana, tarde, noche, etc.)
 * para su posterior asignación a los empleados.
 * </p>
 * <p>
 * Todos los métodos operan bajo el contexto de la empresa autenticada extraída del 
 * {@link SecurityContextHolder} para garantizar un aislamiento total de los datos entre diferentes empresas (Seguridad Multi-tenant).
 * </p>
 * 
 * @see com.gestorrh.api.entity.Turno
 * @see com.gestorrh.api.repository.TurnoRepository
 */
@Service
@RequiredArgsConstructor
public class TurnoService {

    private final TurnoRepository turnoRepository;
    private final EmpresaRepository empresaRepository;
    private final EmpleadoRepository empleadoRepository;

    /**
     * Crea un nuevo tipo de turno dentro del catálogo de la empresa actualmente autenticada.
     * <p>
     * Antes de proceder con el guardado, se validan las reglas de negocio referentes a la coherencia horaria
     * mediante el método {@link #validarHoras(PeticionTurnoDTO)}. El turno se vincula automáticamente
     * a la entidad {@link Empresa} del usuario que ha iniciado sesión.
     * </p>
     *
     * @param peticion Objeto de transferencia de datos con la descripción y el rango horario (inicio y fin) del turno.
     * @return {@link RespuestaTurnoDTO} con la información del turno recién creado, incluyendo su identificador único generado por la base de datos.
     * @throws RuntimeException Si la hora de inicio no es estrictamente anterior a la hora de fin.
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
     * Recupera la lista completa de turnos que han sido definidos por la empresa que realiza la solicitud.
     * <p>
     * La búsqueda se filtra automáticamente utilizando el identificador único de la empresa obtenido del
     * contexto de seguridad global. Se garantiza que una empresa no pueda ver los turnos configurados por otra.
     * </p>
     *
     * @return List de {@link RespuestaTurnoDTO} que contiene todos los turnos disponibles y configurados para la empresa solicitante.
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
     * Modifica los datos de un turno ya existente en la base de datos.
     * <p>
     * El sistema realiza una comprobación de propiedad: verifica que el turno pertenezca efectivamente 
     * a la empresa que está realizando la petición. Si el turno existe pero pertenece a otra empresa,
     * se deniega el acceso por motivos de seguridad.
     * </p>
     *
     * @param idTurno Identificador único del turno que se desea actualizar.
     * @param peticion Nuevos datos (descripción, hora de inicio y hora de fin) para el turno.
     * @return {@link RespuestaTurnoDTO} con la información actualizada y persistida del turno.
     * @throws EntityNotFoundException Si el turno no se encuentra en la base de datos.
     * @throws RuntimeException Si no pertenece a la empresa autenticada.
     */
    @Transactional
    public RespuestaTurnoDTO actualizarTurno(Long idTurno, PeticionTurnoDTO peticion) {

        validarHoras(peticion);
        Empresa empresaLogueada = obtenerEmpresaAutenticada();

        Turno turno = turnoRepository.findById(idTurno)
                .orElseThrow(() -> new EntityNotFoundException("Turno no encontrado con ID: " + idTurno));

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
     * Realiza el borrado físico de un turno del catálogo del sistema.
     * <p>
     * Al igual que en el proceso de actualización, se valida estrictamente que el turno sea propiedad 
     * de la empresa autenticada. Es importante tener en cuenta que el borrado puede fallar si existen
     * asignaciones de turnos vinculadas a este registro (Integridad Referencial).
     * </p>
     *
     * @param idTurno Identificador único del turno que se pretende eliminar de forma definitiva.
     * @throws EntityNotFoundException Si el turno no se encuentra en la base de datos.
     * @throws RuntimeException El acceso es denegado por falta de permisos de propiedad.
     */
    @Transactional
    public void eliminarTurno(Long idTurno) {

        Empresa empresaLogueada = obtenerEmpresaAutenticada();

        Turno turno = turnoRepository.findById(idTurno)
                .orElseThrow(() -> new EntityNotFoundException("Turno no encontrado con ID: " + idTurno));

        if (!turno.getEmpresa().getIdEmpresa().equals(empresaLogueada.getIdEmpresa())) {
            throw new RuntimeException("Acceso denegado: Este turno no pertenece a tu empresa.");
        }

        turnoRepository.delete(turno);
    }

    /**
     * Recupera la entidad Empresa asociada al usuario que ha iniciado sesión actualmente.
     * Utiliza el email extraído del SecurityContextHolder para realizar la búsqueda en el repositorio.
     *
     * @return {@link Empresa} Entidad que representa a la empresa autenticada.
     * @throws EntityNotFoundException Si no se encuentra ninguna empresa con el correo electrónico del contexto de seguridad.
     */
    private Empresa obtenerEmpresaAutenticada() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String emailAuth = auth.getName();
        boolean esEmpresa = auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_EMPRESA"));

        if (esEmpresa) {
            return empresaRepository.findByEmail(emailAuth)
                    .orElseThrow(() -> new EntityNotFoundException("Empresa no encontrada en el sistema"));
        } else {
            return empleadoRepository.findByEmail(emailAuth)
                    .orElseThrow(() -> new EntityNotFoundException("Empleado no encontrado en el sistema"))
                    .getEmpresa();
        }
    }

    /**
     * Valida las reglas de negocio horarias para un turno.
     * <p>
     * Un turno es válido si cumple una de estas dos condiciones:
     * </p>
     * <ul>
     *   <li><b>Turno diurno:</b> horaFin es estrictamente posterior a horaInicio.</li>
     *   <li><b>Turno nocturno:</b> horaInicio >= 17:00 y horaFin está en el rango [00:00, 08:00].</li>
     * </ul>
     *
     * @param peticion DTO que contiene las horas a validar.
     * @throws RuntimeException Si las horas no cumplen ninguna de las condiciones válidas.
     */
    private void validarHoras(PeticionTurnoDTO peticion) {
        LocalTime inicio = peticion.getHoraInicio();
        LocalTime fin = peticion.getHoraFin();

        boolean esDiurnoValido = fin.isAfter(inicio);

        boolean esNocturnoValido = !inicio.isBefore(LocalTime.of(16, 0))
                && !fin.isAfter(LocalTime.of(8, 0));

        if (!esDiurnoValido && !esNocturnoValido) {
            throw new RuntimeException(
                    "Regla de negocio violada: Turno inválido. Para turnos diurnos la hora de fin debe ser " +
                            "posterior a la de inicio. Para turnos nocturnos la hora de inicio debe ser a partir " +
                            "de las 17:00 y la hora de fin no puede superar las 08:00.");
        }
    }

    /**
     * Calcula los minutos reales de un turno teniendo en cuenta que puede cruzar medianoche.
     * <p>
     * Si horaFin es anterior o igual a horaInicio se interpreta que el turno termina
     * al día siguiente, sumando 24 horas al resultado negativo de Duration.between.
     * </p>
     *
     * @param horaInicio Hora de inicio del turno.
     * @param horaFin    Hora de fin del turno.
     * @return Duración real del turno expresada en minutos.
     */
    public static long calcularMinutosTurno(LocalTime horaInicio, LocalTime horaFin) {
        long minutos = Duration.between(horaInicio, horaFin).toMinutes();
        return minutos <= 0 ? minutos + 24 * 60 : minutos;
    }

    /**
     * Convierte la entidad de base de datos {@link Turno} en un objeto de respuesta DTO.
     * Se utiliza para desacoplar la capa de persistencia de la capa de presentación.
     *
     * @param turno Entidad Turno a ser mapeada.
     * @return {@link RespuestaTurnoDTO} con los datos formateados para ser devueltos por la API.
     */
    private RespuestaTurnoDTO mapearARespuesta(Turno turno) {
        return RespuestaTurnoDTO.builder()
                .idTurno(turno.getIdTurno())
                .descripcion(turno.getDescripcion())
                .horaInicio(turno.getHoraInicio())
                .horaFin(turno.getHoraFin())
                .build();
    }
}
