package ec.edu.c4.ant.api;

import ec.edu.c4.ant.demo.ModoDemo;
import ec.edu.c4.ant.service.AntMonitor;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

/** Estado del patron de resiliencia: circuito de la ANT, conexion a Redis y contenido de la cache. */
@Tag(name = "Estado", description = "Monitoreo del patrón de caché y del circuit breaker")
@RestController
@RequestMapping("/api/ant")
public class EstadoController {

    private final AntMonitor monitor;
    private final CircuitBreakerRegistry circuitos;
    private final StringRedisTemplate redis;
    private final ModoDemo modo;
    private final java.time.Duration ttlReciente;
    private final java.time.Duration ttlRespaldo;

    public EstadoController(AntMonitor monitor, CircuitBreakerRegistry circuitos, StringRedisTemplate redis, ModoDemo modo,
                            @org.springframework.beans.factory.annotation.Value("${ant.cache.fresh-ttl:PT30M}") java.time.Duration ttlReciente,
                            @org.springframework.beans.factory.annotation.Value("${ant.cache.ttl:P7D}") java.time.Duration ttlRespaldo) {
        this.ttlReciente = ttlReciente;
        this.ttlRespaldo = ttlRespaldo;
        this.modo = modo;
        this.monitor = monitor;
        this.circuitos = circuitos;
        this.redis = redis;
    }

    public record Estado(
            String circuitoAnt,
            float tasaFallosAnt,
            boolean redisConectado,
            long consultasEnCache,
            long consultasPendientes,
            long respuestasAnt,
            long respuestasCache,
            long respuestasRespaldo,
            long fallosAnt,
            Instant ultimaRespuestaAnt,
            Instant ultimoFalloAnt,
            String ultimoError,
            boolean modoDemo,
            boolean antCaidaSimulada,
            long segundosParaReintentarAnt,
            long ttlRecienteSegundos,
            long ttlRespaldoSegundos
    ) { }

    /** Debe coincidir con resilience4j.circuitbreaker.instances.ant.wait-duration-in-open-state. */
    private static final long ESPERA_CIRCUITO_ABIERTO = 60;

    @Operation(summary = "Estado del circuito de la ANT, de Redis y de la caché")
    @GetMapping("/estado")
    public Estado estado() {
        CircuitBreaker cb = circuitos.circuitBreaker("ant");
        boolean redisOk = false;
        long enCache = 0;
        long pendientes = 0;
        try {
            redisOk = "PONG".equalsIgnoreCase(redis.execute((org.springframework.data.redis.core.RedisCallback<String>) c -> c.ping()));
            try (Cursor<String> cursor = redis.scan(ScanOptions.scanOptions().match(modo.prefijo() + "ant:licencia:*").count(500).build())) {
                while (cursor.hasNext() && enCache < 10_000) {
                    cursor.next();
                    enCache++;
                }
            }
            Long n = redis.opsForSet().size("ant:pendientes");
            pendientes = n == null ? 0 : n;
        } catch (RuntimeException e) {
            redisOk = false;
        }
        return new Estado(
                cb.getState().name(),
                cb.getMetrics().getFailureRate(),
                redisOk, enCache, pendientes,
                monitor.respuestasAnt(), monitor.respuestasCache(), monitor.respuestasRespaldo(), monitor.fallosAnt(),
                monitor.ultimaRespuestaOk(), monitor.ultimoFallo(), monitor.ultimoError(),
                modo.activo(), modo.antCaida(), segundosParaReintentar(cb),
                modo.activo() ? 10 : ttlReciente.toSeconds(), ttlRespaldo.toSeconds());
    }

    /** Segundos que faltan para que el circuito abierto pase a semiabierto y vuelva a probar la ANT. */
    private long segundosParaReintentar(CircuitBreaker cb) {
        if (cb.getState() != CircuitBreaker.State.OPEN || monitor.ultimoFallo() == null) {
            return 0;
        }
        long transcurridos = java.time.Duration.between(monitor.ultimoFallo(), Instant.now()).toSeconds();
        return Math.max(0, ESPERA_CIRCUITO_ABIERTO - transcurridos);
    }
}
