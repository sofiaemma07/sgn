package com.sgn.ent;

import com.sgn.enm.FasePlanilla;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "planilla_cierre", uniqueConstraints = @UniqueConstraint(columnNames = {"bloque_id", "fase"}))
public class PlanillaCierre {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(optional = false) @JoinColumn(name = "bloque_id") private Bloque bloque;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private FasePlanilla fase;
    @ManyToOne(optional = false) @JoinColumn(name = "cerrado_por") private Usuario cerradoPor;
    @Column(nullable = false) private LocalDateTime cerradoEn;

    public Long getId() { return id; }
    public Bloque getBloque() { return bloque; }
    public void setBloque(Bloque bloque) { this.bloque = bloque; }
    public FasePlanilla getFase() { return fase; }
    public void setFase(FasePlanilla fase) { this.fase = fase; }
    public Usuario getCerradoPor() { return cerradoPor; }
    public void setCerradoPor(Usuario cerradoPor) { this.cerradoPor = cerradoPor; }
    public LocalDateTime getCerradoEn() { return cerradoEn; }
    public void setCerradoEn(LocalDateTime cerradoEn) { this.cerradoEn = cerradoEn; }
}
