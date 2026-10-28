package com.sgn.repo;

import com.sgn.ent.PlanillaCierre;
import com.sgn.enm.FasePlanilla;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.List;

public interface PlanillaCierreRepo extends JpaRepository<PlanillaCierre, Long> {
    Optional<PlanillaCierre> findByBloqueIdAndFase(long bloqueId, FasePlanilla fase);
    List<PlanillaCierre> findByBloqueId(long bloqueId);
}
