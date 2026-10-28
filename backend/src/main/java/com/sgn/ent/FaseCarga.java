package com.sgn.ent;

import com.sgn.enm.FasePlanilla;
import jakarta.persistence.*;
import java.time.LocalDate;

@Entity
@Table(name = "fase_carga", uniqueConstraints = @UniqueConstraint(columnNames = {"anio", "fase"}))
public class FaseCarga {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false) private int anio;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private FasePlanilla fase;
    @Column(nullable = false) private LocalDate desde;
    @Column(nullable = false) private LocalDate hasta;

    public Long getId() { return id; }
    public int getAnio() { return anio; }
    public void setAnio(int anio) { this.anio = anio; }
    public FasePlanilla getFase() { return fase; }
    public void setFase(FasePlanilla fase) { this.fase = fase; }
    public LocalDate getDesde() { return desde; }
    public void setDesde(LocalDate desde) { this.desde = desde; }
    public LocalDate getHasta() { return hasta; }
    public void setHasta(LocalDate hasta) { this.hasta = hasta; }
}
