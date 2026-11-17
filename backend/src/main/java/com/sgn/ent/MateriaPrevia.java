package com.sgn.ent;

import jakarta.persistence.*;

@Entity
@Table(name = "materia_previa", uniqueConstraints =
    @UniqueConstraint(columnNames = {"alumno_legajo", "bloque_origen_id"}))
public class MateriaPrevia {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false) @JoinColumn(name = "alumno_legajo", nullable = false)
    private Usuario alumno;

    @ManyToOne(optional = false) @JoinColumn(name = "bloque_origen_id", nullable = false)
    private Bloque bloqueOrigen;

    @ManyToOne(optional = false) @JoinColumn(name = "creada_por", nullable = false)
    private Usuario creadaPor;

    public Long getId() { return id; }
    public Usuario getAlumno() { return alumno; }
    public void setAlumno(Usuario alumno) { this.alumno = alumno; }
    public Bloque getBloqueOrigen() { return bloqueOrigen; }
    public void setBloqueOrigen(Bloque bloqueOrigen) { this.bloqueOrigen = bloqueOrigen; }
    public Usuario getCreadaPor() { return creadaPor; }
    public void setCreadaPor(Usuario creadaPor) { this.creadaPor = creadaPor; }
}
