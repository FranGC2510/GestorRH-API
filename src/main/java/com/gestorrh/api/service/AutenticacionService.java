package com.gestorrh.api.service;

import com.gestorrh.api.dto.autenticacion.RespuestaLoginDTO;
import com.gestorrh.api.dto.autenticacion.PeticionLoginDTO;
import com.gestorrh.api.entity.Empleado;
import com.gestorrh.api.entity.Empresa;
import com.gestorrh.api.repository.EmpleadoRepository;
import com.gestorrh.api.repository.EmpresaRepository;
import com.gestorrh.api.security.ServicioJwt;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * Servicio encargado de orquestar los procesos de autenticación y seguridad de acceso.
 * <p>
 * Proporciona métodos específicos para el inicio de sesión de empresas y empleados,
 * validando credenciales cifradas y estados de cuenta (actividad y vigencia de contratos).
 * </p>
 * <p>
 * Tras una autenticación exitosa, genera tokens de acceso seguros (JWT) enriquecidos
 * con los {@code Claims} necesarios para la autorización basada en roles dentro de la API.
 * </p>
 *
 * @see com.gestorrh.api.security.ServicioJwt
 * @see com.gestorrh.api.dto.autenticacion.RespuestaLoginDTO
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AutenticacionService {

    private final EmpresaRepository empresaRepository;
    private final EmpleadoRepository empleadoRepository;
    private final PasswordEncoder codificadorPassword;
    private final ServicioJwt servicioJwt;

    /**
     * Realiza la autenticación centralizada para el perfil de Empresa.
     * <p>
     * El proceso verifica la existencia de la cuenta por email y valida la contraseña 
     * mediante el codificador configurado. Si el acceso es válido, se emite un token JWT 
     * que contiene la identidad de la empresa y su rol administrativo.
     * </p>
     *
     * @param request Objeto {@link PeticionLoginDTO} con las credenciales de la empresa.
     * @return {@link RespuestaLoginDTO} que incluye el token de sesión y los datos de perfil básicos.
     * @throws RuntimeException Si el correo no existe o la contraseña es errónea.
     */
    public RespuestaLoginDTO loginEmpresa(PeticionLoginDTO request) {

        Empresa empresa = empresaRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> {
                    log.warn("Intento de login fallido (Empresa): El email '{}' no existe en la BD.", request.getEmail());
                    return new RuntimeException("Credenciales inválidas");
                });

        if (!codificadorPassword.matches(request.getPassword(), empresa.getPassword())) {
            log.warn("Intento de login fallido (Empresa): Contraseña incorrecta para el email '{}'.", request.getEmail());
            throw new RuntimeException("Credenciales inválidas");
        }

        Map<String, Object> extraClaims = new HashMap<>();
        extraClaims.put("rol", "EMPRESA");
        extraClaims.put("id", empresa.getIdEmpresa());

        String token = servicioJwt.generarToken(empresa.getEmail(), extraClaims);

        log.info("Inicio de sesión EXITOSO (Empresa): {}", empresa.getEmail());

        return RespuestaLoginDTO.builder()
                .token(token)
                .rol("EMPRESA")
                .id(empresa.getIdEmpresa())
                .nombre(empresa.getNombre())
                .build();
    }

    /**
     * Realiza el proceso de autenticación integral para un Empleado.
     * <p>
     * Además de la validación estándar de credenciales, este método aplica reglas de negocio 
     * críticas sobre el estado de la cuenta:
     * </p>
     * <ul>
     *   <li>Verifica que el flag {@code activo} sea verdadero.</li>
     *   <li>Comprueba que la {@code fechaBajaContrato} no haya sido superada (Contrato vigente).</li>
     * </ul>
     * <p>
     * Si todas las validaciones son exitosas, emite un token JWT enriquecido con 
     * el rol específico (EMPLEADO, SUPERVISOR), el ID del empleado y el ID de su empresa.
     * </p>
     *
     * @param peticion Objeto {@link PeticionLoginDTO} con el email y password del trabajador.
     * @return {@link RespuestaLoginDTO} con el token de acceso y la información de perfil para el frontend.
     * @throws RuntimeException Si las credenciales fallan o si el empleado tiene el acceso bloqueado por cese laboral.
     */
    public RespuestaLoginDTO loginEmpleado(PeticionLoginDTO peticion) {

        Empleado empleado = empleadoRepository.findByEmail(peticion.getEmail())
                .orElseThrow(() -> {
                    log.warn("Intento de login fallido (Empleado): El email '{}' no existe en la BD.", peticion.getEmail());
                    return new RuntimeException("Credenciales inválidas");
                });

        if (!codificadorPassword.matches(peticion.getPassword(), empleado.getPassword())) {
            log.warn("Intento de login fallido (Empleado): Contraseña incorrecta para el email '{}'.", peticion.getEmail());
            throw new RuntimeException("Credenciales inválidas");
        }

        boolean contratoExpirado = empleado.getFechaBajaContrato() != null && !empleado.getFechaBajaContrato().isAfter(java.time.LocalDate.now());

        if (!empleado.getActivo() || contratoExpirado) {
            log.warn("Intento de login bloqueado (Empleado): La cuenta de '{}' está inactiva o su contrato ha expirado.", peticion.getEmail());
            throw new RuntimeException("Acceso denegado: Su relación laboral con la empresa ha finalizado.");
        }

        Map<String, Object> datosExtra = new HashMap<>();
        datosExtra.put("rol", empleado.getRol().name());
        datosExtra.put("id", empleado.getIdEmpleado());
        datosExtra.put("idEmpresa", empleado.getEmpresa().getIdEmpresa());
        datosExtra.put("nombreEmpresa", empleado.getEmpresa().getNombre());

        String token = servicioJwt.generarToken(empleado.getEmail(), datosExtra);

        log.info("Inicio de sesión EXITOSO (Empleado): {} con Rol [{}]", empleado.getEmail(), empleado.getRol());

        return RespuestaLoginDTO.builder()
                .token(token)
                .rol(empleado.getRol().name())
                .id(empleado.getIdEmpleado())
                .nombre(empleado.getNombre() + " " + empleado.getApellidos())
                .nombreEmpresa(empleado.getEmpresa().getNombre())
                .build();
    }
}
