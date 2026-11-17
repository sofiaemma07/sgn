package com.sgn.config;

import com.sgn.ent.Usuario;
import com.sgn.enm.TipoUsr;
import com.sgn.repo.UsuarioRepo;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class SeedConfig {

    @Value("${sgn.seed.admin-password:${sgn.seed.bootstrap-password:1234}}")
    private String adminPassword;
    @Value("${sgn.seed.office-password:${sgn.seed.bootstrap-password:1234}}")
    private String officePassword;
    @Value("${sgn.seed.admin-legajo:0000}")
    private String adminLegajo;
    @Value("${sgn.seed.admin-cuil:00000000000}")
    private String adminCuil;
    @Value("${sgn.seed.admin-email:admin@gmail.com}")
    private String adminEmail;
    @Value("${sgn.seed.office-legajo:1111}")
    private String officeLegajo;
    @Value("${sgn.seed.office-cuil:11111111111}")
    private String officeCuil;
    @Value("${sgn.seed.office-email:oficina@gmail.com}")
    private String officeEmail;

    @Bean
    CommandLineRunner seed(UsuarioRepo usuarioRepo, PasswordEncoder passwordEncoder) {
        return args -> {
            // Solo crea accesos de arranque en una base vacía; no carga datos académicos de demostración.
            if (usuarioRepo.count() != 0) return;

            usuarioRepo.save(usuario(passwordEncoder, adminLegajo, adminCuil, "Admin", "Sistema",
                adminEmail, adminPassword, TipoUsr.ADMIN));
            usuarioRepo.save(usuario(passwordEncoder, officeLegajo, officeCuil, "Oficina", "Alumnos",
                officeEmail, officePassword, TipoUsr.OFICINA));
        };
    }

    private Usuario usuario(PasswordEncoder encoder, String legajo, String cuil,
                            String nombre, String apellido, String email, String password, TipoUsr tipo) {
        var usuario = new Usuario();
        usuario.setLegajo(legajo);
        usuario.setCuil(cuil);
        usuario.setNom(nombre);
        usuario.setApe(apellido);
        usuario.setMail(email);
        usuario.setPass(encoder.encode(password));
        usuario.setTipo(tipo);
        usuario.setActivo(true);
        // Usuario inicia con primerIngreso=true y debe cambiar la clave al ingresar.
        return usuario;
    }
}
