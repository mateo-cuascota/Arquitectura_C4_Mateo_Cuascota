package ec.edu.c4.sri.api;

import ec.edu.c4.sri.model.ContribuyenteResponse;
import ec.edu.c4.sri.model.Vehiculo;
import ec.edu.c4.sri.service.ContribuyenteService;
import ec.edu.c4.sri.service.VehiculoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "SRI", description = "Contribuyentes y vehículos")
@Validated
@RestController
@RequestMapping("/api/sri")
public class SriController {

    private final ContribuyenteService contribuyentes;
    private final VehiculoService vehiculos;

    public SriController(ContribuyenteService contribuyentes, VehiculoService vehiculos) {
        this.contribuyentes = contribuyentes;
        this.vehiculos = vehiculos;
    }

    /** GET /api/sri/contribuyentes/{cedulaORuc}?email=... */
    @Operation(summary = "Acepta cédula o RUC: verifica si es contribuyente PERSONA NATURAL y devuelve su información")
    @GetMapping("/contribuyentes/{identificacion}")
    public ContribuyenteResponse verificarContribuyente(
            @PathVariable
            @Pattern(regexp = "\\d{10}|\\d{10}001", message = "Ingresa una cédula de 10 dígitos o un RUC de 13 dígitos terminado en 001")
            String identificacion,
            @RequestParam(required = false)
            @Email(message = "Correo electrónico inválido")
            String email) {
        return contribuyentes.verificar(identificacion, email);
    }

    /** GET /api/sri/vehiculos/{placa} */
    @Operation(summary = "Obtiene la información del vehículo por placa, CAMV o CPN")
    @GetMapping("/vehiculos/{placa}")
    public Vehiculo obtenerVehiculo(
            @PathVariable
            @Pattern(regexp = "[A-Za-z0-9-]{5,20}", message = "Placa inválida")
            String placa) {
        return vehiculos.obtener(placa);
    }
}
