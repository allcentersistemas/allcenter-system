package com.allcenter.modulesystem.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.allcenter.modulesystem.dto.GuiaDtos.AddGuiaDetalleManualRequest;
import com.allcenter.modulesystem.dto.GuiaDtos.AddGuiaDetallePaleRequest;
import com.allcenter.modulesystem.dto.GuiaDtos.CreateGuiaRequest;
import com.allcenter.modulesystem.dto.GuiaDtos.GuiaDetalleLineDto;
import com.allcenter.modulesystem.dto.GuiaDtos.GuiaResponse;
import com.allcenter.modulesystem.model.Pale;
import com.allcenter.modulesystem.repository.GuiaRepository;
import com.allcenter.modulesystem.repository.PaleRepository;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@SpringBootTest
@Transactional
class GuiaInventoryIntegrationTest {

    @Autowired
    private GuiaInventoryService guiaInventoryService;

    @Autowired
    private PaleRepository paleRepository;

    @Autowired
    private GuiaRepository guiaRepository;

    @Test
    void flujoCompleto_numeroCorrelativo_lineaManual_yPaleEscaneado() {
        Pale paleEscaneado = paleRepository.save(pale("P-ESC-001", "ESCANEADO", 8, "OC-100, OC-101"));
        Pale palePendiente = paleRepository.save(pale("P-PEN-001", "PENDIENTE", 3, "OC-200"));

        GuiaResponse creada = guiaInventoryService.createGuia(new CreateGuiaRequest("Notas prueba", null, null, 99L));
        assertThat(creada.guia().numeroGuia()).isEqualTo("G-000001");
        assertThat(creada.guia().estado()).isEqualTo("BORRADOR");
        assertThat(creada.detalles()).isEmpty();

        long guiaId = creada.guia().guiaId();

        GuiaResponse conManual =
                guiaInventoryService.addDetalleManual(
                        guiaId,
                        new AddGuiaDetalleManualRequest("Cable utp", "metros", "25"));
        assertThat(conManual.detalles()).hasSize(1);
        GuiaDetalleLineDto manual = conManual.detalles().getFirst();
        assertThat(manual.paleId()).isNull();
        assertThat(manual.descripcion()).isEqualTo("Cable utp");
        assertThat(manual.unidadMedida()).isEqualTo("metros");
        assertThat(manual.cantidad()).isEqualTo("25");

        GuiaResponse conPale =
                guiaInventoryService.addDetalleFromPale(guiaId, new AddGuiaDetallePaleRequest(paleEscaneado.getId()));
        assertThat(conPale.detalles()).hasSize(2);
        GuiaDetalleLineDto desdePale =
                conPale.detalles().stream().filter(d -> d.paleId() != null).findFirst().orElseThrow();
        assertThat(desdePale.descripcion()).isEqualTo("P-ESC-001 · OC-100, OC-101");
        assertThat(desdePale.unidadMedida()).isEqualTo("piezas");
        assertThat(desdePale.cantidad()).isEqualTo("8");

        assertThat(guiaInventoryService.listPalesEscaneados())
                .extracting(p -> p.paleId())
                .contains(paleEscaneado.getId());

        assertThatThrownBy(
                        () ->
                                guiaInventoryService.addDetalleFromPale(
                                        guiaId, new AddGuiaDetallePaleRequest(palePendiente.getId())))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(
                        ex ->
                                assertThat(((ResponseStatusException) ex).getStatusCode().value())
                                        .isEqualTo(400));

        assertThatThrownBy(
                        () ->
                                guiaInventoryService.addDetalleFromPale(
                                        guiaId, new AddGuiaDetallePaleRequest(paleEscaneado.getId())))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(
                        ex ->
                                assertThat(((ResponseStatusException) ex).getStatusCode().value())
                                        .isEqualTo(409));

        GuiaResponse segunda = guiaInventoryService.createGuia(new CreateGuiaRequest(null, null, null, null));
        assertThat(segunda.guia().numeroGuia()).isEqualTo("G-000002");

        assertThat(guiaRepository.findMaxCorrelativoSequence()).isEqualTo(2L);
    }

    private static Pale pale(String codigo, String estadoEnvio, int piezas, String ordenesResumen) {
        Pale p = new Pale();
        p.setCodigo(codigo);
        p.setEstado("CERRADO");
        p.setEstadoEnvio(estadoEnvio);
        p.setCantidadPiezas(piezas);
        p.setCantidadOrdenes(1);
        p.setOrdenesResumen(ordenesResumen);
        p.setFechaCreacion(LocalDateTime.now());
        return p;
    }
}
