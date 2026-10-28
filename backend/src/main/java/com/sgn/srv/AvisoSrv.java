package com.sgn.srv;

import com.sgn.dto.Dtos.AvisoMetadata;
import com.sgn.dto.Dtos.AvisoRes;
import com.sgn.ent.Aviso;
import com.sgn.ent.Bloque;
import com.sgn.ent.FaseCarga;
import com.sgn.ent.Nota;
import com.sgn.ent.Reporte;
import com.sgn.ent.Solicitud;
import com.sgn.ent.Usuario;
import com.sgn.enm.FasePlanilla;
import com.sgn.enm.TipoUsr;
import com.sgn.repo.AvisoRepo;
import com.sgn.repo.FaseCargaRepo;
import com.sgn.repo.UsuarioRepo;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class AvisoSrv {
    private static final ZoneId ZONA = ZoneId.of("America/Argentina/Buenos_Aires");
    private static final DateTimeFormatter FMT = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
    private final AvisoRepo avisoRepo;
    private final UsuarioRepo usuarioRepo;
    private final FaseCargaRepo faseRepo;

    @Value("${sgn.anio-activo:2026}") private int anioActivo;

    public AvisoSrv(AvisoRepo avisoRepo, UsuarioRepo usuarioRepo, FaseCargaRepo faseRepo) {
        this.avisoRepo = avisoRepo;
        this.usuarioRepo = usuarioRepo;
        this.faseRepo = faseRepo;
    }

    @Transactional(readOnly = true)
    public List<AvisoRes> listar(Usuario usuario) {
        return avisoRepo.findByDestinatarioLegajoOrderByCreadaEnDesc(usuario.getLegajo()).stream()
            .map(this::toRes).toList();
    }

    @Transactional
    public void leer(long id, Usuario usuario) {
        Aviso aviso = avisoRepo.findByIdAndDestinatarioLegajo(id, usuario.getLegajo())
            .orElseThrow(() -> new IllegalArgumentException("Notificación no encontrada"));
        aviso.setLeida(true);
    }

    @Transactional
    public void leerTodas(Usuario usuario) {
        avisoRepo.findByDestinatarioLegajoOrderByCreadaEnDesc(usuario.getLegajo()).stream()
            .filter(a -> !a.isLeida()).forEach(a -> a.setLeida(true));
    }

    @Transactional
    public void descartar(long id, Usuario usuario) {
        avisoRepo.findByIdAndDestinatarioLegajo(id, usuario.getLegajo()).ifPresent(avisoRepo::delete);
    }

    @Transactional
    public void limpiarLeidas(Usuario usuario) {
        avisoRepo.deleteByDestinatarioLegajoAndLeidaTrue(usuario.getLegajo());
    }

    @Transactional
    public void notaEntregada(Nota nota, FasePlanilla fase) {
        String periodo = fase.etiqueta();
        crear(nota.getAlumno(), "NOTA:" + nota.getBloque().getId() + ":" + fase + ":" + nota.getAlumno().getLegajo(),
            "Notas actualizadas", "El docente ha actualizado las notas de " + nota.getBloque().getMateria().getNom()
                + ". Tu promedio y estado académico han sido recalculados.", "/alumno/historial",
            nota.getBloque().getMateria().getNom(), periodo, null);
    }

    @Transactional
    public void planillaEntregada(Bloque bloque, Usuario docente, FasePlanilla fase) {
        String mensaje = "El docente " + docente.nomCompleto() + " cerró las notas de "
            + bloque.getMateria().getNom() + " · " + bloque.getCurso().etiqueta() + " (" + fase.etiqueta() + ").";
        String clave = "ACTA:" + bloque.getId() + ":" + fase;
        paraTipo(TipoUsr.OFICINA, clave, "Acta de notas entregada", mensaje,
            "/oficina/notas/" + bloque.getId(), bloque.getMateria().getNom(), fase.etiqueta(), null);
    }

    @Transactional
    public void reporteNuevo(Reporte reporte) {
        Nota nota = reporte.getNota();
        String periodo = periodo(nota);
        crear(nota.getBloque().getProf(), "REPORTE:" + reporte.getId() + ":DOCENTE",
            "Nueva solicitud de revisión", "El alumno " + reporte.getAlumno().nomCompleto()
                + " ha solicitado una revisión de nota en " + nota.getBloque().getMateria().getNom()
                + " — " + periodo + ".", "/profesor/reportes?reporteId=" + reporte.getId(), nota.getBloque().getMateria().getNom(), periodo, null);
    }

    @Transactional
    public void reporteDesestimado(Reporte reporte) {
        Nota nota = reporte.getNota();
        String periodo = periodo(nota);
        crear(reporte.getAlumno(), "REPORTE:" + reporte.getId() + ":DESESTIMADO",
            "Solicitud de revisión desestimada", "Tu solicitud de revisión de nota en "
                + nota.getBloque().getMateria().getNom() + " fue desestimada por el docente.",
            "/alumno/historial", nota.getBloque().getMateria().getNom(), periodo, null);
    }

    @Transactional
    public void rectificacionDerivada(Reporte reporte, Solicitud solicitud) {
        Nota nota = reporte.getNota();
        String periodo = periodo(nota);
        crear(reporte.getAlumno(), "SOL:" + solicitud.getId() + ":DERIVADA:ALUMNO",
            "Rectificación derivada a Oficina", "El docente dio visto bueno a tu rectificación de nota en "
                + nota.getBloque().getMateria().getNom()
                + ". El trámite fue derivado a Oficina de Alumnos para su validación final.",
            "/alumno/historial", nota.getBloque().getMateria().getNom(), periodo, solicitud.getId());
    }

    @Transactional
    public void solicitudAOficina(Solicitud solicitud) {
        Nota nota = solicitud.getNota();
        String periodo = periodo(nota);
        String mensaje = "El docente " + solicitud.getSolicitante().nomCompleto()
            + " ha solicitado una rectificación de nota para el alumno " + nota.getAlumno().nomCompleto()
            + " en " + nota.getBloque().getMateria().getNom() + " (" + periodo
            + " — Nota anterior: " + (solicitud.getValActualTexto() != null ? solicitud.getValActualTexto() : solicitud.getValActual())
            + " ➔ Nota propuesta: " + (solicitud.getValNuevoTexto() != null ? solicitud.getValNuevoTexto() : solicitud.getValNuevo()) + ").";
        paraTipo(TipoUsr.OFICINA, "SOL:" + solicitud.getId() + ":OFICINA", "Rectificación para validar",
            mensaje, "/oficina?solicitudId=" + solicitud.getId(), nota.getBloque().getMateria().getNom(), periodo, solicitud.getId());
    }

    @Transactional
    public void solicitudResuelta(Solicitud solicitud, boolean aprobada, String notaAnterior, String notaNueva) {
        Nota nota = solicitud.getNota();
        String periodo = periodo(nota);
        if (aprobada) {
            crear(nota.getAlumno(), "SOL:" + solicitud.getId() + ":RESUELTA:ALUMNO",
                "Nota actualizada", "Se actualizó tu nota de " + nota.getBloque().getMateria().getNom()
                    + " (" + periodo + "): de " + notaAnterior + " a " + notaNueva + ".",
                "/alumno/historial", nota.getBloque().getMateria().getNom(), periodo, solicitud.getId());
        } else if (solicitud.getReporte() != null) {
            crear(nota.getAlumno(), "SOL:" + solicitud.getId() + ":RECHAZADA:ALUMNO",
                "Solicitud de revisión desestimada", "Oficina de Alumnos desestimó tu solicitud de revisión de nota en "
                    + nota.getBloque().getMateria().getNom() + " (" + periodo + ").",
                "/alumno/historial", nota.getBloque().getMateria().getNom(), periodo, solicitud.getId());
        }
        crear(solicitud.getSolicitante(), "SOL:" + solicitud.getId() + ":RESUELTA:DOCENTE",
            "Rectificación " + (aprobada ? "aprobada" : "rechazada"), "Oficina de Alumnos "
                + (aprobada ? "aprobó" : "rechazó") + " la solicitud de rectificación de nota de "
                + nota.getAlumno().nomCompleto() + " en " + nota.getBloque().getMateria().getNom() + ".",
            "/profesor/solicitudes?solicitudId=" + solicitud.getId(), nota.getBloque().getMateria().getNom(), periodo, solicitud.getId());
    }

    @Scheduled(cron = "0 0 9 * * *", zone = "America/Argentina/Buenos_Aires")
    @Transactional
    public void recordarVencimientosCierres() {
        LocalDate hoy = LocalDate.now(ZONA);
        for (FaseCarga fase : faseRepo.findByAnioOrderById(anioActivo)) {
            if (!List.of(FasePlanilla.CIERRE_1, FasePlanilla.CIERRE_2, FasePlanilla.CIERRE_3).contains(fase.getFase())) continue;
            long dias = java.time.temporal.ChronoUnit.DAYS.between(hoy, fase.getHasta());
            if (dias < 0 || dias > 3) continue;
            String nombre = fase.getFase().etiqueta();
            String mensaje = "La ventana de carga de " + nombre + " vence el " + fase.getHasta() + ".";
            paraTipo(TipoUsr.OFICINA, "CALENDARIO:" + anioActivo + ":" + fase.getFase() + ":" + fase.getHasta(),
                "Cierre próximo a vencer", mensaje, "/oficina/fases", null, nombre, null);
            paraTipo(TipoUsr.ADMIN, "CALENDARIO:" + anioActivo + ":" + fase.getFase() + ":" + fase.getHasta(),
                "Cierre próximo a vencer", mensaje, "/oficina/fases", null, nombre, null);
        }
    }

    private void paraTipo(TipoUsr tipo, String clave, String titulo, String mensaje, String destino,
                          String materia, String periodo, Long solicitudId) {
        usuarioRepo.findByTipoAndActivoTrue(tipo).forEach(u -> crear(u, clave + ":" + u.getLegajo(), titulo,
            mensaje, destino, materia, periodo, solicitudId));
    }

    private void crear(Usuario usuario, String clave, String titulo, String mensaje, String destino,
                       String materia, String periodo, Long solicitudId) {
        if (avisoRepo.existsByClaveEvento(clave)) return;
        Aviso aviso = new Aviso();
        aviso.setDestinatario(usuario);
        aviso.setTitulo(titulo);
        aviso.setMensaje(mensaje);
        aviso.setLeida(false);
        aviso.setCreadaEn(LocalDateTime.now(ZONA));
        aviso.setDestino(destino);
        aviso.setMateria(materia);
        aviso.setPeriodo(periodo);
        aviso.setSolicitudId(solicitudId);
        aviso.setClaveEvento(clave.length() > 240 ? clave.substring(0, 240) : clave);
        avisoRepo.save(aviso);
    }

    private String periodo(Nota nota) {
        if (nota.getFase() != null) return nota.getFase().etiqueta();
        return switch (nota.getTipo()) {
            case BIM -> nota.getNumPer() + "° Bimestre";
            case CUAT -> nota.getNumPer() + "° Cuatrimestre";
            case DIC -> "Cierre Diciembre";
            case FEB -> nota.getNumPer() == 1 ? "Cierre Febrero" : "Cierre Marzo";
            case FINAL -> nota.getNumPer() == 1 ? "Final 1° cuatrimestre"
                : nota.getNumPer() == 2 ? "Final 2° cuatrimestre" : "Calificación definitiva";
            default -> nota.getTitulo() == null ? "Período oficial" : nota.getTitulo();
        };
    }

    private AvisoRes toRes(Aviso aviso) {
        String rol = switch (aviso.getDestinatario().getTipo()) {
            case ALUMNO -> "alumno";
            case PROFESOR -> "docente";
            case OFICINA, ADMIN -> "oficina_alumnos";
            default -> aviso.getDestinatario().getTipo().name().toLowerCase();
        };
        return new AvisoRes(String.valueOf(aviso.getId()), aviso.getDestinatario().getLegajo(), rol,
            aviso.getTitulo(), aviso.getMensaje(), aviso.isLeida(), aviso.getCreadaEn().format(FMT),
            aviso.getDestino(), new AvisoMetadata(aviso.getMateria(), aviso.getPeriodo(), aviso.getSolicitudId()));
    }
}
