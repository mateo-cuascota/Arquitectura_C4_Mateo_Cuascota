package ec.edu.c4.sri.api;

import ec.edu.c4.sri.client.SriClient;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.Instant;

/** Indica si los servicios REST del SRI responden (resultado guardado 60 s para no saturar al SRI). */
@Tag(name = "Estado", description = "Disponibilidad de los servicios del SRI")
@RestController
@RequestMapping("/api/sri")
public class EstadoController {

    private static final String RUC_PRUEBA = "1790016919001";
    private static final Duration VIGENCIA = Duration.ofSeconds(60);

    private final SriClient sri;
    private volatile Estado ultimo;

    public EstadoController(SriClient sri) {
        this.sri = sri;
    }

    public record Estado(boolean sriDisponible, long latenciaMs, Instant verificadoEn) { }

    @Operation(summary = "Verifica si el SRI está respondiendo")
    @GetMapping("/estado")
    public Estado estado() {
        Estado e = ultimo;
        if (e != null && e.verificadoEn().isAfter(Instant.now().minus(VIGENCIA))) {
            return e;
        }
        long inicio = System.currentTimeMillis();
        boolean ok;
        try {
            ok = sri.ping(RUC_PRUEBA);
        } catch (RuntimeException ex) {
            ok = false;
        }
        ultimo = new Estado(ok, System.currentTimeMillis() - inicio, Instant.now());
        return ultimo;
    }
}
