package ec.edu.c4.sri.client;

import ec.edu.c4.sri.model.Contribuyente;
import ec.edu.c4.sri.model.Vehiculo;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Optional;

/** Cliente REST hacia los servicios publicos del SRI. */
@Component
public class SriClient {

    private static final String CATASTRO = "/sri-catastro-sujeto-servicio-internet/rest/ConsolidadoContribuyente";
    private static final String VEHICULOS = "/sri-matriculacion-vehicular-recaudacion-servicio-internet/rest/BaseVehiculo";

    private final RestClient rest;

    public SriClient(RestClient sriRestClient) {
        this.rest = sriRestClient;
    }

    /** existePorNumeroRuc?numeroRuc= */
    public boolean existePorNumeroRuc(String ruc) {
        Boolean existe = rest.get()
                .uri(CATASTRO + "/existePorNumeroRuc?numeroRuc={ruc}", ruc)
                .retrieve()
                .body(Boolean.class);
        return Boolean.TRUE.equals(existe);
    }

    /** obtenerPorNumerosRuc?&ruc= */
    public Optional<Contribuyente> obtenerPorNumeroRuc(String ruc) {
        List<Contribuyente> lista = rest.get()
                .uri(CATASTRO + "/obtenerPorNumerosRuc?&ruc={ruc}", ruc)
                .retrieve()
                .body(new ParameterizedTypeReference<List<Contribuyente>>() { });
        return lista == null || lista.isEmpty() ? Optional.empty() : Optional.of(lista.get(0));
    }

    /** obtenerPorNumeroPlacaOPorNumeroCampvOPorNumeroCpn?numeroPlacaCampvCpn= */
    public Optional<Vehiculo> obtenerVehiculo(String placa) {
        Vehiculo v = rest.get()
                .uri(VEHICULOS + "/obtenerPorNumeroPlacaOPorNumeroCampvOPorNumeroCpn?numeroPlacaCampvCpn={placa}", placa)
                .retrieve()
                .body(Vehiculo.class);
        return Optional.ofNullable(v).filter(x -> x.numeroPlaca() != null || x.codigoVehiculo() != null);
    }
}
