package com.allcenter.modulebiesse.model;

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

/**
 * Línea de una impresión de sticker. Una por cada pieza física impresa en el evento.
 * Mantiene un snapshot JSON de la etiqueta para que la trazabilidad sobreviva incluso
 * si la parte/pieza se modifica luego.
 */
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

    /** Texto exacto codificado en el QR (compatible con {@code pieces/resolve}). */
    @Column(name = "codigo_qr", length = 256)
    private String codigoQr;

    /** Snapshot JSON con los datos clave (descripcion, dims, material, edges, etc.). */
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
