package com.sgn.ctrl;

import com.sgn.dto.Dtos.ConvocatoriaPreviaInfo;
import com.sgn.dto.Dtos.PreviaRes;
import com.sgn.dto.Dtos.RendicionPreviaReq;
import com.sgn.dto.Dtos.RendicionPreviaRes;
import com.sgn.srv.MateriaPreviaSrv;
import com.sgn.srv.UsrDetailsSrv;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/previas")
public class MateriaPreviaCtrl {
    private final MateriaPreviaSrv srv;
    private final UsrDetailsSrv detalles;

    public MateriaPreviaCtrl(MateriaPreviaSrv srv, UsrDetailsSrv detalles) {
        this.srv = srv;
        this.detalles = detalles;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('OFICINA','ADMIN')")
    public List<PreviaRes> listar() { return srv.listar(); }

    @GetMapping("/mias")
    @PreAuthorize("hasRole('ALUMNO')")
    public List<PreviaRes> mias(org.springframework.security.core.Authentication auth) {
        return srv.mias(detalles.actual(auth.getName()));
    }

    @GetMapping("/convocatorias")
    @PreAuthorize("hasAnyRole('OFICINA','ADMIN')")
    public List<ConvocatoriaPreviaInfo> convocatorias(@RequestParam int anio) {
        return srv.convocatorias(anio);
    }

    @PostMapping("/{id}/rendiciones")
    @PreAuthorize("hasAnyRole('OFICINA','ADMIN')")
    public RendicionPreviaRes registrar(@PathVariable long id, @Valid @RequestBody RendicionPreviaReq req,
                                         org.springframework.security.core.Authentication auth) {
        return srv.registrar(id, req, detalles.actual(auth.getName()));
    }
}
