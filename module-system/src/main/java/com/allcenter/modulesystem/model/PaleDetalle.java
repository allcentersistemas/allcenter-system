package com.allcenter.modulesystem.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "paledetalle")
@Getter
@Setter
public class PaleDetalle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "paledetalleid")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "paleenvioid", nullable = false)
    private Pale pale;

    @Column(name = "piezaid", nullable = false)
    private Long piezaId;

    @Column(name = "partid", nullable = false)
    private Long partId;

    @Column(name = "orderid", nullable = false)
    private Long orderId;

    @Column(name = "ordername", length = 200)
    private String orderName;

    @Column(name = "partcode", length = 120)
    private String partCode;

    @Column(name = "descripcion", columnDefinition = "TEXT")
    private String descripcion;

    @Column(name = "descripcion1", columnDefinition = "TEXT")
    private String descripcion1;

    @Column(name = "numero_pieza")
    private Integer numeroPieza;

    @Column(name = "medida", length = 80)
    private String medida;

    @Column(name = "total_piezas")
    private Integer totalPiezas;

    @Column(name = "agregado_por")
    private Long agregadoPor;

    @Column(name = "fecha_agregado", nullable = false)
    private LocalDateTime fechaAgregado;
}
