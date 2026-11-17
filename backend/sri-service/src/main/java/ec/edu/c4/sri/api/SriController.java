package ec.edu.c4.sri.api;

import ec.edu.c4.sri.model.ContribuyenteResponse;
import ec.edu.c4.sri.model.Vehiculo;
import ec.edu.c4.sri.service.ContribuyenteService;
import ec.edu.c4.sri.service.VehiculoService;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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

    /** GET /api/sri/contribuyentes/{ruc}?email=... */
    @GetMapping("/contribuyentes/{ruc}")
    public ContribuyenteResponse verificarContribuyente(
            @PathVariable
            @Pattern(regexp = "\\d{10}001", message = "El RUC de persona natural debe tener 13 dígitos y terminar en 001")
            String ruc,
            @RequestParam(required = false)
            @Email(message = "Correo electrónico inválido")
            String email) {
        return contribuyentes.verificar(ruc, email);
    }

    /** GET /api/sri/vehiculos/{placa} */
    @GetMapping("/vehiculos/{placa}")
    public Vehiculo obtenerVehiculo(
            @PathVariable
            @Pattern(regexp = "[A-Za-z0-9-]{5,20}", message = "Placa inválida")
            String placa) {
        return vehiculos.obtener(placa);
    }
}
