package com.prisma.api.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "notificaciones")
@com.fasterxml.jackson.annotation.JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class Notificacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = false)
    @com.fasterxml.jackson.annotation.JsonIgnore
    private Usuario usuario;

    @Column(nullable = false, length = 150)
    private String titulo;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String cuerpo;

    @Column(nullable = false, length = 50)
    private String tipo = "GENERAL";

    @Column(name = "pedido_id")
    private Long pedidoId;

    @Column(name = "numero_pedido", length = 50)
    private String numeroPedido;

    @Column(name = "estado_pedido", length = 30)
    private String estadoPedido;

    @Column(nullable = false)
    private boolean leida = false;

    @Column(name = "fcm_token_destino", length = 255)
    private String fcmTokenDestino;

    @Column(name = "creado_en", nullable = false, updatable = false)
    private OffsetDateTime creadoEn = OffsetDateTime.now();

    public Notificacion() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Usuario getUsuario() { return usuario; }
    public void setUsuario(Usuario usuario) { this.usuario = usuario; }

    public String getTitulo() { return titulo; }
    public void setTitulo(String titulo) { this.titulo = titulo; }

    public String getCuerpo() { return cuerpo; }
    public void setCuerpo(String cuerpo) { this.cuerpo = cuerpo; }

    public String getTipo() { return tipo; }
    public void setTipo(String tipo) { this.tipo = tipo; }

    public Long getPedidoId() { return pedidoId; }
    public void setPedidoId(Long pedidoId) { this.pedidoId = pedidoId; }

    public String getNumeroPedido() { return numeroPedido; }
    public void setNumeroPedido(String numeroPedido) { this.numeroPedido = numeroPedido; }

    public String getEstadoPedido() { return estadoPedido; }
    public void setEstadoPedido(String estadoPedido) { this.estadoPedido = estadoPedido; }

    public boolean isLeida() { return leida; }
    public void setLeida(boolean leida) { this.leida = leida; }

    public String getFcmTokenDestino() { return fcmTokenDestino; }
    public void setFcmTokenDestino(String fcmTokenDestino) { this.fcmTokenDestino = fcmTokenDestino; }

    public OffsetDateTime getCreadoEn() { return creadoEn; }
    public void setCreadoEn(OffsetDateTime creadoEn) { this.creadoEn = creadoEn; }
}
