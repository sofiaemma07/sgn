package com.sgn.ctrl;

import com.sgn.dto.Dtos.*;
import com.sgn.ent.Usuario;
import com.sgn.enm.TipoUsr;
import com.sgn.srv.NotaSrv;
import com.sgn.srv.UsuarioSrv;
import com.sgn.srv.UsrDetailsSrv;
import com.sgn.srv.BoletinSrv;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.io.ByteArrayOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@RestController
@RequestMapping("/api/usuarios")
public class UsuarioCtrl {

    private final UsuarioSrv srv;
    private final UsrDetailsSrv det;
    private final NotaSrv notaSrv;
    private final BoletinSrv boletinSrv;

    public UsuarioCtrl(UsuarioSrv srv, UsrDetailsSrv det, NotaSrv notaSrv, BoletinSrv boletinSrv) {
        this.srv = srv;
        this.det = det;
        this.notaSrv = notaSrv;
        this.boletinSrv = boletinSrv;
    }

    @GetMapping("/me")
    public PerfilRes me() {
        var auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        return notaSrv.perfil(det.actual(auth.getName()));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('OFICINA','ADMIN')")
    public List<UsrRes> listar(@RequestParam(required = false) TipoUsr tipo) {
        return srv.listar(tipo);
    }

    @GetMapping("/alumnos/padron")
    @PreAuthorize("hasAnyRole('OFICINA','ADMIN')")
    public PaginaAlumnos padron(@RequestParam(defaultValue = "") String q,
        @RequestParam(defaultValue = "0") int anio, @RequestParam(required = false) Long cursoId,
        @RequestParam(required = false) String turno, @RequestParam(required = false) String especialidad,
        @RequestParam(required = false) String estado, @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "25") int size) {
        return srv.buscarPadron(q, cicloActivo(anio), cursoId, turno, especialidad, estado, page, size);
    }

    @GetMapping(value = "/alumnos/exportar.csv", produces = "text/csv;charset=UTF-8")
    @PreAuthorize("hasAnyRole('OFICINA','ADMIN')")
    public ResponseEntity<String> exportarPadron(@RequestParam(defaultValue = "") String q,
        @RequestParam(defaultValue = "0") int anio, @RequestParam(required = false) Long cursoId,
        @RequestParam(required = false) String turno, @RequestParam(required = false) String especialidad,
        @RequestParam(required = false) String estado) {
        return ResponseEntity.ok().contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=padron-alumnos-" + anio + ".csv")
            .body(srv.exportarPadron(q, cicloActivo(anio), cursoId, turno, especialidad, estado));
    }

    @GetMapping("/matriculacion/alumnos")
    @PreAuthorize("hasAnyRole('OFICINA','ADMIN')")
    public PaginaAlumnos matriculacion(@RequestParam String q, @RequestParam int anio,
        @RequestParam long cursoId, @RequestParam boolean disponibles,
        @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "25") int size) {
        return srv.buscarMatriculacion(q, anio, cursoId, disponibles, page, size);
    }

    @PostMapping("/matriculacion/asignar")
    @PreAuthorize("hasAnyRole('OFICINA','ADMIN')")
    public MsgRes inscribirMasivo(@RequestBody AsignacionMasivaReq req) {
        return new MsgRes(srv.inscribirMasivo(req) + " alumnos asignados al curso");
    }

    @PostMapping("/matriculacion/desasignar")
    @PreAuthorize("hasAnyRole('OFICINA','ADMIN')")
    public MsgRes desinscribirMasivo(@RequestBody DesasignacionMasivaReq req) {
        return new MsgRes(srv.desinscribirMasivo(req) + " alumnos retirados del curso");
    }

    @PostMapping("/matriculacion/asignar-disponibles")
    @PreAuthorize("hasAnyRole('OFICINA','ADMIN')")
    public MsgRes matricularDisponibles(@RequestBody MatriculacionCursoReq req) {
        return new MsgRes(srv.matricularDisponibles(req) + " alumnos disponibles asignados al curso");
    }

    @PostMapping("/matriculacion/retirar-todos")
    @PreAuthorize("hasAnyRole('OFICINA','ADMIN')")
    public MsgRes retirarTodos(@RequestBody MatriculacionCursoReq req) {
        return new MsgRes(srv.retirarTodoCurso(req) + " alumnos retirados del curso");
    }

    @PostMapping("/alumnos/boletines.zip")
    @PreAuthorize("hasAnyRole('OFICINA','ADMIN')")
    public ResponseEntity<byte[]> boletinesSeleccionados(@RequestBody AlumnosPdfReq req) {
        if (req.legajos() == null || req.legajos().isEmpty()) throw new IllegalArgumentException("Seleccioná al menos un alumno");
        if (req.legajos().size() > 100) throw new IllegalArgumentException("Descargá hasta 100 boletines por lote");
        try {
            var auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
            Usuario actor = det.actual(auth.getName());
            var baos = new ByteArrayOutputStream();
            try (var zip = new ZipOutputStream(baos)) {
                for (String legajo : req.legajos().stream().distinct().toList()) {
                    zip.putNextEntry(new ZipEntry(legajo + "-boletin.pdf"));
                    zip.write(boletinSrv.pdfAlumno(legajo, actor));
                    zip.closeEntry();
                }
            }
            return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=boletines-alumnos.zip")
                .contentType(MediaType.APPLICATION_OCTET_STREAM).body(baos.toByteArray());
        } catch (Exception e) {
            if (e instanceof RuntimeException runtime) throw runtime;
            throw new IllegalStateException("No se pudieron generar los boletines seleccionados", e);
        }
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public UsrRes crear(@Valid @RequestBody UsrReq req) {
        return srv.crear(req);
    }

    @PostMapping("/alumnos")
    @PreAuthorize("hasAnyRole('OFICINA','ADMIN')")
    public UsrRes crearAlumno(@Valid @RequestBody UsrReq req) {
        if (req.tipo() != TipoUsr.ALUMNO) throw new IllegalArgumentException("Solo se pueden dar de alta alumnos en este módulo");
        return srv.crear(req);
    }

    @PutMapping("/{legajo}")
    @PreAuthorize("hasRole('ADMIN')")
    public UsrRes editar(@PathVariable String legajo, @RequestBody UsrReq req) {
        return srv.editar(legajo, req);
    }

    @PostMapping("/{legajo}/reset-password")
@PreAuthorize("hasRole('ADMIN')")
public MsgRes resetPassword(@PathVariable String legajo) {
    return new MsgRes(srv.resetPassword(legajo));
}

    @PatchMapping("/{legajo}/activo")
    @PreAuthorize("hasRole('ADMIN')")
    public UsrRes toggle(@PathVariable String legajo, @RequestParam boolean activo) {
        return srv.toggle(legajo, activo);
    }

    @PatchMapping("/{legajo}/estado-alumno")
    @PreAuthorize("hasAnyRole('OFICINA','ADMIN')")
    public AlumnoAdminItem estadoAlumno(@PathVariable String legajo, @RequestBody EstadoAlumnoReq req,
                                        @RequestParam(defaultValue = "0") int anio) {
        return srv.cambiarEstadoAlumno(legajo, req, cicloActivo(anio));
    }

    @GetMapping("/cursos")
    @PreAuthorize("hasAnyRole('OFICINA','ADMIN')")
    public List<CursoRes> cursos() {
        return srv.cursos();
    }

    @PostMapping("/cursos")
    @PreAuthorize("hasRole('ADMIN')")
    public CursoRes crearCurso(@RequestBody CursoReq req) {
        return srv.crearCurso(req);
    }

    @GetMapping("/materias")
    @PreAuthorize("hasAnyRole('OFICINA','ADMIN','PROFESOR')")
    public List<MateriaRes> materias() {
        return srv.materias();
    }

    @PostMapping("/materias")
    @PreAuthorize("hasRole('ADMIN')")
    public MateriaRes crearMateria(@Valid @RequestBody MateriaReq req) {
        return srv.crearMateria(req);
    }

    @PostMapping("/inscripcion")
    @PreAuthorize("hasAnyRole('OFICINA','ADMIN')")
    public MsgRes inscribir(@RequestBody InscReq req) {
        srv.inscribir(req);
        return new MsgRes("Alumno inscripto");
    }

    @PostMapping("/bloque")
    @PreAuthorize("hasAnyRole('OFICINA','ADMIN')")
    public MsgRes bloque(@RequestBody BloqueReq req) {
        srv.asignarBloque(req);
        return new MsgRes("Bloque asignado");
    }

    @GetMapping("/notificaciones")
    @PreAuthorize("hasRole('ADMIN')")
    public List<MailRes> notificaciones() {
        return srv.notificaciones();
    }

    private int cicloActivo(int solicitado) {
        return solicitado > 0 ? solicitado : notaSrv.anioActivo();
    }
}
