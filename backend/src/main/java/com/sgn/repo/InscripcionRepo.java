package com.sgn.repo;

import com.sgn.ent.Inscripcion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface InscripcionRepo extends JpaRepository<Inscripcion, Long> {

    List<Inscripcion> findByAlumnoLegajoAndAnio(String legajo, int anio);
    List<Inscripcion> findByAlumnoLegajoAndActivaTrue(String legajo);

    List<Inscripcion> findByCursoIdAndAnio(Long cursoId, int anio);

    Optional<Inscripcion> findByAlumnoLegajoAndCursoIdAndAnio(
        String legajo,
        Long cursoId,
        int anio
    );

    List<Inscripcion> findByAlumnoLegajoAndAnioAndActivaTrue(
        String legajo,
        int anio
    );

    List<Inscripcion> findByAlumnoLegajoInAndAnioAndActivaTrue(List<String> legajos, int anio);

    Optional<Inscripcion> findByAlumnoLegajoAndCursoIdAndAnioAndActivaTrue(
        String legajo,
        Long cursoId,
        int anio
    );
}
