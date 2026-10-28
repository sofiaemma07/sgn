package com.sgn.repo;

import com.sgn.ent.FaseCarga;
import com.sgn.enm.FasePlanilla;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface FaseCargaRepo extends JpaRepository<FaseCarga, Long> {
    List<FaseCarga> findByAnioOrderById(int anio);
    Optional<FaseCarga> findByAnioAndFase(int anio, FasePlanilla fase);
}
