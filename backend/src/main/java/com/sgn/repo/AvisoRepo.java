package com.sgn.repo;

import com.sgn.ent.Aviso;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface AvisoRepo extends JpaRepository<Aviso, Long> {
    List<Aviso> findByDestinatarioLegajoOrderByCreadaEnDesc(String legajo);
    Optional<Aviso> findByIdAndDestinatarioLegajo(Long id, String legajo);
    boolean existsByClaveEvento(String claveEvento);
    long countByDestinatarioLegajoAndLeidaFalse(String legajo);
    void deleteByDestinatarioLegajoAndLeidaTrue(String legajo);
}
