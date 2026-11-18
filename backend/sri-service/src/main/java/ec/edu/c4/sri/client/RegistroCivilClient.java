package ec.edu.c4.sri.client;

import com.fasterxml.jackson.databind.JsonNode;
import ec.edu.c4.sri.model.PersonaRegistro;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Optional;

/**
 * Consulta del Registro Civil del Ecuador a traves de Didit (servicio de verificacion de identidad).
 * Se usa solo para cedulas que el SRI no conoce (personas sin RUC) y solo si hay una API key configurada
 * en DIDIT_API_KEY. Doc: https://docs.didit.me/api-reference/database-validation/ecuador/cedula
 */
@Component
public class RegistroCivilClient {

    private static final Logger log = LoggerFactory.getLogger(RegistroCivilClient.class);
    public static final String FUENTE = "Registro Civil (Didit)";

    private final RestClient rest;
    private final String apiKey;

    public RegistroCivilClient(@Value("${didit.base-url:https://verification.didit.me}") String baseUrl,
                               @Value("${didit.api-key:}") String apiKey) {
        SimpleClientHttpRequestFactory f = new SimpleClientHttpRequestFactory();
        f.setConnectTimeout(8000);
        f.setReadTimeout(12000);
        this.rest = RestClient.builder().baseUrl(baseUrl).requestFactory(f).build();
        this.apiKey = apiKey == null ? "" : apiKey.trim();
    }

    public boolean configurado() {
        return !apiKey.isEmpty();
    }

    @Cacheable(cacheNames = "sri-registro-civil", key = "#cedula", unless = "#result == null")
    public Optional<PersonaRegistro> buscarPorCedula(String cedula) {
        if (!configurado()) {
            return Optional.empty();
        }
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("issuing_state", "ECU");
        form.add("services", "ecu_cedula");
        form.add("personal_number", cedula);
        try {
            JsonNode r = rest.post()
                    .uri("/v3/database-validation/")
                    .header("x-api-key", apiKey)
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(form)
                    .retrieve()
                    .body(JsonNode.class);
            return extraer(r, cedula);
        } catch (RestClientException e) {
            log.warn("No se pudo consultar el Registro Civil (Didit): {}", e.getMessage());
            return Optional.empty();
        }
    }

    static Optional<PersonaRegistro> extraer(JsonNode r, String cedula) {
        if (r == null) {
            return Optional.empty();
        }
        for (JsonNode v : r.path("validations")) {
            if (!"MATCH".equalsIgnoreCase(v.path("outcome_code").asText())) {
                continue;
            }
            JsonNode d = v.path("source_data");
            String nombre = (d.path("last_name").asText("") + " " + d.path("first_name").asText("")).trim();
            if (!nombre.isBlank()) {
                return Optional.of(new PersonaRegistro(cedula, nombre.toUpperCase(), "NAT", FUENTE));
            }
        }
        return Optional.empty();
    }
}
