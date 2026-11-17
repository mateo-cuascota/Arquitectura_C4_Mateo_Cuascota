package ec.edu.c4.ant.cache;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import ec.edu.c4.ant.model.LicenciaInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * Acceso al cache en la nube (Redis: Redis Cloud / Azure Cache for Redis / Upstash...).
 * Los fallos del cache nunca rompen la consulta: se registran y se continua.
 */
@Repository
public class LicenciaCacheRepository {

    private static final Logger log = LoggerFactory.getLogger(LicenciaCacheRepository.class);
    private static final String PREFIX = "ant:licencia:";

    private final StringRedisTemplate redis;
    private final ObjectMapper mapper;
    private final Duration ttl;

    public LicenciaCacheRepository(StringRedisTemplate redis, ObjectMapper mapper,
                                   @Value("${ant.cache.ttl:P7D}") Duration ttl) {
        this.redis = redis;
        this.mapper = mapper;
        this.ttl = ttl;
    }

    public static String key(String cedula, String placa) {
        return PREFIX + cedula + ":" + (placa == null || placa.isBlank() ? "-" : placa.toUpperCase());
    }

    public void save(String key, LicenciaInfo info) {
        try {
            String json = mapper.writeValueAsString(new CachedLicencia(info, Instant.now()));
            redis.opsForValue().set(key, json, ttl);
        } catch (JsonProcessingException e) {
            log.error("No se pudo serializar la licencia para el cache", e);
        } catch (RuntimeException e) {
            log.warn("Cache no disponible al guardar {}: {}", key, e.getMessage());
        }
    }

    public Optional<CachedLicencia> find(String key) {
        try {
            String json = redis.opsForValue().get(key);
            return json == null ? Optional.empty() : Optional.of(mapper.readValue(json, CachedLicencia.class));
        } catch (JsonProcessingException e) {
            log.warn("Entrada de cache corrupta {}", key);
            return Optional.empty();
        } catch (RuntimeException e) {
            log.warn("Cache no disponible al leer {}: {}", key, e.getMessage());
            return Optional.empty();
        }
    }
}
