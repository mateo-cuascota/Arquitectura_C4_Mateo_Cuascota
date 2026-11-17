package ec.edu.c4.ant.api;

import ec.edu.c4.ant.model.LicenciaResponse;
import ec.edu.c4.ant.service.LicenciaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Pattern;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Licencias", description = "Puntos de licencia con caché en la nube")
@Validated
@RestController
@RequestMapping("/api/ant")
public class LicenciaController {

    private final LicenciaService service;

    public LicenciaController(LicenciaService service) {
        this.service = service;
    }

    /** GET /api/ant/licencias/{cedula}?placa=ABC1234 */
    @Operation(summary = "Puntos de la licencia: desde la caché, en vivo de la ANT o desde la caché de respaldo")
    @GetMapping("/licencias/{cedula}")
    public LicenciaResponse puntos(
            @PathVariable @Pattern(regexp = "\\d{10}", message = "La cédula debe tener 10 dígitos") String cedula,
            @RequestParam(required = false, defaultValue = "")
            @Pattern(regexp = "[A-Za-z0-9-]{0,20}", message = "Placa inválida") String placa) {
        return service.consultar(cedula, placa.trim().toUpperCase().replace("-", ""));
    }
}
