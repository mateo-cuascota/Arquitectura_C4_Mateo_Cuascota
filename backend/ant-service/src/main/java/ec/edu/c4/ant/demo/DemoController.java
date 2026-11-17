package ec.edu.c4.ant.demo;

import ec.edu.c4.ant.service.AntMonitor;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Activa/desactiva el modo demostracion y la caida simulada de la ANT. */
@Tag(name = "Demostración", description = "Simulador de la ANT para demostrar el patrón de caché")
@RestController
@RequestMapping("/api/ant/demo")
public class DemoController {

    private final ModoDemo modo;
    private final CircuitBreakerRegistry circuitos;
    private final AntMonitor monitor;

    public DemoController(ModoDemo modo, CircuitBreakerRegistry circuitos, AntMonitor monitor) {
        this.modo = modo;
        this.circuitos = circuitos;
        this.monitor = monitor;
    }

    public record Configuracion(boolean activo, boolean antCaida) { }

    @Operation(summary = "Estado del modo demostración")
    @GetMapping
    public Configuracion obtener() {
        return new Configuracion(modo.activo(), modo.antCaida());
    }

    @Operation(summary = "Activa el simulador de la ANT y/o simula su caída")
    @PostMapping
    public Configuracion configurar(@RequestBody Configuracion c) {
        boolean cambioDeModo = c.activo() != modo.activo();
        modo.configurar(c.activo(), c.antCaida());
        if (cambioDeModo) {
            // Cada modo empieza con el circuito cerrado y metricas propias.
            circuitos.circuitBreaker("ant").reset();
            monitor.reiniciar();
        }
        return obtener();
    }
}
