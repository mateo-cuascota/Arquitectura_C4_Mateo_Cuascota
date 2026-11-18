package ec.edu.c4.sri.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RegistroCivilClientTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void extraeNombreCuandoHayCoincidencia() throws Exception {
        var json = mapper.readTree("""
                {"request_id":"x","status":"Approved","issuing_state":"ECU","match_type":"full_match",
                 "validations":[{"outcome_code":"MATCH","service_id":"ecu_cedula",
                   "source_data":{"first_name":"Ana Maria","last_name":"Perez Lopez","identification_number":"1710034065"},
                   "validation":{"identification_number":"full_match"}}]}
                """);

        var p = RegistroCivilClient.extraer(json, "1710034065");

        assertThat(p).isPresent();
        assertThat(p.get().nombreCompleto()).isEqualTo("PEREZ LOPEZ ANA MARIA");
        assertThat(p.get().fuente()).isEqualTo(RegistroCivilClient.FUENTE);
    }

    @Test
    void sinCoincidenciaNoDevuelveNada() throws Exception {
        var json = mapper.readTree("""
                {"status":"Declined","match_type":"no_match",
                 "validations":[{"outcome_code":"NO_MATCH","source_data":{"identification_number":"NO_MATCH"}}]}
                """);

        assertThat(RegistroCivilClient.extraer(json, "1710034065")).isEmpty();
    }

    @Test
    void sinApiKeyNoConsulta() {
        var cliente = new RegistroCivilClient("https://verification.didit.me", "");
        assertThat(cliente.configurado()).isFalse();
        assertThat(cliente.buscarPorCedula("1710034065")).isEmpty();
    }
}
