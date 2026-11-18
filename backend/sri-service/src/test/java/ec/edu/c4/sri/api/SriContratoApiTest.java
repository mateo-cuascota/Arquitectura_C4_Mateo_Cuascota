package ec.edu.c4.sri.api;

import ec.edu.c4.sri.model.ContribuyenteResponse;
import ec.edu.c4.sri.model.PersonaRegistro;
import ec.edu.c4.sri.service.BusinessRuleException;
import ec.edu.c4.sri.service.ContribuyenteService;
import ec.edu.c4.sri.service.NotFoundException;
import ec.edu.c4.sri.service.ValidacionException;
import ec.edu.c4.sri.service.VehiculoService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.ResourceAccessException;

import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Contrato HTTP del SRI Service: verbos, codigos de estado y forma de los errores (docs/api-contrato.md). */
@WebMvcTest(SriController.class)
class SriContratoApiTest {

    private static final String URL = "/api/sri/contribuyentes/{id}";

    @Autowired
    MockMvc mvc;

    @MockBean
    ContribuyenteService contribuyentes;

    @MockBean
    VehiculoService vehiculos;

    @Test
    void verificacionExitosaDevuelve200() throws Exception {
        when(contribuyentes.verificar("1726617747", "a@b.com", false)).thenReturn(new ContribuyenteResponse(
                "1726617747001", "1726617747", "a@b.com", true, true,
                new PersonaRegistro("1726617747", "PEREZ JUAN", "NAT", "SRI"), null));

        mvc.perform(get(URL, "1726617747").param("email", "a@b.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.esContribuyente").value(true))
                .andExpect(jsonPath("$.persona.nombreCompleto").value("PEREZ JUAN"));
    }

    @Test
    void faltaElCorreo400() throws Exception {
        mvc.perform(get(URL, "1726617747"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("CORREO_REQUERIDO"))
                .andExpect(jsonPath("$.mensaje").value("El correo electrónico es obligatorio"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.instance").value("/api/sri/contribuyentes/1726617747"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void correoMalFormado400() throws Exception {
        mvc.perform(get(URL, "1726617747").param("email", "no-es-correo"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("CORREO_INVALIDO"));
    }

    @Test
    void formatoDeIdentificacionInvalido400() throws Exception {
        mvc.perform(get(URL, "123").param("email", "a@b.com"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("IDENTIFICACION_INVALIDA"));
    }

    @Test
    void cedulaConDigitoVerificadorIncorrecto400() throws Exception {
        when(contribuyentes.verificar(anyString(), anyString(), anyBoolean()))
                .thenThrow(new ValidacionException("CEDULA_INVALIDA", "La cédula 1726617748 no es válida"));

        mvc.perform(get(URL, "1726617748").param("email", "a@b.com"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("CEDULA_INVALIDA"));
    }

    @Test
    void rucNoRegistrado404() throws Exception {
        when(contribuyentes.verificar(anyString(), anyString(), anyBoolean()))
                .thenThrow(new NotFoundException("RUC_NO_REGISTRADO", "El RUC no está registrado"));

        mvc.perform(get(URL, "1726617747001").param("email", "a@b.com"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("RUC_NO_REGISTRADO"));
    }

    @Test
    void noEsPersonaNatural422() throws Exception {
        when(contribuyentes.verificar(anyString(), anyString(), anyBoolean()))
                .thenThrow(new BusinessRuleException("NO_ES_PERSONA_NATURAL", "Corresponde a SOCIEDAD"));

        mvc.perform(get(URL, "1726617747001").param("email", "a@b.com"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("NO_ES_PERSONA_NATURAL"));
    }

    @Test
    void sriCaido502() throws Exception {
        when(contribuyentes.verificar(anyString(), anyString(), anyBoolean()))
                .thenThrow(new ResourceAccessException("timeout"));

        mvc.perform(get(URL, "1726617747").param("email", "a@b.com"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.codigo").value("SRI_NO_DISPONIBLE"));
    }

    @Test
    void vehiculoNoEncontrado404() throws Exception {
        when(vehiculos.obtener("XYZ9999"))
                .thenThrow(new NotFoundException("VEHICULO_NO_ENCONTRADO", "No se encontró un vehículo con placa XYZ9999"));

        mvc.perform(get("/api/sri/vehiculos/{placa}", "XYZ9999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("VEHICULO_NO_ENCONTRADO"));
    }

    @Test
    void placaInvalida400() throws Exception {
        mvc.perform(get("/api/sri/vehiculos/{placa}", "AB"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("PLACA_INVALIDA"));
    }

    @Test
    void metodoNoPermitido405() throws Exception {
        mvc.perform(post(URL, "1726617747").param("email", "a@b.com"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.codigo").value("METODO_NO_PERMITIDO"));
    }

    @Test
    void rutaInexistente404() throws Exception {
        mvc.perform(get("/api/sri/no-existe"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("RUTA_NO_ENCONTRADA"));
    }
}
