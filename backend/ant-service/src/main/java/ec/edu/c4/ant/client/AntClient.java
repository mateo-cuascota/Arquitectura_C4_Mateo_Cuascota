package ec.edu.c4.ant.client;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Locale;

/**
 * Cliente hacia el portal web de la ANT (baja disponibilidad).
 * Protegido con Retry + Circuit Breaker (Resilience4j): si la ANT falla repetidamente
 * el circuito se abre y se deja de golpearla durante un tiempo.
 */
@Component
public class AntClient {

    private static final String CITACIONES =
            "/PortalWEB/paginas/clientes/clp_grid_citaciones.jsp"
                    + "?ps_tipo_identificacion=CED&ps_identificacion={cedula}&ps_placa={placa}";

    private final RestClient rest;

    public AntClient(RestClient antRestClient) {
        this.rest = antRestClient;
    }

    @Retry(name = "ant")
    @CircuitBreaker(name = "ant")
    public String consultarCitaciones(String cedula, String placa) {
        String body;
        try {
            body = rest.get()
                    .uri(CITACIONES, cedula, placa == null ? "" : placa)
                    .retrieve()
                    .body(String.class);
        } catch (RestClientException e) {
            throw new AntUnavailableException("Error al invocar la ANT: " + e.getMessage(), e);
        }
        if (!esRespuestaAdecuada(body)) {
            throw new AntUnavailableException("La ANT devolvio una respuesta vacia o una pagina de error");
        }
        return body;
    }

    /** Una respuesta es adecuada si tiene contenido y no es una pagina de error del proxy/servidor. */
    static boolean esRespuestaAdecuada(String body) {
        if (body == null || body.isBlank()) {
            return false;
        }
        String b = body.toLowerCase(Locale.ROOT);
        return !(b.contains("bad gateway") || b.contains("service unavailable")
                || b.contains("gateway time-out") || b.contains("gateway timeout")
                || b.contains("internal server error"));
    }
}
