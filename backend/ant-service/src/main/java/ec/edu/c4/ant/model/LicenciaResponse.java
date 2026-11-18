package ec.edu.c4.ant.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Duration;
import java.time.Instant;

/**
 * Respuesta expuesta al front end.
 *
 * @param origen      ANT (respuesta en vivo), CACHE (copia reciente) o CACHE_RESPALDO (ANT caida, se usa la ultima copia valida)
 * @param obtenidoEn  momento en que la ANT entrego esa informacion
 */
public record LicenciaResponse(
        LicenciaInfo licencia,
        Origen origen,
        Instant obtenidoEn,
        String aviso,
        boolean simulado
) {
    public LicenciaResponse(LicenciaInfo licencia, Origen origen, Instant obtenidoEn, String aviso) {
        this(licencia, origen, obtenidoEn, aviso, false);
    }

    public LicenciaResponse conSimulado(boolean s) {
        return new LicenciaResponse(licencia, origen, obtenidoEn, aviso, s);
    }

    /** Antiguedad del dato en segundos (cuanto hace que la ANT lo entrego). */
    @JsonProperty("antiguedadSegundos")
    public long antiguedadSegundos() {
        return obtenidoEn == null ? 0 : Math.max(0, Duration.between(obtenidoEn, Instant.now()).toSeconds());
    }

    public enum Origen { ANT, CACHE, CACHE_RESPALDO }
}
