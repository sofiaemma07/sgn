package com.sgn.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public class CursoEspecialidadSchemaUpdater implements ApplicationRunner {
    private final JdbcTemplate jdbc;

    public CursoEspecialidadSchemaUpdater(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void run(ApplicationArguments args) {
        List<String> constraints = jdbc.queryForList(
            "SELECT pg_get_constraintdef(oid) FROM pg_constraint "
                + "WHERE conrelid = to_regclass('curso') AND conname = 'curso_esp_check'",
            String.class);
        if (!constraints.isEmpty() && constraints.get(0).contains("'CICLO_BASICO'")) return;

        if (!constraints.isEmpty())
            jdbc.execute("ALTER TABLE curso DROP CONSTRAINT curso_esp_check");
        jdbc.execute("ALTER TABLE curso ADD CONSTRAINT curso_esp_check "
            + "CHECK (esp IN ('CICLO_BASICO', 'AUTOMOTORES', 'COMPUTACION'))");
    }
}
