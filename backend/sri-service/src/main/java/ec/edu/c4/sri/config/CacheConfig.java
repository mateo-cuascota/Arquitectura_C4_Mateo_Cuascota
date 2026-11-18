package ec.edu.c4.sri.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import ec.edu.c4.sri.model.Contribuyente;
import ec.edu.c4.sri.model.PersonaRegistro;
import ec.edu.c4.sri.model.Vehiculo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.cache.RedisCacheManagerBuilderCustomizer;
import org.springframework.cache.Cache;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair;

import java.time.Duration;

/**
 * Cache en la nube (Redis / Upstash) para las respuestas del SRI.
 * Claves: sri:&lt;cache&gt;::&lt;id&gt;. Si Redis no esta disponible, el servicio sigue consultando al SRI.
 */
@Configuration
@EnableCaching
public class CacheConfig implements CachingConfigurer {

    private static final Logger log = LoggerFactory.getLogger(CacheConfig.class);

    @Bean
    RedisCacheManagerBuilderCustomizer cachesSri(ObjectMapper mapper,
                                                 @Value("${sri.cache.ttl:PT6H}") Duration ttl) {
        return builder -> builder
                .withCacheConfiguration("sri-existe-ruc", config(mapper, Boolean.class, ttl))
                .withCacheConfiguration("sri-contribuyente", config(mapper, Contribuyente.class, ttl))
                .withCacheConfiguration("sri-persona", config(mapper, PersonaRegistro.class, ttl))
                .withCacheConfiguration("sri-registro-civil", config(mapper, PersonaRegistro.class, Duration.ofDays(7)))
                .withCacheConfiguration("sri-vehiculo", config(mapper, Vehiculo.class, ttl));
    }

    private static <T> RedisCacheConfiguration config(ObjectMapper mapper, Class<T> tipo, Duration ttl) {
        return RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(ttl)
                .disableCachingNullValues()
                .prefixCacheNameWith("sri:")
                .serializeValuesWith(SerializationPair.fromSerializer(new Jackson2JsonRedisSerializer<>(mapper, tipo)));
    }

    /** Los fallos de Redis no deben romper la consulta: se registran y se va directo al SRI. */
    @Override
    public CacheErrorHandler errorHandler() {
        return new CacheErrorHandler() {
            @Override
            public void handleCacheGetError(RuntimeException e, Cache cache, Object key) {
                log.warn("Cache {} no disponible al leer {}: {}", cache.getName(), key, e.getMessage());
            }

            @Override
            public void handleCachePutError(RuntimeException e, Cache cache, Object key, Object value) {
                log.warn("Cache {} no disponible al guardar {}: {}", cache.getName(), key, e.getMessage());
            }

            @Override
            public void handleCacheEvictError(RuntimeException e, Cache cache, Object key) {
                log.warn("Cache {} no disponible al borrar {}: {}", cache.getName(), key, e.getMessage());
            }

            @Override
            public void handleCacheClearError(RuntimeException e, Cache cache) {
                log.warn("Cache {} no disponible al limpiar: {}", cache.getName(), e.getMessage());
            }
        };
    }
}
