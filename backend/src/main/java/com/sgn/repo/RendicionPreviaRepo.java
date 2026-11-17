package com.sgn.repo;

import com.sgn.ent.RendicionPrevia;
import com.sgn.enm.ConvocatoriaPrevia;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface RendicionPreviaRepo extends JpaRepository<RendicionPrevia, Long> {
    List<RendicionPrevia> findByPreviaIdOrderByAnioDescConvocatoriaAsc(Long previaId);
    boolean existsByPreviaIdAndAnioAndConvocatoria(Long previaId, int anio, ConvocatoriaPrevia convocatoria);
}
