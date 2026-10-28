package com.sgn.repo;

import com.sgn.ent.Nota;
import com.sgn.enm.TipoNota;
import com.sgn.enm.FasePlanilla;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.Collection;

public interface NotaRepo extends JpaRepository<Nota, Long> {
    List<Nota> findByBloqueIdAndAlumnoLegajo(Long bloqueId, String legajo);
    List<Nota> findByAlumnoLegajoAndBloqueAnio(String legajo, int anio);
    List<Nota> findByBloqueId(Long bloqueId);
    Optional<Nota> findByBloqueIdAndAlumnoLegajoAndTipoAndNumPer(Long bloqueId, String legajo, TipoNota tipo, int numPer);
    Optional<Nota> findByBloqueIdAndAlumnoLegajoAndFase(Long bloqueId, String legajo, FasePlanilla fase);
    List<Nota> findByBloqueIdAndFase(Long bloqueId, FasePlanilla fase);
    List<Nota> findByTipoIn(Collection<TipoNota> tipos);
    long deleteByTipoIn(Collection<TipoNota> tipos);
}
