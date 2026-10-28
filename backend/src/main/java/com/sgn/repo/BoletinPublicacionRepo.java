package com.sgn.repo;

import com.sgn.ent.BoletinPublicacion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface BoletinPublicacionRepo extends JpaRepository<BoletinPublicacion, Long> {
    Optional<BoletinPublicacion> findByCursoIdAndAnio(Long cursoId, int anio);
}
