package com.sgn.srv;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sgn.dto.Dtos.*;
import com.sgn.ent.*;
import com.sgn.enm.*;
import com.sgn.repo.*;
import com.sgn.util.NotaReglas;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class NotaSrv {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private final NotaRepo notaRepo;
    private final BloqueRepo bloqueRepo;
    private final InscripcionRepo inscRepo;
    private final SolicitudRepo solRepo;
    private final ReporteRepo repRepo;
    private final BoletinRepo bolRepo;
    private final UsuarioRepo usrRepo;
    private final AuditoriaRepo audRepo;
    private final FaseCargaRepo faseRepo;
    private final PlanillaCierreRepo cierreRepo;
    private final MailSrv mail;
    private final ObjectMapper mapper;
    private final AvisoSrv avisoSrv;

    @Value("${sgn.anio-activo:2026}")
    private int anioAct;

    private static final List<FasePlanilla> FASES_INFORME = List.of(
        FasePlanilla.BIM_1, FasePlanilla.BIM_2, FasePlanilla.FINAL_1, FasePlanilla.BIM_3,
        FasePlanilla.BIM_4, FasePlanilla.FINAL_2, FasePlanilla.CIERRE_1,
        FasePlanilla.CIERRE_2, FasePlanilla.CIERRE_3, FasePlanilla.CALIF_DEF);

    public NotaSrv(NotaRepo notaRepo, BloqueRepo bloqueRepo, InscripcionRepo inscRepo,
                   SolicitudRepo solRepo, ReporteRepo repRepo, BoletinRepo bolRepo,
                   UsuarioRepo usrRepo, AuditoriaRepo audRepo, FaseCargaRepo faseRepo,
                   PlanillaCierreRepo cierreRepo, MailSrv mail, ObjectMapper mapper, AvisoSrv avisoSrv) {
        this.notaRepo = notaRepo;
        this.bloqueRepo = bloqueRepo;
        this.inscRepo = inscRepo;
        this.solRepo = solRepo;
        this.repRepo = repRepo;
        this.bolRepo = bolRepo;
        this.usrRepo = usrRepo;
        this.audRepo = audRepo;
        this.faseRepo = faseRepo;
        this.cierreRepo = cierreRepo;
        this.mail = mail;
        this.mapper = mapper;
        this.avisoSrv = avisoSrv;
    }

    public int anioActivo() {
        return anioAct;
    }

public List<BloqueRes> bloquesProf(String legajo) {
    return bloqueRepo.findByProfLegajoAndAnio(legajo, anioAct).stream()
        .map(this::toBloque).toList();
}

    public List<BloqueRes> bloquesVer() {
        return bloqueRepo.findAll().stream()
            .filter(b -> b.getAnio() == anioAct)
            .map(this::toBloque).toList();
    }

    public List<AlumnoRes> alumnosBloque(long bloqueId, Usuario actor) {
        Bloque b = bloque(bloqueId);
        chkProf(b, actor);
        return inscRepo.findByCursoIdAndAnio(b.getCurso().getId(), b.getAnio()).stream()
            .filter(Inscripcion::isActiva)
            .map(i -> new AlumnoRes(i.getAlumno().getLegajo(), i.getAlumno().nomCompleto()))
            .sorted(Comparator.comparing(AlumnoRes::nomCompleto))
            .toList();
    }

    public List<FaseInfo> fases(int anio) {
        LocalDate hoy = LocalDate.now(ZoneId.of("America/Argentina/Buenos_Aires"));
        return FASES_INFORME.stream().map(f -> {
            var cfg = configuracionFase(anio, f);
            return new FaseInfo(f, f.etiqueta(), cfg != null ? cfg.getDesde().toString() : null,
                cfg != null ? cfg.getHasta().toString() : null,
                cfg != null && !hoy.isBefore(cfg.getDesde()) && !hoy.isAfter(cfg.getHasta()));
        }).toList();
    }

    @Transactional
    public FaseInfo configurarFase(int anio, FaseConfigReq req) {
        if (req.fase() == null || req.desde() == null || req.hasta() == null)
            throw new IllegalArgumentException("Completá fase y fechas");
        if (!FASES_INFORME.contains(req.fase()))
            throw new IllegalArgumentException("Los cuatrimestres se registran en la columna FINAL correspondiente");
        LocalDate desde;
        LocalDate hasta;
        try { desde = LocalDate.parse(req.desde()); hasta = LocalDate.parse(req.hasta()); }
        catch (Exception e) { throw new IllegalArgumentException("Las fechas deben tener formato AAAA-MM-DD"); }
        if (hasta.isBefore(desde)) throw new IllegalArgumentException("La fecha final no puede ser anterior a la inicial");
        for (FasePlanilla faseRelacionada : fasesMismoPeriodo(req.fase())) {
            var cfg = faseRepo.findByAnioAndFase(anio, faseRelacionada).orElseGet(FaseCarga::new);
            cfg.setAnio(anio); cfg.setFase(faseRelacionada); cfg.setDesde(desde); cfg.setHasta(hasta);
            faseRepo.save(cfg);
        }
        return fases(anio).stream().filter(x -> x.fase() == req.fase()).findFirst().orElseThrow();
    }

    public PlanillaRes planilla(long bloqueId, FasePlanilla fase, Usuario actor) {
        if (!FASES_INFORME.contains(fase)) throw new IllegalArgumentException("Fase no incluida en el informe anual");
        Bloque b = bloque(bloqueId);
        chkProf(b, actor);
        LocalDate hoy = LocalDate.now(ZoneId.of("America/Argentina/Buenos_Aires"));
        var cfg = configuracionFase(b.getAnio(), fase);
        boolean cerrada = cierreRepo.findByBloqueIdAndFase(bloqueId, fase).isPresent();
        List<PlanillaFila> filas = inscRepo.findByCursoIdAndAnio(b.getCurso().getId(), b.getAnio()).stream()
            .filter(Inscripcion::isActiva)
            .sorted(Comparator.comparing(i -> i.getAlumno().nomCompleto()))
            .map(i -> {
                Nota n = buscarNotaFase(bloqueId, i.getAlumno().getLegajo(), fase).orElse(null);
                return new PlanillaFila(i.getAlumno().getLegajo(), i.getAlumno().nomCompleto(),
                    n != null ? valorVisible(n) : "", n != null ? n.getId() : null,
                    n != null && n.isBorrador());
            }).toList();
        String estado = cerrada ? "CERRADA" : cfg == null ? "SIN_FECHAS"
            : hoy.isBefore(cfg.getDesde()) ? "PROGRAMADA" : hoy.isAfter(cfg.getHasta()) ? "VENCIDA" : "ABIERTA";
        return new PlanillaRes(bloqueId, b.getMateria().getNom(), b.getCurso().etiqueta(), b.getAnio(), fase,
            estado, cfg != null ? cfg.getDesde().toString() : null, cfg != null ? cfg.getHasta().toString() : null, filas);
    }

    @Transactional(readOnly = true)
    public PlanillaCompleta planillaCompleta(long bloqueId, Usuario actor) {
        Bloque b = bloque(bloqueId);
        chkProf(b, actor);
        return construirPlanillaCompleta(b, actor.getTipo() == TipoUsr.OFICINA || actor.getTipo() == TipoUsr.ADMIN);
    }

    public List<PlanillaCompleta> planillasCursoParaBoletin(String legajo, int anio) {
        var inscripciones = inscRepo.findByAlumnoLegajoAndAnioAndActivaTrue(legajo, anio);
        if (inscripciones.isEmpty()) throw new NoSuchElementException("El alumno no tiene curso activo para este ciclo lectivo");
        long cursoId = inscripciones.get(0).getCurso().getId();
        return bloqueRepo.findByCursoIdAndAnio(cursoId, anio).stream()
            .sorted(Comparator.comparing(b -> b.getMateria().getNom(), String.CASE_INSENSITIVE_ORDER))
            .map(b -> construirPlanillaCompleta(b, true))
            .toList();
    }

    private PlanillaCompleta construirPlanillaCompleta(Bloque b, boolean ocultarBorradores) {
        LocalDate hoy = LocalDate.now(ZoneId.of("America/Argentina/Buenos_Aires"));
        Map<FasePlanilla, FaseCarga> configuradas = faseRepo.findByAnioOrderById(b.getAnio()).stream()
            .collect(Collectors.toMap(FaseCarga::getFase, f -> f));
        Set<FasePlanilla> cerradas = cierreRepo.findByBloqueId(b.getId()).stream()
            .map(PlanillaCierre::getFase).collect(Collectors.toSet());
        List<PlanillaFase> fases = FASES_INFORME.stream().map(f -> {
            FaseCarga cfg = configuracionFase(configuradas, f);
            String estado = cerradas.contains(f) ? "CERRADA" : cfg == null ? "SIN_FECHAS"
                : hoy.isBefore(cfg.getDesde()) ? "PROGRAMADA" : hoy.isAfter(cfg.getHasta()) ? "VENCIDA" : "ABIERTA";
            return new PlanillaFase(f, f.etiqueta(), f.referencia(), cfg != null ? cfg.getDesde().toString() : null,
                cfg != null ? cfg.getHasta().toString() : null, estado);
        }).toList();

        Map<String, Map<FasePlanilla, Nota>> notasPorAlumno = new HashMap<>();
        for (Nota n : notaRepo.findByBloqueId(b.getId())) {
            if (ocultarBorradores && n.isBorrador()) continue;
            FasePlanilla f = n.getFase() != null ? n.getFase() : faseDe(n.getTipo(), n.getNumPer());
            if (f != null) notasPorAlumno.computeIfAbsent(n.getAlumno().getLegajo(), k -> new EnumMap<>(FasePlanilla.class))
                .putIfAbsent(f, n);
        }
        List<PlanillaAlumno> alumnos = inscRepo.findByCursoIdAndAnio(b.getCurso().getId(), b.getAnio()).stream()
            .filter(Inscripcion::isActiva)
            .sorted(Comparator.comparing(i -> i.getAlumno().nomCompleto()))
            .map(i -> {
                String legajo = i.getAlumno().getLegajo();
                Map<FasePlanilla, Nota> delAlumno = notasPorAlumno.getOrDefault(legajo, Map.of());
                Map<String, String> valores = new LinkedHashMap<>();
                for (FasePlanilla f : FASES_INFORME) {
                    Nota n = delAlumno.get(f);
                    valores.put(f.name(), n != null ? valorVisible(n) : "");
                }
                return new PlanillaAlumno(legajo, i.getAlumno().nomCompleto(), valores);
            }).toList();
        return new PlanillaCompleta(b.getId(), b.getMateria().getNom(), b.getCurso().etiqueta(), b.getAnio(), fases, alumnos);
    }

    public List<PlanillaCompleta> planillasCurso(long bloqueId, Usuario actor) {
        Bloque referencia = bloque(bloqueId);
        chkProf(referencia, actor);
        return bloqueRepo.findByCursoIdAndAnio(referencia.getCurso().getId(), referencia.getAnio()).stream()
            .sorted(Comparator.comparing(b -> b.getMateria().getNom(), String.CASE_INSENSITIVE_ORDER))
            .map(b -> planillaCompleta(b.getId(), actor))
            .toList();
    }

    @Transactional
    public PlanillaRes guardarPlanilla(PlanillaReq req, Usuario prof, boolean cerrar) {
        Bloque b = bloque(req.bloqueId());
        chkProf(b, prof);
        if (prof.getTipo() == TipoUsr.PROFESOR && !b.getProf().getLegajo().equals(prof.getLegajo()))
            throw new AccessDeniedException("Sin acceso al bloque");
        if (!FASES_INFORME.contains(req.fase()))
            throw new IllegalArgumentException("Los cuatrimestres se informan en la columna FINAL correspondiente");
        verificarFaseAbierta(b, req.fase());
        if (cierreRepo.findByBloqueIdAndFase(b.getId(), req.fase()).isPresent())
            throw new IllegalStateException("La planilla ya está cerrada; solicitá una modificación a Oficina");
        var valores = req.notas() == null ? List.<PlanillaValor>of() : req.notas();
        Set<String> legajos = new HashSet<>();
        for (PlanillaValor v : valores) {
            if (!legajos.add(v.legajo())) throw new IllegalArgumentException("Hay alumnos duplicados en la planilla");
            String nuevoVisible = normalizarNotaPlanilla(v.val());
            BigDecimal nuevoValor = parseValorNumerico(nuevoVisible);
            if (nuevoValor != null) NotaReglas.validarVal(nuevoValor);
            if (!nuevoVisible.isBlank() && !faseHabilitadaParaAlumno(b, req.fase(), v.legajo()))
                throw new IllegalStateException("El alumno todavía no está habilitado para esta instancia de cierre o calificación definitiva");
            var insc = inscRepo.findByAlumnoLegajoAndCursoIdAndAnioAndActivaTrue(v.legajo(), b.getCurso().getId(), b.getAnio())
                .orElseThrow(() -> new IllegalArgumentException("Un alumno no pertenece al curso de esta planilla"));
            Nota n = buscarNotaFase(b.getId(), v.legajo(), req.fase()).orElseGet(Nota::new);
            if (n.getId() == null && nuevoVisible.isBlank()) continue;
            BigDecimal anterior = n.getId() != null ? n.getVal() : null;
            String anteriorVisible = n.getId() != null ? valorVisible(n) : "";
            if (n.getId() != null && !n.isBorrador()) {
                if (!Objects.equals(anteriorVisible, nuevoVisible))
                    throw new IllegalStateException("Una nota ya entregada solo puede cambiarse mediante solicitud a Oficina");
                if (n.getFase() == null) { n.setFase(req.fase()); notaRepo.save(n); }
                continue;
            }
            n.setBloque(b); n.setAlumno(insc.getAlumno()); n.setTipo(req.fase().tipo()); n.setNumPer(req.fase().periodo());
            n.setFase(req.fase()); n.setTitulo(req.fase().etiqueta()); n.setVal(nuevoValor);
            n.setValorEspecial(Set.of("EP", "EN PROCESO", "NC", "NI").contains(nuevoVisible)
                ? ("EN PROCESO".equals(nuevoVisible) ? "EP" : nuevoVisible) : null);
            n.setEst(("EP".equals(nuevoVisible) || "EN PROCESO".equals(nuevoVisible))
                || (nuevoValor != null && calcEst(nuevoValor) == EstNota.EN_PROCESO)
                ? EstNota.EN_PROCESO : EstNota.NORMAL); n.setCargadoPor(prof);
            if (n.getFecCarga() == null) n.setFecCarga(LocalDateTime.now());
            n.setFecMod(LocalDateTime.now()); n.setBorrador(true);
            n = notaRepo.save(n);
            if (!Objects.equals(anteriorVisible, nuevoVisible)) auditar(prof, null, insc.getAlumno(), b.getMateria().getNom(),
                anterior, nuevoValor, "Edición de borrador " + req.fase().etiqueta() + ": " + (anteriorVisible.isBlank() ? "(vacío)" : anteriorVisible) + " → " + (nuevoVisible.isBlank() ? "(vacío)" : nuevoVisible), "BORRADOR", "EN_BORRADOR");
        }
        if (cerrar) {
            List<String> habilitados = inscRepo.findByCursoIdAndAnio(b.getCurso().getId(), b.getAnio()).stream()
                .filter(Inscripcion::isActiva)
                .map(i -> i.getAlumno().getLegajo())
                .filter(leg -> faseHabilitadaParaAlumno(b, req.fase(), leg)).toList();
            if (habilitados.isEmpty())
                throw new IllegalStateException("Todavía no hay alumnos habilitados para esta instancia");
            List<Nota> filas = habilitados.stream()
                .map(leg -> buscarNotaFase(b.getId(), leg, req.fase()).orElse(null))
                .filter(Objects::nonNull).toList();
            if (filas.size() != habilitados.size() || filas.stream().anyMatch(n -> n.getVal() == null && n.getValorEspecial() == null))
                throw new IllegalStateException("Completá la nota de cada alumno habilitado antes de entregar esta instancia");
            filas.forEach(n -> { n.setBorrador(false); n.setFecMod(LocalDateTime.now()); });
            notaRepo.saveAll(filas);
            var cierre = new PlanillaCierre(); cierre.setBloque(b); cierre.setFase(req.fase());
            cierre.setCerradoPor(prof); cierre.setCerradoEn(LocalDateTime.now()); cierreRepo.save(cierre);
            filas.forEach(n -> {
                auditarConTexto(prof, null, n.getAlumno(), b.getMateria().getNom(), req.fase().etiqueta(), null, n.getVal(), "",
                    valorVisible(n), "Entrega de notas: " + req.fase().etiqueta(), "CIERRE_PLANILLA", "CERRADA");
                genBol(n);
                avisoSrv.notaEntregada(n, req.fase());
            });
            avisoSrv.planillaEntregada(b, prof, req.fase());
        }
        return planilla(b.getId(), req.fase(), prof);
    }

    @Transactional
    public void guardarBorradores(List<PlanillaReq> planillas, Usuario prof) {
        if (planillas == null || planillas.isEmpty()) throw new IllegalArgumentException("No hay borradores para guardar");
        for (PlanillaReq planilla : planillas) guardarPlanilla(planilla, prof, false);
    }

    private void verificarFaseAbierta(Bloque b, FasePlanilla fase) {
        if (fase == null) throw new IllegalArgumentException("Seleccioná una fase");
        var cfg = Optional.ofNullable(configuracionFase(b.getAnio(), fase))
            .orElseThrow(() -> new IllegalStateException("Oficina todavía no configuró las fechas de esta fase"));
        LocalDate hoy = LocalDate.now(ZoneId.of("America/Argentina/Buenos_Aires"));
        if (hoy.isBefore(cfg.getDesde()) || hoy.isAfter(cfg.getHasta()))
            throw new IllegalStateException("La carga de esta fase solo está habilitada entre " + cfg.getDesde() + " y " + cfg.getHasta());
    }

    private FaseCarga configuracionFase(int anio, FasePlanilla fase) {
        return faseRepo.findByAnioAndFase(anio, faseFechaReferencia(fase))
            .or(() -> faseFechaReferencia(fase) == fase ? Optional.empty() : faseRepo.findByAnioAndFase(anio, fase))
            .orElse(null);
    }

    private FaseCarga configuracionFase(Map<FasePlanilla, FaseCarga> configuradas, FasePlanilla fase) {
        FasePlanilla referencia = faseFechaReferencia(fase);
        FaseCarga cfg = configuradas.get(referencia);
        return cfg != null || referencia == fase ? cfg : configuradas.get(fase);
    }

    private FasePlanilla faseFechaReferencia(FasePlanilla fase) {
        return switch (fase) {
            case FINAL_1 -> FasePlanilla.BIM_2;
            case FINAL_2 -> FasePlanilla.BIM_4;
            default -> fase;
        };
    }

    private List<FasePlanilla> fasesMismoPeriodo(FasePlanilla fase) {
        return switch (fase) {
            case BIM_2, FINAL_1 -> List.of(FasePlanilla.BIM_2, FasePlanilla.FINAL_1);
            case BIM_4, FINAL_2 -> List.of(FasePlanilla.BIM_4, FasePlanilla.FINAL_2);
            default -> List.of(fase);
        };
    }

    private Optional<Nota> buscarNotaFase(long bloqueId, String legajo, FasePlanilla fase) {
        var nota = notaRepo.findByBloqueIdAndAlumnoLegajoAndFase(bloqueId, legajo, fase);
        return nota.isPresent() ? nota : notaRepo.findByBloqueIdAndAlumnoLegajoAndTipoAndNumPer(
            bloqueId, legajo, fase.tipo(), fase.periodo());
    }

    private String normalizarNotaPlanilla(String valor) {
        if (valor == null || valor.isBlank()) return "";
        String limpio = valor.trim().toUpperCase(Locale.ROOT);
        if (Set.of("EP", "EN PROCESO", "NC", "NI").contains(limpio))
            return "EN PROCESO".equals(limpio) ? "EP" : limpio;
        try {
            BigDecimal numero = NotaReglas.redondearVal(new BigDecimal(limpio));
            return numero.toPlainString();
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Ingresá una nota entre 1 y 10, o EP, NI o NC");
        }
    }

    private BigDecimal parseValorNumerico(String valor) {
        if (valor == null || valor.isBlank() || Set.of("EP", "EN PROCESO", "NC", "NI").contains(valor)) return null;
        return new BigDecimal(valor);
    }

    private String valorVisible(Nota n) {
        return n.getValorEspecial() != null ? visibleEspecial(n.getValorEspecial())
            : n.getVal() != null ? n.getVal().setScale(0, RoundingMode.HALF_UP).toPlainString() : "";
    }

    private boolean faseHabilitadaParaAlumno(Bloque bloque, FasePlanilla fase, String legajo) {
        return switch (fase) {
            case CIERRE_1 -> faseCerrada(bloque, FasePlanilla.FINAL_2)
                && notaDesaprobada(buscarNotaFase(bloque.getId(), legajo, FasePlanilla.FINAL_2).orElse(null));
            case CIERRE_2 -> faseCerrada(bloque, FasePlanilla.CIERRE_1)
                && notaDesaprobada(buscarNotaFase(bloque.getId(), legajo, FasePlanilla.CIERRE_1).orElse(null));
            case CIERRE_3 -> faseCerrada(bloque, FasePlanilla.CIERRE_2)
                && notaDesaprobada(buscarNotaFase(bloque.getId(), legajo, FasePlanilla.CIERRE_2).orElse(null));
            case CALIF_DEF -> (faseCerrada(bloque, FasePlanilla.FINAL_2)
                    && notaAprobada(buscarNotaFase(bloque.getId(), legajo, FasePlanilla.FINAL_2).orElse(null)))
                || (faseCerrada(bloque, FasePlanilla.CIERRE_1)
                    && notaAprobada(buscarNotaFase(bloque.getId(), legajo, FasePlanilla.CIERRE_1).orElse(null)))
                || (faseCerrada(bloque, FasePlanilla.CIERRE_2)
                    && notaAprobada(buscarNotaFase(bloque.getId(), legajo, FasePlanilla.CIERRE_2).orElse(null)))
                || (faseCerrada(bloque, FasePlanilla.CIERRE_3)
                    && notaAprobada(buscarNotaFase(bloque.getId(), legajo, FasePlanilla.CIERRE_3).orElse(null)));
            default -> true;
        };
    }

    private boolean faseCerrada(Bloque bloque, FasePlanilla fase) {
        return cierreRepo.findByBloqueIdAndFase(bloque.getId(), fase).isPresent();
    }

    private boolean notaAprobada(Nota nota) {
        return nota != null && nota.getVal() != null && nota.getVal().compareTo(BigDecimal.valueOf(6)) > 0;
    }

    private boolean notaDesaprobada(Nota nota) {
        return nota != null && (("EP".equals(nota.getValorEspecial()) || "EN PROCESO".equals(nota.getValorEspecial()))
            || (nota.getVal() != null && nota.getVal().compareTo(BigDecimal.valueOf(6)) <= 0));
    }

    private String visibleEspecial(String valor) {
        return "EP".equals(valor) ? "EP" : valor;
    }

    private FasePlanilla faseDe(TipoNota tipo, int periodo) {
        return switch (tipo) {
            case BIM -> switch (periodo) { case 1 -> FasePlanilla.BIM_1; case 2 -> FasePlanilla.BIM_2; case 3 -> FasePlanilla.BIM_3; case 4 -> FasePlanilla.BIM_4; default -> null; };
            case CUAT -> null;
            case FINAL -> switch (periodo) { case 1 -> FasePlanilla.FINAL_1; case 2 -> FasePlanilla.FINAL_2; case 3 -> FasePlanilla.CALIF_DEF; default -> null; };
            case DIC -> FasePlanilla.CIERRE_1;
            case FEB -> periodo == 1 ? FasePlanilla.CIERRE_2 : periodo == 2 ? FasePlanilla.CIERRE_3 : null;
            default -> null;
        };
    }

    private boolean esFaseMasRecienteCargada(Nota nota) {
        int solicitada = ordenFaseNota(nota);
        if (solicitada < 0) return false;
        int masReciente = notaRepo.findByBloqueIdAndAlumnoLegajo(
                nota.getBloque().getId(), nota.getAlumno().getLegajo()).stream()
            .filter(n -> !n.isBorrador())
            .mapToInt(this::ordenFaseNota).max().orElse(-1);
        return solicitada == masReciente;
    }

    private boolean puedeReportarError(Nota nota) {
        if (nota == null || nota.isBorrador() || faseDeNota(nota) == null
            || !FASES_INFORME.contains(faseDeNota(nota))
            || (nota.getVal() == null && nota.getValorEspecial() == null)) return false;
        return cierreRepo.findByBloqueId(nota.getBloque().getId()).stream()
            .max(Comparator.comparing(PlanillaCierre::getCerradoEn))
            .map(cierre -> cierre.getFase() == faseDeNota(nota))
            .orElse(false);
    }

    private int ordenFaseNota(Nota nota) {
        FasePlanilla fase = faseDeNota(nota);
        return fase == null ? -1 : FASES_INFORME.indexOf(fase);
    }

    private FasePlanilla faseDeNota(Nota nota) {
        FasePlanilla fase = nota.getFase() != null ? nota.getFase() : faseDe(nota.getTipo(), nota.getNumPer());
        if (fase == null && nota.getTipo() == TipoNota.CUAT) {
            fase = nota.getNumPer() == 1 ? FasePlanilla.FINAL_1 : nota.getNumPer() == 2 ? FasePlanilla.FINAL_2 : null;
        }
        if (fase == FasePlanilla.CUAT_1) fase = FasePlanilla.FINAL_1;
        if (fase == FasePlanilla.CUAT_2) fase = FasePlanilla.FINAL_2;
        return fase;
    }

    public List<NotaRes> notasAlumnoBloque(long bloqueId, String alumLeg, Usuario actor) {
        Bloque b = bloque(bloqueId);
        chkAccesoNotas(b, actor, alumLeg);
        return notaRepo.findByBloqueIdAndAlumnoLegajo(bloqueId, alumLeg).stream()
            .filter(n -> !n.isBorrador())
            .filter(n -> faseDeNota(n) != null && FASES_INFORME.contains(faseDeNota(n)))
            .map(this::toNota).toList();
    }

    @Transactional
    public NotaRes cargar(NotaReq req, Usuario prof) {
        Bloque b = bloque(req.bloqueId());
        chkProf(b, prof);
        if (prof.getTipo() == TipoUsr.PROFESOR && !b.getProf().getLegajo().equals(prof.getLegajo())) {
            throw new AccessDeniedException("Sin acceso al bloque");
        }
        BigDecimal valorRedondeado = NotaReglas.redondearVal(req.val());
        TipoNota tipo;
        try {
            tipo = TipoNota.valueOf(req.tipo());
        } catch (Exception e) {
            throw new IllegalArgumentException("Tipo de evaluación inválido");
        }
        if (tipo == TipoNota.PRUEBA || tipo == TipoNota.TRABAJO)
            throw new IllegalStateException("No se cargan evaluaciones individuales; usá la planilla por fases");
        FasePlanilla faseResumen = faseDe(tipo, req.numPer());
        if (faseResumen != null)
            throw new IllegalStateException("Las notas de período se cargan desde la planilla del bloque, no alumno por alumno");
        Usuario alum = alumno(req.legajo());
        if (alum.getTipo() != TipoUsr.ALUMNO) {
            throw new IllegalArgumentException("El legajo no corresponde a un alumno");
        }
        inscRepo.findByAlumnoLegajoAndCursoIdAndAnioAndActivaTrue(
    alum.getLegajo(),
    b.getCurso().getId(),
    b.getAnio()
)
.orElseThrow(() -> new IllegalArgumentException(
    "El alumno no está inscripto actualmente en este curso/año"
));
        if (tipo == TipoNota.BIM || tipo == TipoNota.CUAT || tipo == TipoNota.FINAL) {
            var exist = notaRepo.findByBloqueIdAndAlumnoLegajoAndTipoAndNumPer(
                req.bloqueId(), req.legajo(), tipo, req.numPer());
            if (exist.isPresent()) {
                throw new IllegalStateException("La nota ya está cargada. Solicitá una modificación.");
            }
        }

        Nota n = new Nota();
        n.setBloque(b);
        n.setAlumno(alum);
        n.setTipo(tipo);
        n.setNumPer(req.numPer());
        n.setFase(faseResumen);
        n.setBorrador(false);
        n.setTitulo(req.titulo());
        n.setVal(valorRedondeado);
        n.setEst(calcEst(valorRedondeado));
        n.setCargadoPor(prof);
        n.setFecCarga(LocalDateTime.now());
        n.setFecMod(LocalDateTime.now());
        n = notaRepo.save(n);

        auditar(prof, null, alum, b.getMateria().getNom(), null, n.getVal(),
            "Carga inicial", "CARGA", n.getEst().name());

        var det = tipo + " " + req.numPer() + ": " + fmtVal(n);
        mail.notaAlumno(n.getAlumno().getMail(), n.getAlumno().getMailTutor(), b.getMateria().getNom(), det);

        if (tipo == TipoNota.BIM || tipo == TipoNota.CUAT || tipo == TipoNota.FINAL || tipo == TipoNota.DIC || tipo == TipoNota.FEB) {
            genBol(n);
        }
        return toNota(n);
    }

    @Transactional
    public SolRes solCambio(SolReq req, Usuario prof) {
        String motivo = motivoObligatorio(req.motivo());
        Nota n = notaRepo.findById(req.notaId()).orElseThrow(() -> new NoSuchElementException("Nota no encontrada"));
        chkProf(n.getBloque(), prof);
        if (prof.getTipo() == TipoUsr.PROFESOR && !n.getBloque().getProf().getLegajo().equals(prof.getLegajo())) {
            throw new AccessDeniedException("No puede modificar notas de otro docente");
        }
        if (n.isBorrador()) {
            throw new IllegalStateException("Los borradores se corrigen directamente desde la planilla");
        }
        if (!esFaseMasRecienteCargada(n)) {
            throw new IllegalStateException("Solo se puede solicitar la modificación de la fase más reciente cargada; las fases anteriores quedan fijas");
        }
        String valorNuevoTexto = normalizarValorSolicitud(req.valNuevo());
        BigDecimal valorNuevoRedondeado = parseValorNumerico(valorNuevoTexto);
        if (solRepo.existsByNotaIdAndEst(n.getId(), EstSol.PEND_OFICINA)) {
            throw new IllegalStateException("Ya hay una solicitud pendiente para esta nota");
        }
        var s = new Solicitud();
        s.setNota(n);
        s.setSolicitante(prof);
        s.setMotivo(motivo);
        s.setValActual(n.getVal());
        s.setValActualTexto(valorVisible(n));
        s.setValNuevo(valorNuevoRedondeado);
        s.setValNuevoTexto(valorNuevoTexto);
        s.setEst(EstSol.PEND_OFICINA);
        s.setFecSol(LocalDateTime.now());
        s = solRepo.save(s);
        auditarConTexto(prof, null, n.getAlumno(), n.getBloque().getMateria().getNom(),
            faseDeNota(n) != null ? faseDeNota(n).etiqueta() : "",
            n.getVal(), valorNuevoRedondeado, valorVisible(n), valorNuevoTexto,
            motivo, "SOLICITUD", EstSol.PEND_OFICINA.name());
        avisoSrv.solicitudAOficina(s);
        usrRepo.findByTipo(TipoUsr.OFICINA).forEach(o ->
            mail.solOficina(o.getMail(), prof.nomCompleto(), n.getBloque().getMateria().getNom(),
                motivo, valorVisible(n), valorNuevoTexto)
        );
        return toSol(s);
    }

    @Transactional
    public NotaRes aplicarSol(long solId, Usuario prof) {
        Solicitud s = solRepo.findById(solId).orElseThrow(() -> new NoSuchElementException("Solicitud no encontrada"));
        if (s.getEst() != EstSol.AUTORIZADA) {
            throw new IllegalStateException("Solicitud no autorizada");
        }
        chkProf(s.getNota().getBloque(), prof);
        aplicarCambio(s, prof);
        return toNota(s.getNota());
    }

    public List<SolRes> pendOficina(boolean todas) {
        var list = todas
            ? solRepo.findAllByOrderByFecSolDesc()
            : solRepo.findByEstInOrderByFecSolDesc(List.of(EstSol.PEND_OFICINA, EstSol.PEND_REVISION, EstSol.AUTORIZADA));
        return list.stream().map(this::toSol).toList();
    }

    public List<SolRes> solsProf(String legajo) {
        return solRepo.findBySolicitanteLegajoOrderByFecSolDesc(legajo).stream().map(this::toSol).toList();
    }

    @Transactional
    public SolRes resolverSol(long id, boolean autoriza, Usuario oficina) {
        Solicitud s = solRepo.findById(id).orElseThrow(() -> new NoSuchElementException("Solicitud no encontrada"));
        if (s.getEst() != EstSol.PEND_OFICINA && s.getEst() != EstSol.PEND_REVISION && s.getEst() != EstSol.AUTORIZADA) {
            throw new IllegalStateException("La solicitud ya fue procesada");
        }
        s.setAutorizo(oficina);
        s.setFecRes(LocalDateTime.now());
        if (autoriza) {
            aplicarCambio(s, oficina);
        } else {
            s.setEst(EstSol.RECHAZADA);
            solRepo.save(s);
            auditar(s.getSolicitante(), oficina, s.getNota().getAlumno(),
                s.getNota().getBloque().getMateria().getNom(), s.getValActual(), s.getValNuevo(),
                s.getMotivo(), "RECHAZO", EstSol.RECHAZADA.name());
            avisoSrv.solicitudResuelta(s, false, valorVisible(s.getNota()), valorVisible(s.getNota()));
        }
        cerrarReportesDeNota(s.getNota().getId());
        return toSol(s);
    }

    private void aplicarCambio(Solicitud s, Usuario autorizo) {
        Nota n = s.getNota();
        BigDecimal ant = n.getVal();
        String antVisible = valorVisible(n);
        String valorNuevo = valorNuevoSolicitud(s);
        n.setVal(s.getValNuevo());
        n.setValorEspecial(Set.of("EP", "NI", "NC").contains(valorNuevo) ? valorNuevo : null);
        n.setEst("EP".equals(valorNuevo) || (s.getValNuevo() != null && calcEst(s.getValNuevo()) == EstNota.EN_PROCESO)
            ? EstNota.EN_PROCESO : EstNota.NORMAL);
        n.setFecMod(LocalDateTime.now());
        notaRepo.save(n);
        s.setEst(EstSol.APLICADA);
        s.setAutorizo(autorizo);
        s.setFecRes(LocalDateTime.now());
        solRepo.save(s);
        auditarConTexto(s.getSolicitante(), autorizo, n.getAlumno(), n.getBloque().getMateria().getNom(),
            faseDeNota(n) != null ? faseDeNota(n).etiqueta() : "",
            ant, s.getValNuevo(), antVisible, valorVisible(n), s.getMotivo(), "APROBACION", EstSol.APLICADA.name());
        avisoSrv.solicitudResuelta(s, true, antVisible, valorVisible(n));
        mail.notaAlumno(n.getAlumno().getMail(), n.getAlumno().getMailTutor(),
            n.getBloque().getMateria().getNom(), "Modificada: " + valorVisible(n));
        if (n.getTipo() == TipoNota.BIM || n.getTipo() == TipoNota.CUAT || n.getTipo() == TipoNota.FINAL) {
            genBol(n);
        }
    }

    @Transactional
    public Reporte repError(RepReq req, Usuario alum) {
        String motivo = motivoObligatorio(req.motivo());
        Nota n = notaRepo.findById(req.notaId()).orElseThrow(() -> new NoSuchElementException("Nota no encontrada"));
        if (!n.getAlumno().getLegajo().equals(alum.getLegajo())) {
            throw new AccessDeniedException("No puede reportar esta nota");
        }
        if (!puedeReportarError(n)) {
            throw new IllegalStateException("Solo podés reportar notas de la planilla que se cerró más recientemente");
        }
        if (!repRepo.findActivosByAlumnoAndProfesor(alum.getLegajo(), n.getBloque().getProf().getLegajo(),
                List.of(EstRep.PENDIENTE, EstRep.DOC_SOLICITA)).isEmpty()) {
            throw new IllegalStateException("Ya tenés un reporte pendiente con este docente. Esperá a que se resuelva antes de enviar otro.");
        }
        var r = new Reporte();
        r.setNota(n);
        r.setAlumno(alum);
        r.setMotivo(motivo);
        r.setEst(EstRep.PENDIENTE);
        r.setFecRep(LocalDateTime.now());
        r = repRepo.save(r);
        avisoSrv.reporteNuevo(r);
        mail.enviar(n.getBloque().getProf().getMail(), "SGN - Reporte de error en nota",
            "Alumno " + alum.nomCompleto() + " reportó error en " + n.getBloque().getMateria().getNom()
                + "\nMotivo: " + motivo + "\nNota actual: " + fmtVal(n));
        return r;
    }

    public List<Long> notasReportablesAlumno(String legajo) {
        return notaRepo.findByAlumnoLegajoAndBloqueAnio(legajo, anioAct).stream()
            .filter(this::puedeReportarError)
            .filter(n -> repRepo.findActivosByAlumnoAndProfesor(legajo,
                n.getBloque().getProf().getLegajo(), List.of(EstRep.PENDIENTE, EstRep.DOC_SOLICITA)).isEmpty())
            .map(Nota::getId)
            .toList();
    }

    public List<RepRes> repProf(String legajo) {
        return repRepo.findByNotaBloqueProfLegajoAndEst(legajo, EstRep.PENDIENTE)
            .stream().map(this::toRep).toList();
    }

    @Transactional
    public SolRes repDocSolicita(long repId, SolReq req, Usuario prof) {
        Reporte r = repRepo.findById(repId).orElseThrow(() -> new NoSuchElementException("Reporte no encontrado"));
        chkProf(r.getNota().getBloque(), prof);
        var sol = solCambio(new SolReq(r.getNota().getId(), req.motivo(), req.valNuevo()), prof);
        Solicitud solicitud = solRepo.findById(sol.id()).orElseThrow();
        solicitud.setReporte(r);
        solRepo.save(solicitud);
        r.setEst(EstRep.DOC_SOLICITA);
        repRepo.save(r);
        avisoSrv.rectificacionDerivada(r, solicitud);
        return sol;
    }

    @Transactional
    public void cerrarReporte(long repId, Usuario prof) {
        Reporte r = repRepo.findById(repId).orElseThrow(() -> new NoSuchElementException("Reporte no encontrado"));
        chkProf(r.getNota().getBloque(), prof);
        if (r.getEst() != EstRep.PENDIENTE) {
            throw new IllegalStateException("El reporte ya fue procesado");
        }
        r.setEst(EstRep.CERRADO);
        repRepo.save(r);
        avisoSrv.reporteDesestimado(r);
    }

    private void cerrarReportesDeNota(Long notaId) {
        List<Reporte> reportes = repRepo.findByNotaIdAndEstIn(notaId, List.of(EstRep.PENDIENTE, EstRep.DOC_SOLICITA));
        reportes.forEach(r -> r.setEst(EstRep.CERRADO));
        repRepo.saveAll(reportes);
    }

    public List<BloqueRes> materiasAlumno(String legajo) {

    var cursoIds = inscRepo.findByAlumnoLegajoAndAnioAndActivaTrue(
        legajo,
        anioAct
    ).stream()
        .map(i -> i.getCurso().getId())
        .collect(Collectors.toSet());

    return bloqueRepo.findAll().stream()
        .filter(b -> b.getAnio() == anioAct && cursoIds.contains(b.getCurso().getId()))
        .map(this::toBloque)
        .toList();
}

    public RendRes rendimiento(String legajo) {
        var mats = new ArrayList<MateriaRend>();
        var perMap = new LinkedHashMap<String, List<Double>>();
        double sum = 0;
        int cnt = 0;
        for (BloqueRes b : materiasAlumno(legajo)) {
            var notas = notaRepo.findByBloqueIdAndAlumnoLegajo(b.id(), legajo).stream()
                .filter(n -> !n.isBorrador())
                .toList();
            double prom = promNoOf(notas);
            double pct = prom > 0 ? (prom / 10.0) * 100 : 0;
            String est = estadoMateria(notas);
            mats.add(new MateriaRend(b.materia(), round2(prom), round2(pct), est));
            if (prom > 0) { sum += prom; cnt++; }
            for (Nota n : notas) {
                if (n.getVal() == null) continue;
                if (n.getTipo() == TipoNota.BIM || n.getTipo() == TipoNota.CUAT || n.getTipo() == TipoNota.FINAL) {
                    String k = n.getTipo().name() + "-" + n.getNumPer();
                    perMap.computeIfAbsent(k, x -> new ArrayList<>()).add(n.getVal().doubleValue());
                }
            }
        }
        var periodos = perMap.entrySet().stream()
            .map(e -> {
                String[] p = e.getKey().split("-");
                double avg = e.getValue().stream().mapToDouble(v -> v).average().orElse(0);
                return new PeriodoRend(p[0], Integer.parseInt(p[1]), round2(avg));
            }).toList();
        double general = cnt > 0 ? sum / cnt : 0;
        return new RendRes(round2(general > 0 ? (general / 10.0) * 100 : 0), round2(general), mats, periodos);
    }

    public List<NotaRes> notasMateriaAlumno(long bloqueId, String legajo) {
        return notaRepo.findByBloqueIdAndAlumnoLegajo(bloqueId, legajo).stream()
            .map(this::toNota).toList();
    }

    public List<BolRes> boletines(String legajo) {
        return bolRepo.findByAlumnoLegajoOrderByAnioDescFecGenDesc(legajo).stream()
            .map(this::toBol).toList();
    }

    public List<NotaRes> todasNotasAnio(int anio) {
        return notaRepo.findAll().stream()
            .filter(n -> n.getBloque().getAnio() == anio)
            .filter(n -> faseDeNota(n) != null && FASES_INFORME.contains(faseDeNota(n)))
            .map(this::toNota).toList();
    }

    public List<NotaRes> historialAlumno(String legajo) {
        return notaRepo.findByAlumnoLegajoAndBloqueAnio(legajo, anioAct).stream()
            .filter(n -> !n.isBorrador())
            .filter(n -> faseDeNota(n) != null && FASES_INFORME.contains(faseDeNota(n)))
            .map(this::toNota).toList();
    }

    public List<AudRes> auditoria() {
        return audRepo.findAllByOrderByFecDesc().stream()
            .filter(a -> !"BORRADOR".equals(a.getAccion()) && !"EN_BORRADOR".equals(a.getEst()))
            .map(this::toAud).toList();
    }

    @Transactional(readOnly = true)
    public List<AudRes> cambiosAlumno(String legajo) {
        var solicitudesAplicadas = solRepo.findAllByOrderByFecSolDesc().stream()
            .filter(s -> s.getEst() == EstSol.APLICADA)
            .filter(s -> s.getNota().getAlumno().getLegajo().equals(legajo))
            .toList();
        return audRepo.findByAlumnoLegajoAndAccionOrderByFecDesc(legajo, "APROBACION").stream()
            .map(a -> toAud(a, periodoAuditoria(a, solicitudesAplicadas))).toList();
    }

    private String periodoAuditoria(Auditoria auditoria, List<Solicitud> solicitudes) {
        if (auditoria.getPeriodo() != null && !auditoria.getPeriodo().isBlank()) return auditoria.getPeriodo();
        if (auditoria.getFec() == null) return null;
        return solicitudes.stream()
            .filter(s -> s.getNota().getBloque().getMateria().getNom().equalsIgnoreCase(auditoria.getMateria()))
            .filter(s -> s.getMotivo().equals(auditoria.getMotivo()))
            .filter(s -> s.getValNuevo() != null && auditoria.getValNuevo() != null
                && s.getValNuevo().compareTo(auditoria.getValNuevo()) == 0)
            .filter(s -> s.getFecRes() != null
                && Math.abs(java.time.Duration.between(s.getFecRes(), auditoria.getFec()).toMillis()) <= 5000)
            .min(Comparator.comparingLong(s -> Math.abs(java.time.Duration.between(s.getFecRes(), auditoria.getFec()).toMillis())))
            .map(s -> faseDeNota(s.getNota()) != null ? faseDeNota(s.getNota()).etiqueta() : null)
            .orElse(null);
    }

    public PerfilRes perfil(Usuario u) {
        String curso = "";
        int anio = anioAct;
        if (u.getTipo() == TipoUsr.ALUMNO) {
            var insc = inscRepo.findByAlumnoLegajoAndAnioAndActivaTrue(
    u.getLegajo(),
    anioAct
);

if (!insc.isEmpty()) {
    curso = insc.get(0).getCurso().etiqueta();
    anio = insc.get(0).getAnio();
}
        }
        return new PerfilRes(u.getLegajo(), u.getCuil(), u.nomCompleto(), u.getMail(), u.getTipo(), curso, anio);
    }

    private void genBol(Nota n) {
        try {
            var alum = n.getAlumno();
            var notas = notaRepo.findByAlumnoLegajoAndBloqueAnio(alum.getLegajo(), n.getBloque().getAnio());
            TipoBol tb = switch (n.getTipo()) {
                case BIM -> TipoBol.BIM;
                case CUAT -> TipoBol.CUAT;
                case FINAL -> TipoBol.FINAL;
                default -> null;
            };
            if (tb == null) return;
            var filtradas = notas.stream()
                .filter(x -> esBol(x, tb, n.getNumPer()))
                .map(x -> Map.of(
                    "materia", x.getBloque().getMateria().getNom(),
                    "tipo", x.getTipo().name(),
                    "numPer", x.getNumPer(),
                    "val", valorVisible(x),
                    "est", x.getEst().name()
                )).toList();
            var json = mapper.writeValueAsString(filtradas);
            var bol = new Boletin();
            bol.setAlumno(alum);
            bol.setAnio(n.getBloque().getAnio());
            bol.setTipoPer(tb);
            bol.setNumPer(n.getNumPer());
            bol.setContenido(json);
            bol.setFecGen(LocalDateTime.now());
            bolRepo.save(bol);
        } catch (Exception e) {
            throw new RuntimeException("Error generando boletín", e);
        }
    }

    private boolean esBol(Nota x, TipoBol tb, int numPer) {
        return switch (tb) {
            case BIM -> x.getTipo() == TipoNota.BIM && x.getNumPer() == numPer;
            case CUAT -> x.getTipo() == TipoNota.CUAT && x.getNumPer() == numPer;
            case FINAL -> x.getTipo() == TipoNota.FINAL;
        };
    }

    private double promNoOf(List<Nota> notas) {
        Map<FasePlanilla, Nota> porFase = notas.stream()
            .filter(n -> !n.isBorrador())
            .filter(n -> faseDeNota(n) != null && FASES_INFORME.contains(faseDeNota(n)))
            .collect(Collectors.toMap(this::faseDeNota, n -> n, (a, b) -> {
                LocalDateTime aFecha = a.getFecMod() != null ? a.getFecMod() : a.getFecCarga();
                LocalDateTime bFecha = b.getFecMod() != null ? b.getFecMod() : b.getFecCarga();
                return bFecha != null && (aFecha == null || bFecha.isAfter(aFecha)) ? b : a;
            }, () -> new EnumMap<>(FasePlanilla.class)));
        Double score = puntajeActual(porFase);
        return score == null ? 0 : score;
    }

    private Double puntajeActual(Map<FasePlanilla, Nota> notas) {
        for (FasePlanilla fase : List.of(FasePlanilla.CALIF_DEF, FasePlanilla.CIERRE_3,
                FasePlanilla.CIERRE_2, FasePlanilla.CIERRE_1, FasePlanilla.FINAL_2)) {
            if (notas.containsKey(fase)) return puntaje(notas.get(fase));
        }
        if (notas.containsKey(FasePlanilla.BIM_3) || notas.containsKey(FasePlanilla.BIM_4))
            return promedioFases(notas, FasePlanilla.BIM_3, FasePlanilla.BIM_4);
        if (notas.containsKey(FasePlanilla.FINAL_1)) return puntaje(notas.get(FasePlanilla.FINAL_1));
        return promedioFases(notas, FasePlanilla.BIM_1, FasePlanilla.BIM_2);
    }

    private Double promedioFases(Map<FasePlanilla, Nota> notas, FasePlanilla a, FasePlanilla b) {
        List<Double> scores = List.of(a, b).stream().map(notas::get).filter(Objects::nonNull)
            .map(this::puntaje).filter(Objects::nonNull).toList();
        return scores.isEmpty() ? null : scores.stream().mapToDouble(Double::doubleValue).average().orElse(0);
    }

    private Double puntaje(Nota nota) {
        return nota == null || nota.getVal() == null ? null : nota.getVal().doubleValue();
    }

    private String estadoMateria(List<Nota> notas) {
        boolean tieneCalificacionOficial = notas.stream().anyMatch(n -> !n.isBorrador()
            && faseDeNota(n) != null && FASES_INFORME.contains(faseDeNota(n))
            && (n.getVal() != null || n.getValorEspecial() != null));
        if (!tieneCalificacionOficial) return "SIN_NOTAS";
        return notas.stream()
            .filter(n -> !n.isBorrador())
            .filter(n -> faseDeNota(n) == FasePlanilla.CALIF_DEF)
            .max(Comparator.comparing(n -> n.getFecMod() != null ? n.getFecMod() : n.getFecCarga()))
            .filter(n -> n.getVal() != null && n.getVal().compareTo(BigDecimal.valueOf(6)) > 0)
            .map(n -> "APROBADA")
            .orElse("CURSANDO");
    }

    private EstNota calcEst(BigDecimal val) {
        return NotaReglas.enProceso(val) ? EstNota.EN_PROCESO : EstNota.NORMAL;
    }

    private Bloque bloque(long id) {
        return bloqueRepo.findById(id).orElseThrow(() -> new NoSuchElementException("Bloque no encontrado"));
    }

    private Usuario alumno(String leg) {
        return usrRepo.findById(leg).orElseThrow(() -> new NoSuchElementException("Alumno no encontrado"));
    }

    private void chkProf(Bloque b, Usuario u) {
        if (u.getTipo() == TipoUsr.ADMIN || u.getTipo() == TipoUsr.OFICINA
            || u.getTipo() == TipoUsr.PRECEPTOR || u.getTipo() == TipoUsr.DIRECTIVO) return;
        if (u.getTipo() == TipoUsr.PROFESOR && b.getProf().getLegajo().equals(u.getLegajo())) return;
        throw new AccessDeniedException("Sin acceso al bloque");
    }

private void chkAccesoNotas(Bloque b, Usuario u, String alumLeg) {

    if (u.getTipo() == TipoUsr.ALUMNO) {

        if (!u.getLegajo().equals(alumLeg)) {
            throw new AccessDeniedException(
                "No puede consultar datos de otro alumno"
            );
        }

        inscRepo.findByAlumnoLegajoAndCursoIdAndAnioAndActivaTrue(
            alumLeg,
            b.getCurso().getId(),
            b.getAnio()
        ).orElseThrow(() -> new AccessDeniedException(
            "Sin acceso: el alumno no está inscripto actualmente en este curso/año"
        ));

        return;
    }

    chkProf(b, u);
}

    private void auditar(Usuario actor, Usuario autorizo, Usuario alumno, String materia,
                         BigDecimal ant, BigDecimal neu, String motivo, String accion, String est) {
        var a = new Auditoria();
        a.setActor(actor);
        a.setAutorizo(autorizo);
        a.setAlumno(alumno);
        a.setMateria(materia);
        a.setValAnt(ant);
        a.setValNuevo(neu);
        a.setMotivo(motivo);
        a.setAccion(accion);
        a.setEst(est);
        a.setFec(LocalDateTime.now());
        audRepo.save(a);
    }

    private void auditarConTexto(Usuario actor, Usuario autorizo, Usuario alumno, String materia,
                                 String periodo,
                                 BigDecimal ant, BigDecimal neu, String antTexto, String nuevoTexto,
                                 String motivo, String accion, String est) {
        var a = new Auditoria();
        a.setActor(actor); a.setAutorizo(autorizo); a.setAlumno(alumno); a.setMateria(materia);
        a.setPeriodo(periodo);
        a.setValAnt(ant); a.setValNuevo(neu); a.setValAntTxt(antTexto); a.setValNuevoTxt(nuevoTexto);
        a.setMotivo(motivo); a.setAccion(accion); a.setEst(est); a.setFec(LocalDateTime.now());
        audRepo.save(a);
    }

    private BloqueRes toBloque(Bloque b) {
        return new BloqueRes(b.getId(), b.titulo(), b.getMateria().getId(), b.getMateria().getNom(),
            b.getCurso().getId(), b.getCurso().etiqueta(), b.getAnio());
    }

    private NotaRes toNota(Nota n) {
        String val = n.getValorEspecial() != null ? visibleEspecial(n.getValorEspecial()) : n.getEst() == EstNota.EN_PROCESO ? "En proceso" : fmtVal(n);
        return new NotaRes(n.getId(), n.getTipo().name(), n.getNumPer(),
            n.getTitulo(), val, n.getEst().name(),
            n.getFecCarga() != null ? n.getFecCarga().format(FMT) : null,
            n.getFecMod() != null ? n.getFecMod().format(FMT) : null,
            n.getBloque().getMateria().getNom(),
            n.getBloque().getCurso().etiqueta(),
            n.getBloque().getAnio(), n.isBorrador());
    }

    private SolRes toSol(Solicitud s) {
        var n = s.getNota();
        return new SolRes(s.getId(), n.getId(), n.getAlumno().nomCompleto(),
            n.getBloque().getMateria().getNom(), s.getMotivo(),
            s.getValActualTexto() != null ? s.getValActualTexto() : fmt(s.getValActual()), valorNuevoSolicitud(s), s.getEst().name(),
            s.getFecSol().format(FMT),
            s.getSolicitante() != null ? s.getSolicitante().nomCompleto() : "",
            s.getAutorizo() != null ? s.getAutorizo().nomCompleto() : "",
            s.getFecRes() != null ? s.getFecRes().format(FMT) : null);
    }

    private RepRes toRep(Reporte r) {
        var n = r.getNota();
        return new RepRes(r.getId(), n.getId(), r.getAlumno().nomCompleto(),
            n.getBloque().getMateria().getNom(), r.getMotivo(), r.getEst().name(),
            r.getFecRep().format(FMT), !n.isBorrador() && n.getVal() != null && esFaseMasRecienteCargada(n));
    }

    private BolRes toBol(Boletin b) {
        return new BolRes(b.getId(), b.getAnio(), b.getTipoPer().name(), b.getNumPer(),
            b.getContenido(), b.getFecGen().format(FMT));
    }

    private AudRes toAud(Auditoria a) {
        return toAud(a, a.getPeriodo());
    }

    private AudRes toAud(Auditoria a, String periodo) {
        return new AudRes(
            a.getId(),
            a.getActor() != null ? a.getActor().nomCompleto() : "",
            a.getAutorizo() != null ? a.getAutorizo().nomCompleto() : "",
            a.getAlumno() != null ? a.getAlumno().nomCompleto() : "",
            a.getMateria(),
            periodo,
            a.getValAntTxt() != null ? a.getValAntTxt() : fmt(a.getValAnt()),
            a.getValNuevoTxt() != null ? a.getValNuevoTxt() : fmt(a.getValNuevo()),
            a.getMotivo(),
            a.getAccion(),
            a.getEst(),
            a.getFec().format(FMT)
        );
    }

    private String fmtVal(Nota n) {
        return n.getVal() != null ? n.getVal().setScale(0, RoundingMode.HALF_UP).toPlainString() : "-";
    }

    private String motivoObligatorio(String motivo) {
        if (motivo == null || motivo.isBlank()) throw new IllegalArgumentException("El motivo es obligatorio");
        return motivo.trim();
    }

    private String normalizarValorSolicitud(String valor) {
        if (valor == null || valor.isBlank()) throw new IllegalArgumentException("La nueva nota es obligatoria");
        String limpio = valor.trim().toUpperCase(Locale.ROOT);
        if (Set.of("EP", "EN PROCESO", "NI", "NC").contains(limpio))
            return "EN PROCESO".equals(limpio) ? "EP" : limpio;
        try { return NotaReglas.redondearVal(new BigDecimal(limpio)).toPlainString(); }
        catch (NumberFormatException e) { throw new IllegalArgumentException("La nueva nota debe ser del 1 al 10, EP, NI o NC"); }
    }

    private String valorNuevoSolicitud(Solicitud solicitud) {
        return solicitud.getValNuevoTexto() != null ? solicitud.getValNuevoTexto()
            : solicitud.getValNuevo() != null ? fmt(solicitud.getValNuevo()) : "";
    }

    private String fmt(BigDecimal v) {
        return v != null ? v.setScale(0, RoundingMode.HALF_UP).toPlainString() : "-";
    }

    private double round2(double v) {
        return BigDecimal.valueOf(v).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }
}
