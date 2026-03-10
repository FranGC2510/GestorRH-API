package com.gestorrh.api.service;

import com.gestorrh.api.dto.PeticionActualizarEmpleadoDTO;
import com.gestorrh.api.dto.PeticionCrearEmpleadoDTO;
import com.gestorrh.api.dto.RespuestaCrearEmpleadoDTO;
import com.gestorrh.api.dto.RespuestaEmpleadoDTO;
import com.gestorrh.api.entity.Empleado;
import com.gestorrh.api.entity.Empresa;
import com.gestorrh.api.repository.EmpleadoRepository;
import com.gestorrh.api.repository.EmpresaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Servicio encargado de la lógica de negocio de los Empleados.
 */
@Service
@RequiredArgsConstructor
public class EmpleadoService {

    private final EmpleadoRepository empleadoRepository;
    private final EmpresaRepository empresaRepository;
    private final PasswordEncoder codificadorPassword;

    /**
     * Crea un nuevo empleado asociado automáticamente a la empresa que hace la petición.
     */
    @Transactional
    public RespuestaCrearEmpleadoDTO crearEmpleado(PeticionCrearEmpleadoDTO peticion) {

        String correoEmpresaAuth = SecurityContextHolder.getContext().getAuthentication().getName();

        Empresa empresa = empresaRepository.findByEmail(correoEmpresaAuth)
                .orElseThrow(() -> new RuntimeException("Empresa no encontrada en el sistema"));

        if (empleadoRepository.findByEmail(peticion.getEmail()).isPresent()) {
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
     * Obtiene la lista de todos los empleados pertenecientes a la empresa autenticada.
     */
    @Transactional(readOnly = true)
    public List<RespuestaEmpleadoDTO> obtenerEmpleadosDeEmpresa() {

        String correoEmpresaAuth = SecurityContextHolder.getContext().getAuthentication().getName();

        Empresa empresa = empresaRepository.findByEmail(correoEmpresaAuth)
                .orElseThrow(() -> new RuntimeException("Empresa no encontrada"));

        List<Empleado> empleados = empleadoRepository.findByEmpresaIdEmpresa(empresa.getIdEmpresa());

        return empleados.stream().map(emp -> RespuestaEmpleadoDTO.builder()
                .idEmpleado(emp.getIdEmpleado())
                .email(emp.getEmail())
                .nombre(emp.getNombre())
                .apellidos(emp.getApellidos())
                .telefono(emp.getTelefono())
                .puesto(emp.getPuesto())
                .departamento(emp.getDepartamento())
                .rol(emp.getRol())
                .activo(emp.getActivo())
                .fechaBajaContrato(emp.getFechaBajaContrato())
                .build()
        ).collect(Collectors.toList());
    }

    /**
     * Actualiza los datos de un empleado, verificando que pertenezca a la empresa logueada.
     */
    @Transactional
    public RespuestaEmpleadoDTO actualizarEmpleado(Long idEmpleado, PeticionActualizarEmpleadoDTO peticion) {

        String correoEmpresaAuth = SecurityContextHolder.getContext().getAuthentication().getName();
        Empresa empresaLogueada = empresaRepository.findByEmail(correoEmpresaAuth)
                .orElseThrow(() -> new RuntimeException("Empresa no encontrada"));

        Empleado empleado = empleadoRepository.findById(idEmpleado)
                .orElseThrow(() -> new RuntimeException("Empleado no encontrado con ID: " + idEmpleado));

        if (!empleado.getEmpresa().getIdEmpresa().equals(empresaLogueada.getIdEmpresa())) {
            throw new RuntimeException("Acceso denegado: Este empleado no pertenece a tu empresa.");
        }

        // 4. Actualizamos los datos
        empleado.setNombre(peticion.getNombre());
        empleado.setApellidos(peticion.getApellidos());
        empleado.setTelefono(peticion.getTelefono());
        empleado.setPuesto(peticion.getPuesto());
        empleado.setDepartamento(peticion.getDepartamento());
        empleado.setRol(peticion.getRol());
        empleado.setActivo(peticion.getActivo());

        empleado = empleadoRepository.save(empleado);

        return RespuestaEmpleadoDTO.builder()
                .idEmpleado(empleado.getIdEmpleado())
                .email(empleado.getEmail())
                .nombre(empleado.getNombre())
                .apellidos(empleado.getApellidos())
                .telefono(empleado.getTelefono())
                .puesto(empleado.getPuesto())
                .departamento(empleado.getDepartamento())
                .rol(empleado.getRol())
                .activo(empleado.getActivo())
                .fechaBajaContrato(empleado.getFechaBajaContrato())
                .build();
    }

    /**
     * Registra la baja de un empleado poniendo la fecha de hoy como fin de contrato.
     */
    @Transactional
    public void darDeBajaEmpleado(Long idEmpleado, LocalDate fechaBaja) {
        String correoEmpresaAuth = SecurityContextHolder.getContext().getAuthentication().getName();
        Empresa empresaLogueada = empresaRepository.findByEmail(correoEmpresaAuth)
                .orElseThrow(() -> new RuntimeException("Empresa no encontrada"));

        Empleado empleado = empleadoRepository.findById(idEmpleado)
                .orElseThrow(() -> new RuntimeException("Empleado no encontrado"));

        if (!empleado.getEmpresa().getIdEmpresa().equals(empresaLogueada.getIdEmpresa())) {
            throw new RuntimeException("No tienes permiso para dar de baja a este empleado");
        }

        empleado.setFechaBajaContrato(fechaBaja);
        empleadoRepository.save(empleado);
    }

    /**
     * Readmite a un empleado que estaba de baja, reseteando su fecha de fin de contrato
     * y generándole una nueva contraseña de acceso.
     */
    @Transactional
    public RespuestaCrearEmpleadoDTO readmitirEmpleado(Long idEmpleado) {

        String correoEmpresaAuth = SecurityContextHolder.getContext().getAuthentication().getName();
        Empresa empresaLogueada = empresaRepository.findByEmail(correoEmpresaAuth)
                .orElseThrow(() -> new RuntimeException("Empresa no encontrada"));

        Empleado empleado = empleadoRepository.findById(idEmpleado)
                .orElseThrow(() -> new RuntimeException("Empleado no encontrado con ID: " + idEmpleado));

        if (!empleado.getEmpresa().getIdEmpresa().equals(empresaLogueada.getIdEmpresa())) {
            throw new RuntimeException("Acceso denegado: Este empleado no pertenece a tu empresa.");
        }

        if (empleado.getFechaBajaContrato() == null) {
            throw new RuntimeException("El empleado ya está activo. No se puede readmitir a alguien que no está de baja.");
        }

        String nuevaContrasenaPlana = java.util.UUID.randomUUID().toString().substring(0, 8);
        String contrasenaEncriptada = codificadorPassword.encode(nuevaContrasenaPlana);

        empleado.setFechaBajaContrato(null);
        empleado.setActivo(true);
        empleado.setPassword(contrasenaEncriptada);

        empleado = empleadoRepository.save(empleado);

        return RespuestaCrearEmpleadoDTO.builder()
                .idEmpleado(empleado.getIdEmpleado())
                .nombre(empleado.getNombre())
                .apellidos(empleado.getApellidos())
                .email(empleado.getEmail())
                .rol(empleado.getRol())
                .passwordGenerada(nuevaContrasenaPlana)
                .build();
    }
}