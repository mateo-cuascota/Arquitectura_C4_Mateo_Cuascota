package ec.edu.c4.ant.model;

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
        String aviso
) {
    public enum Origen { ANT, CACHE, CACHE_RESPALDO }
}
