package com.sgn.ctrl;

import com.sgn.dto.Dtos.LoginReq;
import com.sgn.dto.Dtos.LoginRes;
import com.sgn.enm.TipoUsr;
import com.sgn.srv.AuthSrv;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AuthCtrlTest {

    @Test
    void loginEntregaJwtEnCookieHttpOnlyYNoEnElCuerpo() {
        AuthSrv authSrv = mock(AuthSrv.class);
        String token = "jwt-de-prueba-no-expuesto";
        long expiracion = System.currentTimeMillis() + 60_000;
        when(authSrv.login(new LoginReq("usuario", "clave")))
            .thenReturn(new LoginRes(token, "A001", "Usuario de prueba", TipoUsr.ALUMNO, false, expiracion));
        AuthCtrl ctrl = new AuthCtrl(authSrv, false, "Lax");
        MockHttpServletResponse response = new MockHttpServletResponse();

        var body = ctrl.login(new LoginReq("usuario", "clave"), response);
        String cookie = response.getHeader("Set-Cookie");

        assertEquals("A001", body.legajo());
        assertFalse(body.toString().contains(token));
        assertTrue(cookie.contains("HttpOnly"));
        assertTrue(cookie.contains("SameSite=Lax"));
        assertTrue(cookie.contains("Path=/api"));
        assertTrue(cookie.startsWith("SGN_SESSION=" + token));
    }

    @Test
    void cerrarSesionExpiraLaCookieHttpOnly() {
        AuthCtrl ctrl = new AuthCtrl(mock(AuthSrv.class), true, "Lax");
        MockHttpServletResponse response = new MockHttpServletResponse();

        ctrl.cerrarSesion(response);

        String cookie = response.getHeader("Set-Cookie");
        assertTrue(cookie.contains("Max-Age=0"));
        assertTrue(cookie.contains("HttpOnly"));
        assertTrue(cookie.contains("Secure"));
    }
}
