package com.sgn.repo;

import com.sgn.ent.Usuario;
import com.sgn.enm.TipoUsr;
import com.sgn.enm.Esp;
import com.sgn.enm.Turno;
import com.sgn.enm.EstadoAlumno;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;

public interface UsuarioRepo extends JpaRepository<Usuario, String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from Usuario u where u.legajo = :legajo")
    Optional<Usuario> findByLegajoForUpdate(@Param("legajo") String legajo);
    Optional<Usuario> findByMail(String mail);
    Optional<Usuario> findByMailIgnoreCase(String mail);
    Optional<Usuario> findByCuil(String cuil);
    List<Usuario> findByTipo(TipoUsr tipo);
    List<Usuario> findByTipoAndActivoTrue(TipoUsr tipo);
    boolean existsByMail(String mail);
    boolean existsByCuil(String cuil);

    @Query(value = """
        select distinct u from Usuario u
        where u.tipo = com.sgn.enm.TipoUsr.ALUMNO
          and (:q = '' or lower(u.legajo) like lower(concat('%', :q, '%'))
            or lower(u.nom) like lower(concat('%', :q, '%'))
            or lower(coalesce(u.nom2, '')) like lower(concat('%', :q, '%'))
            or lower(u.ape) like lower(concat('%', :q, '%'))
            or lower(coalesce(u.ape2, '')) like lower(concat('%', :q, '%'))
            or lower(u.mail) like lower(concat('%', :q, '%'))
            or lower(concat(concat(u.nom, ' '), u.ape)) like lower(concat('%', :q, '%')))
          and (:estado is null or u.estadoAlumno = :estado
            or (u.estadoAlumno is null and :estado = com.sgn.enm.EstadoAlumno.ACTIVO and u.activo = true)
            or (u.estadoAlumno is null and :estado = com.sgn.enm.EstadoAlumno.SUSPENDIDO and u.activo = false))
          and (:cursoId is null
            or (:cursoId = -1 and not exists (select i0.id from Inscripcion i0 where i0.alumno = u and i0.anio = :anio and i0.activa = true))
            or exists (select i1.id from Inscripcion i1 where i1.alumno = u and i1.anio = :anio and i1.activa = true and i1.curso.id = :cursoId))
          and (:turno is null or exists (select i2.id from Inscripcion i2 where i2.alumno = u and i2.anio = :anio and i2.activa = true and i2.curso.turno = :turno))
          and (:esp is null or exists (select i3.id from Inscripcion i3 where i3.alumno = u and i3.anio = :anio and i3.activa = true and i3.curso.esp = :esp))
        """,
        countQuery = """
        select count(u) from Usuario u
        where u.tipo = com.sgn.enm.TipoUsr.ALUMNO
          and (:q = '' or lower(u.legajo) like lower(concat('%', :q, '%'))
            or lower(u.nom) like lower(concat('%', :q, '%'))
            or lower(coalesce(u.nom2, '')) like lower(concat('%', :q, '%'))
            or lower(u.ape) like lower(concat('%', :q, '%'))
            or lower(coalesce(u.ape2, '')) like lower(concat('%', :q, '%'))
            or lower(u.mail) like lower(concat('%', :q, '%'))
            or lower(concat(concat(u.nom, ' '), u.ape)) like lower(concat('%', :q, '%')))
          and (:estado is null or u.estadoAlumno = :estado
            or (u.estadoAlumno is null and :estado = com.sgn.enm.EstadoAlumno.ACTIVO and u.activo = true)
            or (u.estadoAlumno is null and :estado = com.sgn.enm.EstadoAlumno.SUSPENDIDO and u.activo = false))
          and (:cursoId is null
            or (:cursoId = -1 and not exists (select i0.id from Inscripcion i0 where i0.alumno = u and i0.anio = :anio and i0.activa = true))
            or exists (select i1.id from Inscripcion i1 where i1.alumno = u and i1.anio = :anio and i1.activa = true and i1.curso.id = :cursoId))
          and (:turno is null or exists (select i2.id from Inscripcion i2 where i2.alumno = u and i2.anio = :anio and i2.activa = true and i2.curso.turno = :turno))
          and (:esp is null or exists (select i3.id from Inscripcion i3 where i3.alumno = u and i3.anio = :anio and i3.activa = true and i3.curso.esp = :esp))
        """)
    Page<Usuario> buscarPadron(@Param("q") String q, @Param("anio") int anio,
        @Param("cursoId") Long cursoId, @Param("turno") Turno turno,
        @Param("esp") Esp esp, @Param("estado") EstadoAlumno estado, Pageable pageable);

    @Query(value = """
        select u from Usuario u where u.tipo = com.sgn.enm.TipoUsr.ALUMNO
          and (:q = '' or lower(u.legajo) like lower(concat('%', :q, '%'))
            or lower(u.nom) like lower(concat('%', :q, '%'))
            or lower(u.ape) like lower(concat('%', :q, '%'))
            or lower(u.mail) like lower(concat('%', :q, '%')))
          and (:disponibles = false or u.estadoAlumno = com.sgn.enm.EstadoAlumno.ACTIVO
            or (u.estadoAlumno is null and u.activo = true))
          and ((:disponibles = true and not exists (select i.id from Inscripcion i where i.alumno = u and i.anio = :anio and i.activa = true))
            or (:disponibles = false and exists (select i2.id from Inscripcion i2 where i2.alumno = u and i2.anio = :anio and i2.activa = true and i2.curso.id = :cursoId)))
        """,
        countQuery = """
        select count(u) from Usuario u where u.tipo = com.sgn.enm.TipoUsr.ALUMNO
          and (:q = '' or lower(u.legajo) like lower(concat('%', :q, '%'))
            or lower(u.nom) like lower(concat('%', :q, '%'))
            or lower(u.ape) like lower(concat('%', :q, '%'))
            or lower(u.mail) like lower(concat('%', :q, '%')))
          and (:disponibles = false or u.estadoAlumno = com.sgn.enm.EstadoAlumno.ACTIVO
            or (u.estadoAlumno is null and u.activo = true))
          and ((:disponibles = true and not exists (select i.id from Inscripcion i where i.alumno = u and i.anio = :anio and i.activa = true))
            or (:disponibles = false and exists (select i2.id from Inscripcion i2 where i2.alumno = u and i2.anio = :anio and i2.activa = true and i2.curso.id = :cursoId)))
        """)
    Page<Usuario> buscarMatriculacion(@Param("q") String q, @Param("anio") int anio,
        @Param("cursoId") Long cursoId, @Param("disponibles") boolean disponibles, Pageable pageable);
}
