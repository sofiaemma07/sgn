package com.sgn.repo;

import com.sgn.ent.MateriaPrevia;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface MateriaPreviaRepo extends JpaRepository<MateriaPrevia, Long> {
    List<MateriaPrevia> findByAlumnoLegajoOrderByBloqueOrigenAnioAsc(String legajo);
    Optional<MateriaPrevia> findByAlumnoLegajoAndBloqueOrigenId(String legajo, Long bloqueId);
}
