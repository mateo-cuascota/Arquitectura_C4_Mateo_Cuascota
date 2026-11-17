package ec.edu.c4.sri.service;

import ec.edu.c4.sri.client.SriClient;
import ec.edu.c4.sri.model.Vehiculo;
import org.springframework.stereotype.Service;

@Service
public class VehiculoService {

    private final SriClient sri;

    public VehiculoService(SriClient sri) {
        this.sri = sri;
    }

    public Vehiculo obtener(String placa) {
        String normalizada = placa.trim().toUpperCase().replace("-", "");
        return sri.obtenerVehiculo(normalizada)
                .orElseThrow(() -> new NotFoundException("No se encontro un vehiculo con placa " + normalizada));
    }
}
