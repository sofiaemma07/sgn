package com.sgn.ent;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "aviso")
public class Aviso {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false) @JoinColumn(name = "recipient_id")
    private Usuario destinatario;

    @Column(nullable = false, length = 200) private String titulo;
    @Column(nullable = false, columnDefinition = "TEXT") private String mensaje;
    @Column(nullable = false) private boolean leida;
    @Column(nullable = false) private LocalDateTime creadaEn;
    @Column(nullable = false, length = 500) private String destino;
    @Column(length = 120) private String materia;
    @Column(length = 80) private String periodo;
    private Long solicitudId;
    @Column(name = "event_key", nullable = false, unique = true, length = 240) private String claveEvento;

    public Long getId() { return id; }
    public Usuario getDestinatario() { return destinatario; }
    public void setDestinatario(Usuario destinatario) { this.destinatario = destinatario; }
    public String getTitulo() { return titulo; }
    public void setTitulo(String titulo) { this.titulo = titulo; }
    public String getMensaje() { return mensaje; }
    public void setMensaje(String mensaje) { this.mensaje = mensaje; }
    public boolean isLeida() { return leida; }
    public void setLeida(boolean leida) { this.leida = leida; }
    public LocalDateTime getCreadaEn() { return creadaEn; }
    public void setCreadaEn(LocalDateTime creadaEn) { this.creadaEn = creadaEn; }
    public String getDestino() { return destino; }
    public void setDestino(String destino) { this.destino = destino; }
    public String getMateria() { return materia; }
    public void setMateria(String materia) { this.materia = materia; }
    public String getPeriodo() { return periodo; }
    public void setPeriodo(String periodo) { this.periodo = periodo; }
    public Long getSolicitudId() { return solicitudId; }
    public void setSolicitudId(Long solicitudId) { this.solicitudId = solicitudId; }
    public String getClaveEvento() { return claveEvento; }
    public void setClaveEvento(String claveEvento) { this.claveEvento = claveEvento; }
}
