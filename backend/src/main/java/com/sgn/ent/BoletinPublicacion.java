package com.sgn.ent;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "boletin_publicacion", uniqueConstraints =
    @UniqueConstraint(columnNames = {"curso_id", "anio"}))
public class BoletinPublicacion {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "curso_id", nullable = false)
    private Curso curso;

    @Column(nullable = false)
    private int anio;

    @Column(nullable = false)
    private boolean publicado;

    private LocalDateTime publicadoEn;

    @ManyToOne
    @JoinColumn(name = "publicado_por_legajo")
    private Usuario publicadoPor;

    public Long getId() { return id; }
    public Curso getCurso() { return curso; }
    public void setCurso(Curso curso) { this.curso = curso; }
    public int getAnio() { return anio; }
    public void setAnio(int anio) { this.anio = anio; }
    public boolean isPublicado() { return publicado; }
    public void setPublicado(boolean publicado) { this.publicado = publicado; }
    public LocalDateTime getPublicadoEn() { return publicadoEn; }
    public void setPublicadoEn(LocalDateTime publicadoEn) { this.publicadoEn = publicadoEn; }
    public Usuario getPublicadoPor() { return publicadoPor; }
    public void setPublicadoPor(Usuario publicadoPor) { this.publicadoPor = publicadoPor; }
}
