package com.gestorrh.api.service;

import com.gestorrh.api.dto.empleado.*;
import com.gestorrh.api.entity.Empleado;
import com.gestorrh.api.entity.Empresa;
import com.gestorrh.api.repository.EmpleadoRepository;
import com.gestorrh.api.repository.EmpresaRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Servicio encargado de la gestión integral del capital humano de las organizaciones.
 * <p>
 * Proporciona la lógica de negocio necesaria para administrar el ciclo de vida del empleado:
 * desde el alta inicial con generación de credenciales temporales, hasta la gestión de bajas,
 * readmisiones y el mantenimiento del perfil profesional.
 * </p>
 * <p>
 * Implementa políticas estrictas de seguridad multi-tenant para asegurar que los datos
 * del personal sean accesibles únicamente por la empresa propietaria o el propio empleado.
 * </p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EmpleadoService {

    private final EmpleadoRepository empleadoRepository;
    private final EmpresaRepository empresaRepository;
    private final PasswordEncoder codificadorPassword;

    /**
     * Registra un nuevo empleado en la organización y le vincula automáticamente a la empresa solicitante.
     * <p>
     * El proceso de alta automatiza la seguridad inicial: genera una contraseña aleatoria de 8 caracteres
     * mediante {@link UUID} para el primer inicio de sesión del trabajador.
     * </p>
     *
     * @param peticion Objeto {@link PeticionCrearEmpleadoDTO} con la información del nuevo trabajador.
     * @return {@link RespuestaCrearEmpleadoDTO} que incluye la identificación del sistema y la contraseña temporal generada.
     * @throws RuntimeException Si el correo electrónico ya existe en la base de datos (Restricción de unicidad).
     */
    @Transactional
    public RespuestaCrearEmpleadoDTO crearEmpleado(PeticionCrearEmpleadoDTO peticion) {

        String correoEmpresaAuth = SecurityContextHolder.getContext().getAuthentication().getName();

        Empresa empresa = empresaRepository.findByEmail(correoEmpresaAuth)
                .orElseThrow(() -> new EntityNotFoundException("Empresa no encontrada en el sistema"));

        if (empleadoRepository.findByEmail(peticion.getEmail()).isPresent()) {
            log.warn("Empresa '{}' intentó crear empleado fallido: El correo '{}' ya existe.", correoEmpresaAuth, peticion.getEmail());
            throw new RuntimeException("Ya existe un usuario con el correo: " + peticion.getEmail());
        }

        String contrasenaPlana = UUID.randomUUID().toString().substring(0, 8);
        String contrasenaEncriptada = codificadorPassword.encode(contrasenaPlana);

        Empleado nuevoEmpleado = Empleado.builder()
                .empresa(empresa)
                .email(peticion.getEmail())
                .password(contrasenaEncriptada)
                .nombre(peticion.getNombre())
                .apellidos(peticion.getApellidos())
                .telefono(peticion.getTelefono())
                .puesto(peticion.getPuesto())
                .departamento(peticion.getDepartamento())
                .rol(peticion.getRol())
                .activo(true)
                .build();

        nuevoEmpleado = empleadoRepository.save(nuevoEmpleado);

        log.info("ALTA DE EMPLEADO: La empresa '{}' ha registrado a '{}' con rol [{}]", empresa.getEmail(), nuevoEmpleado.getEmail(), nuevoEmpleado.getRol());

        return RespuestaCrearEmpleadoDTO.builder()
                .idEmpleado(nuevoEmpleado.getIdEmpleado())
                .nombre(nuevoEmpleado.getNombre())
                .apellidos(nuevoEmpleado.getApellidos())
                .email(nuevoEmpleado.getEmail())
                .rol(nuevoEmpleado.getRol())
                .passwordGenerada(contrasenaPlana)
                .build();
    }

    /**
     * Recupera la lista de empleados según el rol del usuario autenticado.
     * <p>
     * La EMPRESA obtiene todos los empleados de su organización.
     * El SUPERVISOR obtiene únicamente los empleados de su mismo departamento,
     * aplicando el filtro automáticamente desde el SecurityContext.
     * Si el supervisor no tiene departamento asignado, se devuelve lista vacía.
     * </p>
     *
     * @return List de {@link RespuestaEmpleadoDTO} con el personal visible para el usuario autenticado.
     */
    @Transactional(readOnly = true)
    public List<RespuestaEmpleadoDTO> obtenerEmpleadosDeEmpresa() {

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String emailAuth = auth.getName();
        boolean esEmpresa = auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_EMPRESA"));

        List<Empleado> empleados;

        if (esEmpresa) {
            Empresa empresa = empresaRepository.findByEmail(emailAuth)
                    .orElseThrow(() -> new EntityNotFoundException("Empresa no encontrada"));
            empleados = empleadoRepository.findByEmpresaIdEmpresa(empresa.getIdEmpresa());
        } else {
            Empleado supervisor = empleadoRepository.findByEmail(emailAuth)
                    .orElseThrow(() -> new EntityNotFoundException("Supervisor no encontrado"));

            String departamento = supervisor.getDepartamento();
            if (departamento == null || departamento.isBlank()) {
                log.warn("El supervisor '{}' no tiene departamento asignado. Se devuelve lista vacía.", emailAuth);
                return List.of();
            }

            empleados = empleadoRepository.findByEmpresaIdEmpresaAndDepartamentoIgnoreCase(
                    supervisor.getEmpresa().getIdEmpresa(), departamento);
        }

        return empleados.stream().map(emp -> {
            boolean esRealmenteActivo = emp.getActivo() &&
                    (emp.getFechaBajaContrato() == null || emp.getFechaBajaContrato().isAfter(LocalDate.now()));

            return RespuestaEmpleadoDTO.builder()
                    .idEmpleado(emp.getIdEmpleado())
                    .email(emp.getEmail())
                    .nombre(emp.getNombre())
                    .apellidos(emp.getApellidos())
                    .telefono(emp.getTelefono())
                    .puesto(emp.getPuesto())
                    .departamento(emp.getDepartamento())
                    .rol(emp.getRol())
                    .activo(esRealmenteActivo)
                    .fechaBajaContrato(emp.getFechaBajaContrato())
                    .build();
        }).collect(Collectors.toList());
    }

    /**
     * Actualiza la información profesional y de contacto de un empleado.
     * <p>
     * Verifica estrictamente que el empleado pertenezca a la empresa que solicita la modificación.
     * </p>
     *
     * @param idEmpleado Identificador único del empleado a modificar.
     * @param peticion DTO con los nuevos datos (nombre, apellidos, puesto, rol, etc.).
     * @return {@link RespuestaEmpleadoDTO} con los datos actualizados.
     * @throws RuntimeException Si el empleado no existe o pertenece a otra empresa (Violación de seguridad).
     */
    @Transactional
    public RespuestaEmpleadoDTO actualizarEmpleado(Long idEmpleado, PeticionActualizarEmpleadoDTO peticion) {

        String correoEmpresaAuth = SecurityContextHolder.getContext().getAuthentication().getName();
        Empresa empresaLogueada = empresaRepository.findByEmail(correoEmpresaAuth)
                .orElseThrow(() -> new EntityNotFoundException("Empresa no encontrada"));

        Empleado empleado = empleadoRepository.findById(idEmpleado)
                .orElseThrow(() -> new EntityNotFoundException("Empleado no encontrado con ID: " + idEmpleado));

        if (!empleado.getEmpresa().getIdEmpresa().equals(empresaLogueada.getIdEmpresa())) {
            log.warn("VIOLACIÓN DE SEGURIDAD: La empresa '{}' intentó modificar al empleado ID {}, que pertenece a otra empresa.", correoEmpresaAuth, idEmpleado);
            throw new RuntimeException("Acceso denegado: Este empleado no pertenece a tu empresa.");
        }

        empleado.setNombre(peticion.getNombre());
        empleado.setApellidos(peticion.getApellidos());
        empleado.setTelefono(peticion.getTelefono());
        empleado.setPuesto(peticion.getPuesto());
        empleado.setDepartamento(peticion.getDepartamento());
        empleado.setRol(peticion.getRol());

        empleado = empleadoRepository.save(empleado);

        log.info("La empresa '{}' ha ACTUALIZADO los datos del empleado ID {} ({})", correoEmpresaAuth, idEmpleado, empleado.getEmail());

        boolean esRealmenteActivo = empleado.getActivo() &&
                (empleado.getFechaBajaContrato() == null || empleado.getFechaBajaContrato().isAfter(LocalDate.now()));

        return RespuestaEmpleadoDTO.builder()
                .idEmpleado(empleado.getIdEmpleado())
                .email(empleado.getEmail())
                .nombre(empleado.getNombre())
                .apellidos(empleado.getApellidos())
                .telefono(empleado.getTelefono())
                .puesto(empleado.getPuesto())
                .departamento(empleado.getDepartamento())
                .rol(empleado.getRol())
                .activo(esRealmenteActivo)
                .fechaBajaContrato(empleado.getFechaBajaContrato())
                .build();
    }

    /**
     * Tramita la baja laboral de un empleado en el sistema.
     * <p>
     * Si la fecha de baja es igual o anterior a hoy, el empleado se marca como inactivo inmediatamente.
     * Si es posterior, la baja queda programada para su procesamiento automático futuro.
     * </p>
     *
     * @param idEmpleado Identificador del empleado.
     * @param fechaBaja Fecha efectiva de la finalización del contrato.
     */
    @Transactional
    public void darDeBajaEmpleado(Long idEmpleado, LocalDate fechaBaja) {
        String correoEmpresaAuth = SecurityContextHolder.getContext().getAuthentication().getName();
        Empresa empresaLogueada = empresaRepository.findByEmail(correoEmpresaAuth)
                .orElseThrow(() -> new EntityNotFoundException("Empresa no encontrada"));

        Empleado empleado = empleadoRepository.findById(idEmpleado)
                .orElseThrow(() -> new EntityNotFoundException("Empleado no encontrado"));

        if (!empleado.getEmpresa().getIdEmpresa().equals(empresaLogueada.getIdEmpresa())) {
            log.warn("VIOLACIÓN DE SEGURIDAD: La empresa '{}' intentó dar de baja al empleado ID {}, que pertenece a otra empresa.", correoEmpresaAuth, idEmpleado);
            throw new RuntimeException("No tienes permiso para dar de baja a este empleado");
        }

        empleado.setFechaBajaContrato(fechaBaja);

        if (!fechaBaja.isAfter(LocalDate.now())) {
            empleado.setActivo(false);
            log.info("La empresa '{}' ha tramitado la BAJA INMEDIATA del empleado ID {} ({})", correoEmpresaAuth, idEmpleado, empleado.getEmail());
        } else {
            log.info("La empresa '{}' ha programado la BAJA del empleado ID {} ({}) para la fecha: {}", correoEmpresaAuth, idEmpleado, empleado.getEmail(), fechaBaja);
        }

        empleadoRepository.save(empleado);
    }

    /**
     * Reincorpora a un empleado que previamente se encontraba en estado de baja.
     * <p>
     * Limpia la fecha de fin de contrato, reactiva la cuenta y genera una nueva contraseña de acceso.
     * </p>
     *
     * @param idEmpleado Identificador del empleado a readmitir.
     * @return {@link RespuestaCrearEmpleadoDTO} con la nueva contraseña de acceso para el empleado reincorporado.
     */
    @Transactional
    public RespuestaCrearEmpleadoDTO readmitirEmpleado(Long idEmpleado) {

        String correoEmpresaAuth = SecurityContextHolder.getContext().getAuthentication().getName();
        Empresa empresaLogueada = empresaRepository.findByEmail(correoEmpresaAuth)
                .orElseThrow(() -> new EntityNotFoundException("Empresa no encontrada"));

        Empleado empleado = empleadoRepository.findById(idEmpleado)
                .orElseThrow(() -> new EntityNotFoundException("Empleado no encontrado con ID: " + idEmpleado));

        if (!empleado.getEmpresa().getIdEmpresa().equals(empresaLogueada.getIdEmpresa())) {
            log.warn("VIOLACIÓN DE SEGURIDAD: La empresa '{}' intentó readmitir al empleado ID {}, que pertenece a otra empresa.", correoEmpresaAuth, idEmpleado);
            throw new RuntimeException("Acceso denegado: Este empleado no pertenece a tu empresa.");
        }

        if (empleado.getFechaBajaContrato() == null) {
            log.warn("La empresa '{}' intentó readmitir al empleado ID {}, pero ya estaba en estado activo.", correoEmpresaAuth, idEmpleado);
            throw new RuntimeException("El empleado ya está activo. No se puede readmitir a alguien que no está de baja.");
        }

        String nuevaContrasenaPlana = java.util.UUID.randomUUID().toString().substring(0, 8);
        String contrasenaEncriptada = codificadorPassword.encode(nuevaContrasenaPlana);

        empleado.setFechaBajaContrato(null);
        empleado.setActivo(true);
        empleado.setPassword(contrasenaEncriptada);

        empleado = empleadoRepository.save(empleado);

        log.info("La empresa '{}' ha READMITIDO al empleado ID {} ({}) y se ha generado una nueva contraseña.", correoEmpresaAuth, idEmpleado, empleado.getEmail());

        return RespuestaCrearEmpleadoDTO.builder()
                .idEmpleado(empleado.getIdEmpleado())
                .nombre(empleado.getNombre())
                .apellidos(empleado.getApellidos())
                .email(empleado.getEmail())
                .rol(empleado.getRol())
                .passwordGenerada(nuevaContrasenaPlana)
                .build();
    }

    /**
     * Recupera la información detallada del perfil del empleado que ha iniciado sesión.
     * Este método se basa en el contexto de seguridad JWT.
     *
     * @return {@link RespuestaEmpleadoDTO} con los datos del perfil propio del empleado.
     */
    @Transactional(readOnly = true)
    public RespuestaEmpleadoDTO obtenerMiPerfil() {
        String correoEmpleadoAuth = SecurityContextHolder.getContext().getAuthentication().getName();

        Empleado empleado = empleadoRepository.findByEmail(correoEmpleadoAuth)
                .orElseThrow(() -> new EntityNotFoundException("Error crítico: Empleado no encontrado en el sistema"));

        boolean esRealmenteActivo = empleado.getActivo() &&
                (empleado.getFechaBajaContrato() == null || empleado.getFechaBajaContrato().isAfter(LocalDate.now()));

        return RespuestaEmpleadoDTO.builder()
                .idEmpleado(empleado.getIdEmpleado())
                .email(empleado.getEmail())
                .nombre(empleado.getNombre())
                .apellidos(empleado.getApellidos())
                .telefono(empleado.getTelefono())
                .puesto(empleado.getPuesto())
                .departamento(empleado.getDepartamento())
                .rol(empleado.getRol())
                .activo(esRealmenteActivo)
                .fechaBajaContrato(empleado.getFechaBajaContrato())
                .build();
    }

    /**
     * Permite al empleado autenticado actualizar su contraseña personal.
     * <p>
     * Requiere la validación exitosa de su contraseña actual.
     * </p>
     *
     * @param peticion DTO con la contraseña actual y la nueva contraseña deseada.
     */
    @Transactional
    public void cambiarMiContrasena(PeticionCambiarPasswordDTO peticion) {
        String correoEmpleadoAuth = SecurityContextHolder.getContext().getAuthentication().getName();

        Empleado empleado = empleadoRepository.findByEmail(correoEmpleadoAuth)
                .orElseThrow(() -> new EntityNotFoundException("Error crítico: Empleado no encontrado en el sistema"));

        if (!codificadorPassword.matches(peticion.getPasswordActual(), empleado.getPassword())) {
            log.warn("Cambio de contraseña DENEGADO para el empleado '{}': La contraseña actual no coincide.", correoEmpleadoAuth);
            throw new RuntimeException("La contraseña actual no es correcta. Operación denegada.");
        }

        String nuevaContrasenaEncriptada = codificadorPassword.encode(peticion.getNuevaPassword());
        empleado.setPassword(nuevaContrasenaEncriptada);

        empleadoRepository.save(empleado);
        log.info("El empleado '{}' ha cambiado su contraseña con éxito.", correoEmpleadoAuth);
    }

    /**
     * Restablece la contraseña de un empleado por decisión administrativa de RRHH.
     * <p>
     * A diferencia del cambio de contraseña personal, esta operación no exige la contraseña previa:
     * está pensada para escenarios de olvido, ya que la política de la empresa prohíbe la auto-recuperación
     * por correo. Se mantiene el aislamiento multi-tenant: la empresa autenticada solo puede restablecer
     * contraseñas de empleados que le pertenezcan.
     * </p>
     *
     * @param idEmpleado Identificador del empleado cuya contraseña se va a restablecer.
     * @param nuevaPassword Nueva contraseña en texto plano, que se almacenará cifrada con BCrypt.
     * @return {@link RespuestaEmpleadoDTO} con los datos del empleado actualizado (sin exponer la contraseña).
     * @throws EntityNotFoundException Si el empleado no existe en el sistema.
     * @throws RuntimeException Si el empleado pertenece a otra empresa distinta de la autenticada.
     */
    @Transactional
    public RespuestaEmpleadoDTO resetPassword(Long idEmpleado, String nuevaPassword) {

        String correoEmpresaAuth = SecurityContextHolder.getContext().getAuthentication().getName();
        Empresa empresaLogueada = empresaRepository.findByEmail(correoEmpresaAuth)
                .orElseThrow(() -> new EntityNotFoundException("Empresa no encontrada"));

        Empleado empleado = empleadoRepository.findById(idEmpleado)
                .orElseThrow(() -> new EntityNotFoundException("Empleado no encontrado con ID: " + idEmpleado));

        if (!empleado.getEmpresa().getIdEmpresa().equals(empresaLogueada.getIdEmpresa())) {
            log.warn("VIOLACIÓN DE SEGURIDAD: La empresa '{}' intentó restablecer la contraseña del empleado ID {}, que pertenece a otra empresa.", correoEmpresaAuth, idEmpleado);
            throw new RuntimeException("Acceso denegado: Este empleado no pertenece a tu empresa.");
        }

        empleado.setPassword(codificadorPassword.encode(nuevaPassword));
        empleado = empleadoRepository.save(empleado);

        log.info("La empresa '{}' ha RESTABLECIDO la contraseña del empleado ID {} ({})", correoEmpresaAuth, idEmpleado, empleado.getEmail());

        boolean esRealmenteActivo = empleado.getActivo() &&
                (empleado.getFechaBajaContrato() == null || empleado.getFechaBajaContrato().isAfter(LocalDate.now()));

        return RespuestaEmpleadoDTO.builder()
                .idEmpleado(empleado.getIdEmpleado())
                .email(empleado.getEmail())
                .nombre(empleado.getNombre())
                .apellidos(empleado.getApellidos())
                .telefono(empleado.getTelefono())
                .puesto(empleado.getPuesto())
                .departamento(empleado.getDepartamento())
                .rol(empleado.getRol())
                .activo(esRealmenteActivo)
                .fechaBajaContrato(empleado.getFechaBajaContrato())
                .build();
    }
}