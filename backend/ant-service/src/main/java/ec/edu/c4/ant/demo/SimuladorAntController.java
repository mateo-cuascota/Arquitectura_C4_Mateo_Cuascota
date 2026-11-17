package ec.edu.c4.ant.demo;

import io.swagger.v3.oas.annotations.Hidden;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Simulador local de clp_grid_citaciones.jsp (solo para el modo demostracion).
 * Responde una grilla jqGrid con citaciones SIMULADAS (deterministas segun la cedula)
 * o un 502 cuando se activa "ANT caida".
 */
@Hidden
@RestController
public class SimuladorAntController {

    private static final String[][] INFRACCIONES = {
            {"Exceso de velocidad (rango moderado)", "6", "230.00"},
            {"Uso de celular mientras conduce", "6", "230.00"},
            {"No respetar señal de PARE", "4.5", "115.00"},
            {"Estacionar en lugar prohibido", "1.5", "46.00"},
            {"No portar licencia de conducir", "3", "92.00"},
    };

    private final ModoDemo modo;

    public SimuladorAntController(ModoDemo modo) {
        this.modo = modo;
    }

    @GetMapping("/simulador-ant/PortalWEB/paginas/clientes/clp_grid_citaciones.jsp")
    public ResponseEntity<?> citaciones(@RequestParam("ps_identificacion") String cedula,
                                        @RequestParam(value = "ps_placa", defaultValue = "") String placa)
            throws InterruptedException {
        Thread.sleep(400); // latencia simulada
        if (modo.antCaida()) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).contentType(MediaType.TEXT_HTML)
                    .body("<html><head><title>502 Bad Gateway</title></head><body><h1>502 Bad Gateway</h1></body></html>");
        }
        int semilla = Math.abs(cedula.hashCode());
        int cantidad = 1 + semilla % 3; // 1 a 3 citaciones
        List<Map<String, String>> filas = new ArrayList<>();
        for (int i = 0; i < cantidad; i++) {
            String[] inf = INFRACCIONES[(semilla / (i + 1)) % INFRACCIONES.length];
            Map<String, String> fila = new LinkedHashMap<>();
            fila.put("Placa", placa.isBlank() ? "SIMULADA" : placa);
            fila.put("Fecha", LocalDate.now().minusDays(30L * (i + 1) + semilla % 25).toString());
            fila.put("Infracción", inf[0]);
            fila.put("Puntos", inf[1]);
            fila.put("Valor", "$ " + inf[2]);
            fila.put("Estado", i == 0 ? "PENDIENTE" : "PAGADA");
            filas.add(fila);
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        for (int i = 0; i < filas.size(); i++) {
            Map<String, Object> r = new LinkedHashMap<>(filas.get(i));
            rows.add(r);
        }
        Map<String, Object> grilla = new LinkedHashMap<>();
        grilla.put("page", "1");
        grilla.put("total", 1);
        grilla.put("records", String.valueOf(rows.size()));
        grilla.put("rows", rows);
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(grilla);
    }
}
