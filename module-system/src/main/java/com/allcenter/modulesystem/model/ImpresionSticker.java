package com.allcenter.modulesystem.model;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "impresionsticker")
@Getter
@Setter
public class ImpresionSticker {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "impresionid")
    private Long id;

    @Column(name = "usuarioid", nullable = false)
    private Long usuarioId;

    @Column(name = "orderid")
    private Long orderId;

    @Column(name = "metodo", length = 32)
    private String metodo;

    @Column(name = "equipo", length = 128)
    private String equipo;

    @Column(name = "ubicacion", length = 128)
    private String ubicacion;

    @Column(name = "direccion_ip", length = 64)
    private String direccionIp;

    @Column(name = "user_agent", length = 512)
    private String userAgent;

    @Column(name = "cantidad_etiquetas", nullable = false)
    private Integer cantidadEtiquetas = 0;

    @Column(name = "observaciones", length = 512)
    private String observaciones;

    @Column(name = "fecha", nullable = false)
    private OffsetDateTime fecha;

    @OneToMany(
            mappedBy = "impresion",
            cascade = CascadeType.ALL,
            orphanRemoval = true,
            fetch = FetchType.LAZY)
    private List<ImpresionStickerDetalle> detalles = new ArrayList<>();

    @PrePersist
    void prePersist() {
        if (fecha == null) {
            fecha = OffsetDateTime.now();
        }
        if (cantidadEtiquetas == null) {
            cantidadEtiquetas = detalles == null ? 0 : detalles.size();
        }
    }

    public void addDetalle(ImpresionStickerDetalle d) {
        d.setImpresion(this);
        this.detalles.add(d);
    }
}
