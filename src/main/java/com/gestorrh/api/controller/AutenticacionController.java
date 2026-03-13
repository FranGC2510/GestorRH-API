package com.gestorrh.api.controller;

import com.gestorrh.api.dto.autenticacionDTO.PeticionLoginDTO;
import com.gestorrh.api.dto.autenticacionDTO.RespuestaLoginDTO;
import com.gestorrh.api.service.AutenticacionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controlador que gestiona los endpoints públicos de autenticación.
 */
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AutenticacionController {

    private final AutenticacionService servicioAutenticacion;

    /**
     * Endpoint para el inicio de sesión de una Empresa.
     * URL: POST http://localhost:8080/auth/login-empresa
     */
    @PostMapping("/login-empresa")
    public ResponseEntity<RespuestaLoginDTO> loginEmpresa(@Valid @RequestBody PeticionLoginDTO peticion) {

        RespuestaLoginDTO respuesta = servicioAutenticacion.loginEmpresa(peticion);
        return ResponseEntity.ok(respuesta);
    }

    /**
     * Endpoint para el inicio de sesión de un Empleado.
     * URL: POST http://localhost:8080/auth/login-empleado
     */
    @PostMapping("/login-empleado")
    public ResponseEntity<RespuestaLoginDTO> loginEmpleado(@Valid @RequestBody PeticionLoginDTO peticion) {

        RespuestaLoginDTO respuesta = servicioAutenticacion.loginEmpleado(peticion);
        return ResponseEntity.ok(respuesta);
    }
}
