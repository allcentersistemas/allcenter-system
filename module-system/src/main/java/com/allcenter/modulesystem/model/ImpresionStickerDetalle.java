package com.allcenter.modulesystem.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "impresionstickerdetalle")
@Getter
@Setter
public class ImpresionStickerDetalle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "impresiondetalleid")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "impresionid", nullable = false)
    private ImpresionSticker impresion;

    @Column(name = "partid")
    private Long partId;

    @Column(name = "piezaid")
    private Long piezaId;

    @Column(name = "numero_pieza")
    private Integer numeroPieza;

    @Column(name = "codigo_qr", length = 256)
    private String codigoQr;

    @Column(name = "snapshot", columnDefinition = "TEXT")
    private String snapshot;

    @Column(name = "fecha", nullable = false)
    private OffsetDateTime fecha;

    @PrePersist
    void prePersist() {
        if (fecha == null) {
            fecha = OffsetDateTime.now();
        }
    }
}
