package ec.edu.c4.ant.cache;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.util.Set;

/**
 * Cola (set de Redis) con las consultas que fallaron porque la ANT estaba caida y que aun
 * no tienen copia en cache. {@code ReintentoAntJob} las reintenta en segundo plano.
 * Cada elemento tiene la forma {@code cedula:placa} (placa vacia = "-").
 */
@Repository
public class ConsultasPendientesRepository {

    private static final Logger log = LoggerFactory.getLogger(ConsultasPendientesRepository.class);
    static final String KEY = "ant:pendientes";

    private final StringRedisTemplate redis;

    public ConsultasPendientesRepository(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public void agregar(String cedula, String placa) {
        try {
            redis.opsForSet().add(KEY, cedula + ":" + (placa == null || placa.isBlank() ? "-" : placa));
        } catch (RuntimeException e) {
            log.warn("No se pudo registrar la consulta pendiente: {}", e.getMessage());
        }
    }

    public void quitar(String cedula, String placa) {
        try {
            redis.opsForSet().remove(KEY, cedula + ":" + (placa == null || placa.isBlank() ? "-" : placa));
        } catch (RuntimeException e) {
            log.warn("No se pudo quitar la consulta pendiente: {}", e.getMessage());
        }
    }

    public Set<String> listar() {
        try {
            Set<String> s = redis.opsForSet().members(KEY);
            return s == null ? Set.of() : s;
        } catch (RuntimeException e) {
            log.warn("No se pudo leer la cola de pendientes: {}", e.getMessage());
            return Set.of();
        }
    }
}
