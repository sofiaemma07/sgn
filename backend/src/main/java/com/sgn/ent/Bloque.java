package com.sgn.ent;

import jakarta.persistence.*;
import com.sgn.enm.ModalidadBloque;
import java.util.List;

@Entity
@Table(name = "bloque", uniqueConstraints = @UniqueConstraint(columnNames = {"materia_id", "curso_id", "anio"}))
public class Bloque {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "materia_id")
    private Materia materia;

    @ManyToOne(optional = false)
    @JoinColumn(name = "curso_id")
    private Curso curso;

    @ManyToOne(optional = false)
    @JoinColumn(name = "prof_legajo")
    private Usuario prof;

    @ManyToOne
    @JoinColumn(name = "segundo_prof_legajo")
    private Usuario segundoProf;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private ModalidadBloque modalidad = ModalidadBloque.INDIVIDUAL;

    @Column(name = "suplencia_activa")
    private Boolean suplenciaActiva = true;

    @Column(nullable = false)
    private int anio;

    public String titulo() {
        return materia.getNom() + " " + curso.etiqueta();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Materia getMateria() { return materia; }
    public void setMateria(Materia materia) { this.materia = materia; }
    public Curso getCurso() { return curso; }
    public void setCurso(Curso curso) { this.curso = curso; }
    public Usuario getProf() { return prof; }
    public void setProf(Usuario prof) { this.prof = prof; }
    public Usuario getSegundoProf() { return segundoProf; }
    public void setSegundoProf(Usuario segundoProf) { this.segundoProf = segundoProf; }
    public ModalidadBloque getModalidad() { return modalidad == null ? ModalidadBloque.INDIVIDUAL : modalidad; }
    public void setModalidad(ModalidadBloque modalidad) { this.modalidad = modalidad; }
    public boolean isSuplenciaActiva() { return Boolean.TRUE.equals(suplenciaActiva); }
    public void setSuplenciaActiva(boolean suplenciaActiva) { this.suplenciaActiva = suplenciaActiva; }

    public boolean puedeEditar(Usuario docente) {
        if (docente == null || docente.getTipo() != com.sgn.enm.TipoUsr.PROFESOR) return false;
        if (getModalidad() == ModalidadBloque.SUPLENCIA) {
            return isSuplenciaActiva()
                ? segundoProf != null && segundoProf.getLegajo().equals(docente.getLegajo())
                : prof.getLegajo().equals(docente.getLegajo());
        }
        return prof.getLegajo().equals(docente.getLegajo())
            || getModalidad() == ModalidadBloque.CONJUNTA && segundoProf != null
                && segundoProf.getLegajo().equals(docente.getLegajo());
    }

    public List<Usuario> profesoresActivos() {
        if (getModalidad() == ModalidadBloque.SUPLENCIA) {
            Usuario activo = isSuplenciaActiva() && segundoProf != null ? segundoProf : prof;
            return activo == null ? List.of() : List.of(activo);
        }
        return segundoProf != null && getModalidad() == ModalidadBloque.CONJUNTA
            ? List.of(prof, segundoProf) : List.of(prof);
    }
    public int getAnio() { return anio; }
    public void setAnio(int anio) { this.anio = anio; }
}
