package com.sgn.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.sgn.enm.TipoUsr;
import com.sgn.enm.EstadoAlumno;
import com.sgn.enm.FasePlanilla;
import jakarta.validation.constraints.NotBlank;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public final class Dtos {

    private Dtos() {}

    public record LoginReq(@NotBlank String ident, @NotBlank String pass) {}
    public record RecuperarPasswordReq(@NotBlank String ident) {}
    public record CambiarPasswordReq(
    @NotBlank String actual,
    @NotBlank String nueva
) {}
   public record LoginRes(
    String token,
    String legajo,
    String nom,
    TipoUsr tipo,
    boolean primerIngreso
) {}
    public record PerfilRes(
        String legajo, String cuil, String nom, String mail, TipoUsr tipo,
        String curso, int anioLectivo
    ) {}
    public record UsrRes(
        String legajo, String cuil, String nom, String nom2, String ape, String ape2,
        String mail, String mailTutor, TipoUsr tipo, boolean activo
    ) {}
    public record AlumnoAdminItem(String legajo, String nombre, String mail, boolean activo,
        EstadoAlumno estado, Long cursoId, String curso, String turno, String especialidad) {}
    public record PaginaAlumnos(List<AlumnoAdminItem> items, int page, int size, long total, int pages) {}
    public record AsignacionMasivaReq(List<String> legajos, long cursoId, int anio) {}
    public record DesasignacionMasivaReq(List<String> legajos, long cursoId, int anio) {}
    public record AlumnosPdfReq(List<String> legajos) {}
    public record MatriculacionCursoReq(long cursoId, int anio) {}
    public record EstadoAlumnoReq(EstadoAlumno estado) {}
    public record UsrReq(
        String legajo, String cuil, String nom, String nom2, String ape, String ape2,
        String mail, String mailTutor, String pass, TipoUsr tipo
    ) {}
    public record CursoRes(long id, int anio, int div, String turno, String esp, String etiqueta) {}
    public record CursoReq(int anio, int div, String turno, String esp) {}
    public record MateriaRes(long id, String nom) {}
    public record MateriaReq(@NotBlank String nom) {}
    public record BloqueRes(long id, String titulo, long materiaId, String materia, long cursoId, String curso, int anio) {}
    public record AlumnoRes(String legajo, String nomCompleto) {}
    public record NotaRes(
        long id, String tipo, int numPer, String titulo, String val, String est,
        String fecCarga, String fecMod, String materia, String curso, int anio, boolean borrador
    ) {}
    public record NotaReq(
        long bloqueId, String legajo, String tipo, int numPer,
        String titulo, BigDecimal val
    ) {}
    public record PlanillaValor(String legajo, String val) {}
    public record PlanillaReq(long bloqueId, FasePlanilla fase, List<PlanillaValor> notas) {}
    public record PlanillaFila(String legajo, String alumno, String val, Long notaId, boolean borrador) {}
    public record FaseInfo(FasePlanilla fase, String etiqueta, String desde, String hasta, boolean habilitada) {}
    public record FaseConfigReq(FasePlanilla fase, String desde, String hasta) {}
    public record PlanillaRes(long bloqueId, String materia, String curso, int anio,
        FasePlanilla fase, String estado, String desde, String hasta, List<PlanillaFila> alumnos) {}
    public record PlanillaFase(FasePlanilla fase, String etiqueta, String referencia, String desde, String hasta, String estado) {}
    public record PlanillaAlumno(String legajo, String alumno, Map<String, String> notas) {}
    public record PlanillaCompleta(long bloqueId, String materia, String curso, int anio,
        List<PlanillaFase> fases, List<PlanillaAlumno> alumnos) {}
    public record SolReq(long notaId, @NotBlank String motivo, @NotBlank String valNuevo) {}
    public record SolRes(
        long id, long notaId, String alumno, String materia, String motivo,
        String valActual, String valNuevo, String est, String fecSol,
        String docente, String autorizo, String fecRes
    ) {}
    public record RepReq(long notaId, @NotBlank String motivo) {}
    public record RepRes(
        long id, long notaId, String alumno, String materia, String motivo, String est, String fecRep,
        boolean puedeSolicitarCambio
    ) {}
    public record BolRes(long id, int anio, String tipoPer, int numPer, String contenido, String fecGen) {}
    public record BoletinPublicacionRes(boolean publicado, String publicadoEn) {}
    public record RendRes(double pctGlobal, double promGeneral, List<MateriaRend> materias, List<PeriodoRend> periodos) {}
    public record MateriaRend(String materia, double promNoOf, double pct, String est) {}
    public record PeriodoRend(String tipo, int numPer, double promNoOf) {}
    public record InscReq(String legajo, long cursoId, int anio) {}
    public record BloqueReq(long materiaId, long cursoId, String profLegajo, int anio) {}
    public record AudRes(
        long id, String actor, String autorizo, String alumno, String materia, String periodo,
        String valAnt, String valNuevo, String motivo, String accion, String est, String fec
    ) {}
    public record MailRes(long id, String dest, String asunto, boolean enviado, String error, String fec) {}
    public record AvisoMetadata(String materia, String periodo,
        @JsonProperty("solicitud_id") Long solicitudId) {}
    public record AvisoRes(String id,
        @JsonProperty("recipient_id") String recipientId,
        @JsonProperty("recipient_role") String recipientRole,
        String title, String message, boolean read,
        @JsonProperty("created_at") String createdAt,
        @JsonProperty("target_url") String targetUrl,
        AvisoMetadata metadata) {}
    public record MsgRes(String msg) {}
}
