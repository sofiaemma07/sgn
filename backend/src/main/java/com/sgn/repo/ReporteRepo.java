package com.sgn.repo;

import com.sgn.ent.Reporte;
import com.sgn.enm.EstRep;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ReporteRepo extends JpaRepository<Reporte, Long> {
    List<Reporte> findByEstOrderByFecRepDesc(EstRep est);
    List<Reporte> findByNotaBloqueProfLegajoAndEst(String profLegajo, EstRep est);
    List<Reporte> findByNotaIdAndEstIn(Long notaId, List<EstRep> estados);

    @Query("select r.id from Reporte r where r.alumno.legajo = :alumnoLegajo " +
        "and r.nota.bloque.prof.legajo = :profLegajo and r.est in :estados")
    List<Long> findActivosByAlumnoAndProfesor(@Param("alumnoLegajo") String alumnoLegajo,
        @Param("profLegajo") String profLegajo, @Param("estados") List<EstRep> estados);
}
