package ec.edu.c4.ant.service;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/** Registra el resultado de las llamadas a la ANT y del uso de la cache para el endpoint de estado. */
@Component
public class AntMonitor {

    private final AtomicReference<Instant> ultimaRespuestaOk = new AtomicReference<>();
    private final AtomicReference<Instant> ultimoFallo = new AtomicReference<>();
    private final AtomicReference<String> ultimoError = new AtomicReference<>();
    private final AtomicLong respuestasAnt = new AtomicLong();
    private final AtomicLong respuestasCache = new AtomicLong();
    private final AtomicLong respuestasRespaldo = new AtomicLong();
    private final AtomicLong fallosAnt = new AtomicLong();

    public void reiniciar() {
        ultimaRespuestaOk.set(null);
        ultimoFallo.set(null);
        ultimoError.set(null);
        respuestasAnt.set(0);
        respuestasCache.set(0);
        respuestasRespaldo.set(0);
        fallosAnt.set(0);
    }

    public void exitoAnt() {
        ultimaRespuestaOk.set(Instant.now());
        respuestasAnt.incrementAndGet();
    }

    public void falloAnt(String error) {
        ultimoFallo.set(Instant.now());
        ultimoError.set(error);
        fallosAnt.incrementAndGet();
    }

    public void desdeCache() {
        respuestasCache.incrementAndGet();
    }

    public void desdeRespaldo() {
        respuestasRespaldo.incrementAndGet();
    }

    public Instant ultimaRespuestaOk() { return ultimaRespuestaOk.get(); }
    public Instant ultimoFallo() { return ultimoFallo.get(); }
    public String ultimoError() { return ultimoError.get(); }
    public long respuestasAnt() { return respuestasAnt.get(); }
    public long respuestasCache() { return respuestasCache.get(); }
    public long respuestasRespaldo() { return respuestasRespaldo.get(); }
    public long fallosAnt() { return fallosAnt.get(); }
}
