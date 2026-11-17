package com.sgn.srv;

import com.sgn.dto.Dtos.ConvocatoriaPreviaInfo;
import com.sgn.dto.Dtos.PreviaRes;
import com.sgn.dto.Dtos.RendicionPreviaReq;
import com.sgn.dto.Dtos.RendicionPreviaRes;
import com.sgn.ent.MateriaPrevia;
import com.sgn.ent.Nota;
import com.sgn.ent.RendicionPrevia;
import com.sgn.ent.Usuario;
import com.sgn.enm.ConvocatoriaPrevia;
import com.sgn.enm.FasePlanilla;
import com.sgn.repo.FaseCargaRepo;
import com.sgn.repo.MateriaPreviaRepo;
import com.sgn.repo.NotaRepo;
import com.sgn.repo.RendicionPreviaRepo;
import com.sgn.util.NotaReglas;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.sgn.ent.Bloque;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.NoSuchElementException;

@Service
public class MateriaPreviaSrv {
    private final MateriaPreviaRepo previaRepo;
    private final RendicionPreviaRepo rendicionRepo;
    private final NotaRepo notaRepo;
    private final FaseCargaRepo faseRepo;
    private final MailSrv mailSrv;

    public MateriaPreviaSrv(MateriaPreviaRepo previaRepo, RendicionPreviaRepo rendicionRepo,
                            NotaRepo notaRepo,
                            FaseCargaRepo faseRepo, MailSrv mailSrv) {
        this.previaRepo = previaRepo;
        this.rendicionRepo = rendicionRepo;
        this.notaRepo = notaRepo;
        this.faseRepo = faseRepo;
        this.mailSrv = mailSrv;
    }

    @Transactional(readOnly = true)
    public List<PreviaRes> listar() {
        return previaRepo.findAll().stream()
            .filter(this::correspondeComoPrevia)
            .sorted(Comparator.comparing((MateriaPrevia p) -> p.getAlumno().getApe(), String.CASE_INSENSITIVE_ORDER)
                .thenComparing(p -> p.getAlumno().getNom(), String.CASE_INSENSITIVE_ORDER)
                .thenComparing(p -> p.getBloqueOrigen().getMateria().getNom(), String.CASE_INSENSITIVE_ORDER))
            .map(this::toRes).toList();
    }

    @Transactional(readOnly = true)
    public List<PreviaRes> mias(Usuario alumno) {
        return previaRepo.findByAlumnoLegajoOrderByBloqueOrigenAnioAsc(alumno.getLegajo()).stream()
            .filter(this::correspondeComoPrevia)
            .map(this::toRes).toList();
    }

    @Transactional(readOnly = true)
    public List<ConvocatoriaPreviaInfo> convocatorias(int anio) {
        var agostoDesde = LocalDate.of(anio, 8, 1);
        var diciembreHastaCierre = faseRepo.findByAnioAndFase(anio, FasePlanilla.CIERRE_1)
            .map(f -> f.getHasta()).orElse(null);
        return List.of(
            new ConvocatoriaPreviaInfo(ConvocatoriaPrevia.AGOSTO, anio,
                agostoDesde.toString(), agostoDesde.plusDays(1).toString()),
            new ConvocatoriaPreviaInfo(ConvocatoriaPrevia.DICIEMBRE, anio,
                diciembreHastaCierre != null ? diciembreHastaCierre.plusDays(1).toString() : null,
                diciembreHastaCierre != null ? diciembreHastaCierre.plusDays(2).toString() : null)
        );
    }

    @Transactional
    public void crearDesdeFebreroSiReprobo(Nota nota, Usuario actor) {
        if (!reprobada(nota)) return;
        crearSiNoExiste(nota.getAlumno(), nota.getBloque(), actor, true);
    }

    @Transactional
    public RendicionPreviaRes registrar(long previaId, RendicionPreviaReq req, Usuario actor) {
        if (req.convocatoria() == null || req.fecha() == null || req.anio() < 2020)
            throw new IllegalArgumentException("Completá convocatoria, año y fecha de la mesa");
        if (req.nota() == null) throw new IllegalArgumentException("Ingresá la calificación de la previa");
        NotaReglas.validarVal(req.nota());
        MateriaPrevia previa = previaRepo.findById(previaId)
            .orElseThrow(() -> new NoSuchElementException("Materia previa no encontrada"));
        if (!correspondeComoPrevia(previa))
            throw new IllegalStateException("Solo se pueden registrar rendiciones de materias desaprobadas en el tercer cierre");
        var ventana = convocatorias(req.anio()).stream()
            .filter(c -> c.convocatoria() == req.convocatoria()).findFirst().orElseThrow();
        if (ventana.desde() == null || ventana.hasta() == null)
            throw new IllegalStateException("Configurá primero la fecha límite del cierre de diciembre para calcular esta mesa");
        LocalDate desde = LocalDate.parse(ventana.desde());
        LocalDate hasta = LocalDate.parse(ventana.hasta());
        if (req.fecha().isBefore(desde) || req.fecha().isAfter(hasta))
            throw new IllegalArgumentException("La fecha debe ser " + desde + " o " + hasta + " para esta convocatoria");
        if (aprobada(previa)) throw new IllegalStateException("Esta materia previa ya está aprobada");
        if (rendicionRepo.existsByPreviaIdAndAnioAndConvocatoria(previaId, req.anio(), req.convocatoria()))
            throw new IllegalStateException("Ya existe una rendición para esta convocatoria y año");

        RendicionPrevia rendicion = new RendicionPrevia();
        rendicion.setPrevia(previa);
        rendicion.setConvocatoria(req.convocatoria());
        rendicion.setAnio(req.anio());
        rendicion.setNota(req.nota());
        rendicion.setFecha(req.fecha());
        rendicion.setRegistradaPor(actor);
        rendicion = rendicionRepo.save(rendicion);

        String resultado = aprobada(rendicion) ? "aprobada" : "no aprobada";
        String texto = "Rendición de materia previa: " + previa.getBloqueOrigen().getMateria().getNom()
            + " (" + req.convocatoria() + " " + req.anio() + ") — Nota " + req.nota() + "; " + resultado + ".";
        mailSrv.enviar(previa.getAlumno().getMail(), "SGN - Resultado de materia previa", texto);
        if (previa.getAlumno().getMailTutor() != null && !previa.getAlumno().getMailTutor().isBlank())
            mailSrv.enviar(previa.getAlumno().getMailTutor(), "SGN - Resultado de materia previa", texto);
        return toRes(rendicion);
    }

    @Transactional(readOnly = true)
    public boolean aprobada(String legajo, long bloqueOrigenId) {
        return previaRepo.findByAlumnoLegajoAndBloqueOrigenId(legajo, bloqueOrigenId)
            .map(this::aprobada).orElse(false);
    }

    private MateriaPrevia crearSiNoExiste(Usuario alumno, Bloque bloque, Usuario actor, boolean avisar) {
        return previaRepo.findByAlumnoLegajoAndBloqueOrigenId(alumno.getLegajo(), bloque.getId())
            .orElseGet(() -> {
                MateriaPrevia previa = new MateriaPrevia();
                previa.setAlumno(alumno);
                previa.setBloqueOrigen(bloque);
                previa.setCreadaPor(actor);
                previa = previaRepo.save(previa);
                if (avisar) {
                    String texto = "Quedó pendiente la materia " + bloque.getMateria().getNom()
                        + " del ciclo " + bloque.getAnio() + ". Podés rendirla en las convocatorias de agosto o diciembre.";
                    mailSrv.enviar(alumno.getMail(), "SGN - Materia previa pendiente", texto);
                    if (alumno.getMailTutor() != null && !alumno.getMailTutor().isBlank())
                        mailSrv.enviar(alumno.getMailTutor(), "SGN - Materia previa pendiente", texto);
                }
                return previa;
            });
    }

    private boolean aprobada(MateriaPrevia previa) {
        return rendicionRepo.findByPreviaIdOrderByAnioDescConvocatoriaAsc(previa.getId()).stream()
            .anyMatch(this::aprobada);
    }

    private boolean aprobada(RendicionPrevia rendicion) {
        return NotaReglas.aprobada(rendicion.getNota());
    }

    private boolean correspondeComoPrevia(MateriaPrevia previa) {
        return notaRepo.findByBloqueIdAndAlumnoLegajoAndFase(
                previa.getBloqueOrigen().getId(), previa.getAlumno().getLegajo(), FasePlanilla.CIERRE_2)
            .filter(nota -> !nota.isBorrador() && reprobada(nota))
            .isPresent();
    }

    private boolean reprobada(Nota nota) {
        return NotaReglas.desaprobada(nota.getVal()) || "EP".equals(nota.getValorEspecial())
            || "EN PROCESO".equals(nota.getValorEspecial()) || "NI".equals(nota.getValorEspecial());
    }

    private PreviaRes toRes(MateriaPrevia previa) {
        Bloque bloque = previa.getBloqueOrigen();
        List<RendicionPreviaRes> rendiciones = rendicionRepo.findByPreviaIdOrderByAnioDescConvocatoriaAsc(previa.getId())
            .stream().map(this::toRes).toList();
        return new PreviaRes(previa.getId(), previa.getAlumno().getLegajo(), previa.getAlumno().nomCompleto(),
            bloque.getId(), bloque.getMateria().getNom(), bloque.getCurso().etiqueta(), bloque.getAnio(),
            rendiciones.stream().anyMatch(RendicionPreviaRes::aprobada), rendiciones);
    }

    private RendicionPreviaRes toRes(RendicionPrevia rendicion) {
        return new RendicionPreviaRes(rendicion.getConvocatoria(), rendicion.getAnio(), rendicion.getNota(),
            rendicion.getFecha(), aprobada(rendicion));
    }
}
