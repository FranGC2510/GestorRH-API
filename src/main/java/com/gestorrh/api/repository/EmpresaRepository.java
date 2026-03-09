package com.gestorrh.api.repository;

import com.gestorrh.api.entity.Empresa;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repositorio para gestionar las operaciones de base de datos de la entidad Empresa.
 * Al extender de JpaRepository, heredamos automáticamente métodos como save(), findById(), delete(), etc.
 */
@Repository
public interface EmpresaRepository extends JpaRepository<Empresa, Long> {

    /**
     * Busca una empresa por su correo electrónico.
     * Es fundamental para el proceso de Login.
     * * @param email El correo electrónico a buscar.
     * @return Un Optional que contendrá la Empresa si existe, o estará vacío si no se encuentra.
     */
    Optional<Empresa> findByEmail(String email);

}
