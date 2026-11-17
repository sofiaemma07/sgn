package com.sgn.ent;

import com.sgn.enm.ConvocatoriaPrevia;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "rendicion_previa", uniqueConstraints =
    @UniqueConstraint(columnNames = {"materia_previa_id", "anio", "convocatoria"}))
public class RendicionPrevia {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false) @JoinColumn(name = "materia_previa_id", nullable = false)
    private MateriaPrevia previa;

    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16)
    private ConvocatoriaPrevia convocatoria;

    @Column(nullable = false)
    private int anio;

    @Column(nullable = false, precision = 4, scale = 2)
    private BigDecimal nota;

    @Column(nullable = false)
    private LocalDate fecha;

    @ManyToOne(optional = false) @JoinColumn(name = "registrada_por", nullable = false)
    private Usuario registradaPor;

    public Long getId() { return id; }
    public MateriaPrevia getPrevia() { return previa; }
    public void setPrevia(MateriaPrevia previa) { this.previa = previa; }
    public ConvocatoriaPrevia getConvocatoria() { return convocatoria; }
    public void setConvocatoria(ConvocatoriaPrevia convocatoria) { this.convocatoria = convocatoria; }
    public int getAnio() { return anio; }
    public void setAnio(int anio) { this.anio = anio; }
    public BigDecimal getNota() { return nota; }
    public void setNota(BigDecimal nota) { this.nota = nota; }
    public LocalDate getFecha() { return fecha; }
    public void setFecha(LocalDate fecha) { this.fecha = fecha; }
    public Usuario getRegistradaPor() { return registradaPor; }
    public void setRegistradaPor(Usuario registradaPor) { this.registradaPor = registradaPor; }
}
