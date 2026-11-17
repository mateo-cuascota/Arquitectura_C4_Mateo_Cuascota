package ec.edu.c4.ant.demo;

import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Modo demostracion: el ANT Service consulta un simulador local de la ANT en lugar del portal real.
 * Sirve para mostrar el patron de cache funcionando aunque el portal real este caido.
 * Los datos simulados se guardan con el prefijo "demo:" para no mezclarlos con la cache real.
 */
@Component
public class ModoDemo {

    private final AtomicBoolean activo = new AtomicBoolean(false);
    private final AtomicBoolean antCaida = new AtomicBoolean(false);

    public boolean activo() {
        return activo.get();
    }

    public boolean antCaida() {
        return antCaida.get();
    }

    public void configurar(boolean activo, boolean antCaida) {
        this.activo.set(activo);
        this.antCaida.set(activo && antCaida);
    }

    /** Prefijo de las claves de Redis segun el modo. */
    public String prefijo() {
        return activo.get() ? "demo:" : "";
    }
}
