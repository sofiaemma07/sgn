package com.sgn.ent;

import com.sgn.enm.FasePlanilla;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "planilla_confirmacion", uniqueConstraints =
    @UniqueConstraint(columnNames = {"bloque_id", "fase", "prof_legajo"}))
public class ConfirmacionPlanilla {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false) @JoinColumn(name = "bloque_id")
    private Bloque bloque;

    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20)
    private FasePlanilla fase;

    @ManyToOne(optional = false) @JoinColumn(name = "prof_legajo")
    private Usuario profesor;

    @Column(nullable = false)
    private LocalDateTime confirmadoEn;

    public Long getId() { return id; }
    public Bloque getBloque() { return bloque; }
    public void setBloque(Bloque bloque) { this.bloque = bloque; }
    public FasePlanilla getFase() { return fase; }
    public void setFase(FasePlanilla fase) { this.fase = fase; }
    public Usuario getProfesor() { return profesor; }
    public void setProfesor(Usuario profesor) { this.profesor = profesor; }
    public LocalDateTime getConfirmadoEn() { return confirmadoEn; }
    public void setConfirmadoEn(LocalDateTime confirmadoEn) { this.confirmadoEn = confirmadoEn; }
}
