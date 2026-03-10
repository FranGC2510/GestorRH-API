package com.gestorrh.api.service;

import com.gestorrh.api.dto.RespuestaLoginDTO;
import com.gestorrh.api.dto.PeticionLoginDTO;
import com.gestorrh.api.entity.Empleado;
import com.gestorrh.api.entity.Empresa;
import com.gestorrh.api.repository.EmpleadoRepository;
import com.gestorrh.api.repository.EmpresaRepository;
import com.gestorrh.api.security.ServicioJwt;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * Servicio encargado de gestionar la lógica de autenticación (Login).
 */
@Service
@RequiredArgsConstructor
public class AutenticacionService {

    private final EmpresaRepository empresaRepository;
    private final EmpleadoRepository empleadoRepository;
    private final PasswordEncoder codificadorPassword;
    private final ServicioJwt servicioJwt;

    /**
     * Lógica para el login de una Empresa.
     */
    public RespuestaLoginDTO loginEmpresa(PeticionLoginDTO request) {

        Empresa empresa = empresaRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new RuntimeException("Credenciales inválidas"));

        if (!codificadorPassword.matches(request.getPassword(), empresa.getPassword())) {
            throw new RuntimeException("Credenciales inválidas");
        }

        Map<String, Object> extraClaims = new HashMap<>();
        extraClaims.put("rol", "EMPRESA");
        extraClaims.put("id", empresa.getIdEmpresa());

        String token = servicioJwt.generarToken(empresa.getEmail(), extraClaims);

        return RespuestaLoginDTO.builder()
                .token(token)
                .rol("EMPRESA")
                .id(empresa.getIdEmpresa())
                .nombre(empresa.getNombre())
                .build();
    }

    /**
     * Lógica para el login de un Empleado.
     */
    public RespuestaLoginDTO loginEmpleado(PeticionLoginDTO peticion) {

        Empleado empleado = empleadoRepository.findByEmail(peticion.getEmail())
                .orElseThrow(() -> new RuntimeException("Credenciales inválidas"));

        if (!codificadorPassword.matches(peticion.getPassword(), empleado.getPassword())) {
            throw new RuntimeException("Credenciales inválidas");
        }

        Map<String, Object> datosExtra = new HashMap<>();
        datosExtra.put("rol", empleado.getRol().name());
        datosExtra.put("id", empleado.getIdEmpleado());
        datosExtra.put("idEmpresa", empleado.getEmpresa().getIdEmpresa());

        String token = servicioJwt.generarToken(empleado.getEmail(), datosExtra);

        return RespuestaLoginDTO.builder()
                .token(token)
                .rol(empleado.getRol().name())
                .id(empleado.getIdEmpleado())
                .nombre(empleado.getNombre() + " " + empleado.getApellidos())
                .build();
    }
}
