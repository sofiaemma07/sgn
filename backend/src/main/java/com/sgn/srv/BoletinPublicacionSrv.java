package com.sgn.srv;

import com.sgn.dto.Dtos.BoletinPublicacionRes;
import com.sgn.ent.BoletinPublicacion;
import com.sgn.ent.Usuario;
import com.sgn.enm.FasePlanilla;
import com.sgn.repo.BloqueRepo;
import com.sgn.repo.BoletinPublicacionRepo;
import com.sgn.repo.FaseCargaRepo;
import com.sgn.repo.InscripcionRepo;
import com.sgn.repo.PlanillaCierreRepo;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.NoSuchElementException;

@Service
public class BoletinPublicacionSrv {
    private final BoletinPublicacionRepo repo;
    private final BloqueRepo bloqueRepo;
    private final InscripcionRepo inscRepo;
    private final FaseCargaRepo faseRepo;
    private final PlanillaCierreRepo cierreRepo;

    public BoletinPublicacionSrv(BoletinPublicacionRepo repo, BloqueRepo bloqueRepo,
                                 InscripcionRepo inscRepo, FaseCargaRepo faseRepo,
                                 PlanillaCierreRepo cierreRepo) {
        this.repo = repo;
        this.bloqueRepo = bloqueRepo;
        this.inscRepo = inscRepo;
        this.faseRepo = faseRepo;
        this.cierreRepo = cierreRepo;
    }

    public BoletinPublicacionRes estadoBloque(long bloqueId) {
        var bloque = bloqueRepo.findById(bloqueId)
            .orElseThrow(() -> new NoSuchElementException("Materia/curso no encontrado"));
        return estado(bloque.getCurso().getId(), bloque.getAnio());
    }

    public BoletinPublicacionRes estadoAlumno(String legajo, int anio) {
        var inscripciones = inscRepo.findByAlumnoLegajoAndAnioAndActivaTrue(legajo, anio);
        if (inscripciones.isEmpty()) return new BoletinPublicacionRes(false, null);
        var cursoId = inscripciones.get(0).getCurso().getId();
        var publicacion = repo.findByCursoIdAndAnio(cursoId, anio).orElse(null);
        return new BoletinPublicacionRes(publicacion != null && publicacion.isPublicado(),
            publicacion != null && publicacion.getPublicadoEn() != null
                ? publicacion.getPublicadoEn().toString() : null);
    }

    public boolean estaPublicado(String legajo, int anio) {
        return estadoAlumno(legajo, anio).publicado();
    }

    @Transactional
    public BoletinPublicacionRes publicar(long bloqueId, boolean publicado, Usuario actor) {
        var bloque = bloqueRepo.findById(bloqueId)
            .orElseThrow(() -> new NoSuchElementException("Materia/curso no encontrado"));
        if (publicado) validarFasesVencidasCerradas(bloque.getCurso().getId(), bloque.getAnio());
        var entity = repo.findByCursoIdAndAnio(bloque.getCurso().getId(), bloque.getAnio())
            .orElseGet(BoletinPublicacion::new);
        entity.setCurso(bloque.getCurso());
        entity.setAnio(bloque.getAnio());
        entity.setPublicado(publicado);
        entity.setPublicadoEn(publicado ? LocalDateTime.now() : null);
        entity.setPublicadoPor(publicado ? actor : null);
        entity = repo.save(entity);
        return new BoletinPublicacionRes(entity.isPublicado(), entity.getPublicadoEn() != null
            ? entity.getPublicadoEn().toString() : null);
    }

    private void validarFasesVencidasCerradas(Long cursoId, int anio) {
        LocalDate hoy = LocalDate.now(ZoneId.of("America/Argentina/Buenos_Aires"));
        List<FasePlanilla> fasesRegulares = List.of(FasePlanilla.BIM_1, FasePlanilla.BIM_2,
            FasePlanilla.FINAL_1, FasePlanilla.BIM_3, FasePlanilla.BIM_4, FasePlanilla.FINAL_2);
        for (var bloque : bloqueRepo.findByCursoIdAndAnio(cursoId, anio)) {
            for (FasePlanilla fase : fasesRegulares) {
                var cfg = faseRepo.findByAnioAndFase(anio, fase).orElse(null);
                if (cfg != null && cfg.getHasta().isBefore(hoy)
                    && cierreRepo.findByBloqueIdAndFase(bloque.getId(), fase).isEmpty()) {
                    throw new IllegalStateException("No se puede publicar: " + bloque.getMateria().getNom()
                        + " todavía no entregó " + fase.etiqueta() + ", cuyo plazo ya venció");
                }
            }
        }
    }

    private BoletinPublicacionRes estado(Long cursoId, int anio) {
        var publicacion = repo.findByCursoIdAndAnio(cursoId, anio).orElse(null);
        return new BoletinPublicacionRes(publicacion != null && publicacion.isPublicado(),
            publicacion != null && publicacion.getPublicadoEn() != null
                ? publicacion.getPublicadoEn().toString() : null);
    }
}
