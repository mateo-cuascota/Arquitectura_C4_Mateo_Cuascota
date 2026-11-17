package ec.edu.c4.ant.service;

import ec.edu.c4.ant.cache.CachedLicencia;
import ec.edu.c4.ant.cache.LicenciaCacheRepository;
import ec.edu.c4.ant.client.AntClient;
import ec.edu.c4.ant.client.AntResponseParser;
import ec.edu.c4.ant.client.AntUnavailableException;
import ec.edu.c4.ant.model.LicenciaInfo;
import ec.edu.c4.ant.model.LicenciaResponse;
import ec.edu.c4.ant.model.LicenciaResponse.Origen;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * Patron Cache-Aside con respaldo (stale-if-error) + Circuit Breaker:
 * <ol>
 *   <li>Se busca en Redis. Si la copia es reciente (&lt; fresh-ttl) se devuelve sin llamar a la ANT.</li>
 *   <li>Si no, se consulta la ANT. Cada respuesta adecuada se guarda en Redis (TTL largo).</li>
 *   <li>Si la ANT falla (timeout, 5xx, circuito abierto), se devuelve la ultima copia valida del cache.</li>
 *   <li>Solo si no existe copia en cache se responde 503.</li>
 * </ol>
 */
@Service
public class LicenciaService {

    private static final Logger log = LoggerFactory.getLogger(LicenciaService.class);

    private final AntClient ant;
    private final AntResponseParser parser;
    private final LicenciaCacheRepository cache;
    private final Duration freshTtl;

    public LicenciaService(AntClient ant, AntResponseParser parser, LicenciaCacheRepository cache,
                           @Value("${ant.cache.fresh-ttl:PT30M}") Duration freshTtl) {
        this.ant = ant;
        this.parser = parser;
        this.cache = cache;
        this.freshTtl = freshTtl;
    }

    public LicenciaResponse consultar(String cedula, String placa) {
        String key = LicenciaCacheRepository.key(cedula, placa);
        Optional<CachedLicencia> cached = cache.find(key);

        if (cached.isPresent() && cached.get().cachedAt().isAfter(Instant.now().minus(freshTtl))) {
            return new LicenciaResponse(cached.get().data(), Origen.CACHE, cached.get().cachedAt(),
                    "Información obtenida de la caché (consulta reciente)");
        }

        try {
            String body = ant.consultarCitaciones(cedula, placa);
            LicenciaInfo info = parser.parse(body, cedula, placa);
            cache.save(key, info);
            return new LicenciaResponse(info, Origen.ANT, Instant.now(), null);
        } catch (Exception e) {
            log.warn("ANT no disponible para {}: {}", key, e.getMessage());
            return cached
                    .map(c -> new LicenciaResponse(c.data(), Origen.CACHE_RESPALDO, c.cachedAt(),
                            "La ANT no está disponible; se muestra la última información válida almacenada"))
                    .orElseThrow(() -> new AntUnavailableException(
                            "El portal de la ANT no responde y todavía no hay información guardada en la caché para esta cédula.", e));
        }
    }
}
