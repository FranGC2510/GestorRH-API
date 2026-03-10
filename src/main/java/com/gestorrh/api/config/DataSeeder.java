package com.gestorrh.api.config;

import com.gestorrh.api.entity.Empleado;
import com.gestorrh.api.entity.Empresa;
import com.gestorrh.api.repository.EmpleadoRepository;
import com.gestorrh.api.repository.EmpresaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Clase que inyecta datos de prueba en la base de datos automáticamente al arrancar.
 */
@Component
@RequiredArgsConstructor
public class DataSeeder implements CommandLineRunner {

    private final EmpresaRepository empresaRepository;
    private final EmpleadoRepository empleadoRepository;
    private final PasswordEncoder codificadorPassword;

    @Override
    public void run(String... args) throws Exception {

        if (empresaRepository.count() == 0) {
            // 1. Crear Empresa
            Empresa empresaPrueba = Empresa.builder()
                    .email("admin@miempresa.com")
                    .password(codificadorPassword.encode("123456"))
                    .nombre("Tech Solutions S.L.")
                    .direccion("Calle Falsa 123, Madrid")
                    .telefono("912345678")
                    .build();

            empresaPrueba = empresaRepository.save(empresaPrueba);
            System.out.println("✅ [DataSeeder] Empresa inyectada.");

            Empleado empleadoPrueba = Empleado.builder()
                    .empresa(empresaPrueba)
                    .email("juan@miempresa.com")
                    .password(codificadorPassword.encode("123456"))
                    .nombre("Juan")
                    .apellidos("Pérez")
                    .rol(com.gestorrh.api.entity.RolEmpleado.EMPLEADO)
                    .activo(true)
                    .build();

            empleadoRepository.save(empleadoPrueba);
            System.out.println("✅ [DataSeeder] Empleado inyectado.");
        }
    }
}
