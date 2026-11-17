package com.sgn.ctrl;

import com.sgn.dto.Dtos.*;
import com.sgn.srv.AuthSrv;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;

import jakarta.validation.Valid;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpServletResponse;

@RestController
@RequestMapping("/api/auth")
public class AuthCtrl {
    private static final String SESSION_COOKIE = "SGN_SESSION";

    private final AuthSrv srv;
    private final boolean secureCookie;
    private final String sameSite;

    public AuthCtrl(AuthSrv srv, @Value("${sgn.auth.cookie-secure:false}") boolean secureCookie,
                    @Value("${sgn.auth.cookie-same-site:Lax}") String sameSite) {
        this.srv = srv;
        this.secureCookie = secureCookie;
        this.sameSite = sameSite;
    }

    @PostMapping("/login")
    public SesionRes login(@Valid @RequestBody LoginReq req, HttpServletResponse response) {
        return sesion(srv.login(req), response);
    }

    @PostMapping("/renovar")
    public SesionRes renovar(Authentication authentication, HttpServletResponse response) {
        return sesion(srv.renovar(authentication.getName()), response);
    }

    @PostMapping("/cerrar-sesion")
    public void cerrarSesion(HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE, ResponseCookie.from(SESSION_COOKIE, "")
            .httpOnly(true).secure(secureCookie).sameSite(sameSite).path("/api").maxAge(0).build().toString());
    }

    private SesionRes sesion(LoginRes login, HttpServletResponse response) {
        long segundos = Math.max(0, (login.expiresAt() - System.currentTimeMillis()) / 1000);
        response.addHeader(HttpHeaders.SET_COOKIE, ResponseCookie.from(SESSION_COOKIE, login.token())
            .httpOnly(true).secure(secureCookie).sameSite(sameSite).path("/api")
            .maxAge(segundos).build().toString());
        return new SesionRes(login.legajo(), login.nom(), login.tipo(), login.primerIngreso(), login.expiresAt());
    }

    @PostMapping("/recuperar-password")
    public MsgRes recuperarPassword(@Valid @RequestBody RecuperarPasswordReq req) {
        try {
            srv.recuperarPassword(req.ident());
        } catch (IllegalStateException ignored) {}
        return new MsgRes("Si existe una cuenta activa con ese identificador, se enviará una contraseña temporal al correo registrado.");
    }

    @PostMapping("/cambiar-password")
    public void cambiarPassword(
            @Valid @RequestBody CambiarPasswordReq req,
            Authentication authentication) {

        srv.cambiarPassword(authentication.getName(), req);
    }
}
