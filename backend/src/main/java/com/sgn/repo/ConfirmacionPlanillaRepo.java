package com.sgn.repo;

import com.sgn.ent.ConfirmacionPlanilla;
import com.sgn.enm.FasePlanilla;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface ConfirmacionPlanillaRepo extends JpaRepository<ConfirmacionPlanilla, Long> {
    List<ConfirmacionPlanilla> findByBloqueIdAndFase(long bloqueId, FasePlanilla fase);
    void deleteByBloqueId(long bloqueId);
    Optional<ConfirmacionPlanilla> findByBloqueIdAndFaseAndProfesorLegajo(long bloqueId, FasePlanilla fase, String legajo);
    void deleteByBloqueIdAndFase(long bloqueId, FasePlanilla fase);
}
