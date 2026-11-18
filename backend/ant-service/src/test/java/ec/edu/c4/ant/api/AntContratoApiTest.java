package ec.edu.c4.ant.api;

import ec.edu.c4.ant.client.AntUnavailableException;
import ec.edu.c4.ant.model.LicenciaInfo;
import ec.edu.c4.ant.model.LicenciaResponse;
import ec.edu.c4.ant.model.LicenciaResponse.Origen;
import ec.edu.c4.ant.service.LicenciaService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Contrato HTTP del ANT Service (docs/api-contrato.md). */
@WebMvcTest(LicenciaController.class)
class AntContratoApiTest {

    @Autowired
    MockMvc mvc;

    @MockBean
    LicenciaService service;

    @Test
    void respuestaDesdeRespaldoIndicaOrigenFechaYAviso() throws Exception {
        var info = new LicenciaInfo("1726617747", "", 24, 6.0, 1, List.of());
        when(service.consultar("1726617747", "")).thenReturn(new LicenciaResponse(info, Origen.CACHE_RESPALDO,
                Instant.parse("2026-11-16T10:00:00Z"), "La ANT no está disponible; se muestra la última información válida almacenada"));

        mvc.perform(get("/api/ant/licencias/{cedula}", "1726617747"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.origen").value("CACHE_RESPALDO"))
                .andExpect(jsonPath("$.obtenidoEn").value("2026-11-16T10:00:00Z"))
                .andExpect(jsonPath("$.aviso").exists())
                .andExpect(jsonPath("$.licencia.puntosDisponibles").value(24));
    }

    @Test
    void antCaidaSinCache503ConRetryAfter() throws Exception {
        when(service.consultar("1726617747", "")).thenThrow(new AntUnavailableException("El portal de la ANT no responde"));

        mvc.perform(get("/api/ant/licencias/{cedula}", "1726617747"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().string("Retry-After", "60"))
                .andExpect(jsonPath("$.codigo").value("ANT_NO_DISPONIBLE"))
                .andExpect(jsonPath("$.reintentarEnSegundos").value(60))
                .andExpect(jsonPath("$.mensaje").value("El portal de la ANT no responde"));
    }

    @Test
    void cedulaInvalida400() throws Exception {
        mvc.perform(get("/api/ant/licencias/{cedula}", "12AB"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("CEDULA_INVALIDA"));
    }

    @Test
    void placaInvalida400() throws Exception {
        mvc.perform(get("/api/ant/licencias/{cedula}", "1726617747").param("placa", "PB@1234"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("PLACA_INVALIDA"));
    }

    @Test
    void metodoNoPermitido405() throws Exception {
        mvc.perform(delete("/api/ant/licencias/{cedula}", "1726617747"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.codigo").value("METODO_NO_PERMITIDO"));
    }
}
