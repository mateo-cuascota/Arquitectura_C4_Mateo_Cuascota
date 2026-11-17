package ec.edu.c4.ant.service;

import ec.edu.c4.ant.cache.CachedLicencia;
import ec.edu.c4.ant.cache.ConsultasPendientesRepository;
import ec.edu.c4.ant.cache.LicenciaCacheRepository;
import ec.edu.c4.ant.client.AntClient;
import ec.edu.c4.ant.client.AntResponseParser;
import ec.edu.c4.ant.client.AntUnavailableException;
import ec.edu.c4.ant.demo.ModoDemo;
import org.springframework.beans.factory.annotation.Autowired;
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
 * Patron Cache-Aside con respaldo (stale-if-error) + Retry + Circuit Breaker + reintento en segundo plano.
 * <ol>
 *   <li>Se busca en Redis. Si la copia es reciente (&lt; fresh-ttl) se devuelve sin llamar a la ANT.</li>
 *   <li>Si no, se consulta la ANT. <b>Cada respuesta adecuada se guarda en Redis</b> y la respuesta
 *       se <b>devuelve leyendola desde el cache</b>.</li>
 *   <li>Si la ANT falla (timeout, 5xx, circuito abierto) se devuelve la ultima copia valida del cache.</li>
 *   <li>Si no existe copia, la consulta se encola y {@link ReintentoAntJob} la reintenta hasta guardarla; se responde 503.</li>
 * </ol>
 */
@Service
public class LicenciaService {

    private static final Logger log = LoggerFactory.getLogger(LicenciaService.class);

    private final AntClient ant;
    private final AntResponseParser parser;
    private final LicenciaCacheRepository cache;
    private final ConsultasPendientesRepository pendientes;
    private final AntMonitor monitor;
    private final ModoDemo modo;
    private final Duration freshTtl;

    @Autowired
    public LicenciaService(AntClient ant, AntResponseParser parser, LicenciaCacheRepository cache,
                           ConsultasPendientesRepository pendientes, AntMonitor monitor, ModoDemo modo,
                           @Value("${ant.cache.fresh-ttl:PT30M}") Duration freshTtl) {
        this.ant = ant;
        this.parser = parser;
        this.cache = cache;
        this.pendientes = pendientes;
        this.monitor = monitor;
        this.modo = modo;
        this.freshTtl = freshTtl;
    }

    public LicenciaService(AntClient ant, AntResponseParser parser, LicenciaCacheRepository cache,
                           ConsultasPendientesRepository pendientes, AntMonitor monitor,
                           @Value("${ant.cache.fresh-ttl:PT30M}") Duration freshTtl) {
        this.modo = new ModoDemo();
        this.monitor = monitor;
        this.ant = ant;
        this.parser = parser;
        this.cache = cache;
        this.pendientes = pendientes;
        this.freshTtl = freshTtl;
    }

    public LicenciaResponse consultar(String cedula, String placa) {
        return consultarInterno(cedula, placa).conSimulado(modo.activo());
    }

    public boolean modoDemoActivo() {
        return modo.activo();
    }

    private String clave(String cedula, String placa) {
        return modo.prefijo() + LicenciaCacheRepository.key(cedula, placa);
    }

    private LicenciaResponse consultarInterno(String cedula, String placa) {
        String key = clave(cedula, placa);
        Optional<CachedLicencia> cached = cache.find(key);

        // En modo demo la copia "reciente" dura 10 s para poder mostrar el respaldo sin esperar.
        Duration vigencia = modo.activo() ? Duration.ofSeconds(10) : freshTtl;
        if (cached.isPresent() && cached.get().cachedAt().isAfter(Instant.now().minus(vigencia))) {
            monitor.desdeCache();
            return new LicenciaResponse(cached.get().data(), Origen.CACHE, cached.get().cachedAt(),
                    "Información obtenida de la caché (consulta reciente)");
        }

        try {
            LicenciaInfo info = consultarAntYGuardar(cedula, placa);
            // Se devuelve la informacion desde el cache recien escrito (si Redis no responde, la respuesta en vivo).
            return cache.find(key)
                    .map(c -> new LicenciaResponse(c.data(), Origen.ANT, c.cachedAt(), null))
                    .orElseGet(() -> new LicenciaResponse(info, Origen.ANT, Instant.now(), null));
        } catch (Exception e) {
            log.warn("ANT no disponible para {}: {}", key, e.getMessage());
            if (cached.isPresent()) {
                monitor.desdeRespaldo();
                CachedLicencia c = cached.get();
                return new LicenciaResponse(c.data(), Origen.CACHE_RESPALDO, c.cachedAt(),
                        "La ANT no está disponible; se muestra la última información válida almacenada");
            }
            if (!modo.activo()) {
                pendientes.agregar(cedula, placa);
            }
            throw new AntUnavailableException(
                    "El portal de la ANT no responde y todavía no hay información guardada en la caché para esta cédula. "
                            + "La consulta quedó en cola y se reintentará automáticamente.", e);
        }
    }

    /**
     * Llama a la ANT y, si la respuesta es adecuada, la guarda en el cache y saca la consulta de la cola.
     * Lanza excepcion si la ANT no responde correctamente.
     */
    public LicenciaInfo consultarAntYGuardar(String cedula, String placa) {
        LicenciaInfo info;
        try {
            info = parser.parse(ant.consultarCitaciones(cedula, placa), cedula, placa);
        } catch (RuntimeException e) {
            monitor.falloAnt(e.getMessage());
            throw e;
        }
        monitor.exitoAnt();
        cache.save(clave(cedula, placa), info);
        if (!modo.activo()) {
            pendientes.quitar(cedula, placa);
        }
        return info;
    }
}
