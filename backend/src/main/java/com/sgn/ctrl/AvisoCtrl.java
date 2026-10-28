package com.sgn.ctrl;

import com.sgn.dto.Dtos.AvisoRes;
import com.sgn.dto.Dtos.MsgRes;
import com.sgn.ent.Usuario;
import com.sgn.srv.AvisoSrv;
import com.sgn.srv.UsrDetailsSrv;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/notificaciones")
@PreAuthorize("hasAnyRole('ALUMNO','PROFESOR','OFICINA','ADMIN')")
public class AvisoCtrl {
    private final AvisoSrv avisoSrv;
    private final UsrDetailsSrv usrDetailsSrv;

    public AvisoCtrl(AvisoSrv avisoSrv, UsrDetailsSrv usrDetailsSrv) {
        this.avisoSrv = avisoSrv;
        this.usrDetailsSrv = usrDetailsSrv;
    }

    private Usuario me() {
        return usrDetailsSrv.actual(SecurityContextHolder.getContext().getAuthentication().getName());
    }

    @GetMapping
    public List<AvisoRes> listar() { return avisoSrv.listar(me()); }

    @PostMapping("/{id}/leer")
    public MsgRes leer(@PathVariable long id) {
        avisoSrv.leer(id, me());
        return new MsgRes("Notificación marcada como leída");
    }

    @PostMapping("/leer-todas")
    public MsgRes leerTodas() {
        avisoSrv.leerTodas(me());
        return new MsgRes("Notificaciones marcadas como leídas");
    }

    @DeleteMapping("/leidas")
    public MsgRes limpiarLeidas() {
        avisoSrv.limpiarLeidas(me());
        return new MsgRes("Notificaciones leídas eliminadas");
    }

    @DeleteMapping("/{id}")
    public MsgRes descartar(@PathVariable long id) {
        avisoSrv.descartar(id, me());
        return new MsgRes("Notificación descartada");
    }
}
