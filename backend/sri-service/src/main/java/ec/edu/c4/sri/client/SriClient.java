package ec.edu.c4.sri.client;

import ec.edu.c4.sri.model.Contribuyente;
import ec.edu.c4.sri.model.PersonaRegistro;
import ec.edu.c4.sri.model.Vehiculo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.List;
import java.util.Optional;

/**
 * Cliente REST hacia los servicios publicos del SRI.
 * Las respuestas se guardan en la cache en la nube (Redis/Upstash) con {@link Cacheable}
 * para no repetir llamadas al SRI (ver {@code CacheConfig}).
 */
@Component
public class SriClient {

    private static final Logger log = LoggerFactory.getLogger(SriClient.class);

    private static final String CATASTRO = "/sri-catastro-sujeto-servicio-internet/rest/ConsolidadoContribuyente";
    private static final String PERSONA = "/sri-catastro-sujeto-servicio-internet/rest/Persona";
    private static final String VEHICULOS = "/sri-matriculacion-vehicular-recaudacion-servicio-internet/rest/BaseVehiculo";

    private final RestClient rest;

    public SriClient(RestClient sriRestClient) {
        this.rest = sriRestClient;
    }

    /** existePorNumeroRuc?numeroRuc= */
    @Cacheable(cacheNames = "sri-existe-ruc", key = "#ruc")
    public boolean existePorNumeroRuc(String ruc) {
        return consultarExiste(ruc);
    }

    /** Igual que {@link #existePorNumeroRuc} pero sin cache: lo usa el endpoint de estado para medir al SRI real. */
    public boolean ping(String ruc) {
        return consultarExiste(ruc);
    }

    private boolean consultarExiste(String ruc) {
        Boolean existe = rest.get()
                .uri(CATASTRO + "/existePorNumeroRuc?numeroRuc={ruc}", ruc)
                .retrieve()
                .body(Boolean.class);
        return Boolean.TRUE.equals(existe);
    }

    /** obtenerPorNumerosRuc?&ruc= */
    @Cacheable(cacheNames = "sri-contribuyente", key = "#ruc", unless = "#result == null")
    public Optional<Contribuyente> obtenerPorNumeroRuc(String ruc) {
        List<Contribuyente> lista = rest.get()
                .uri(CATASTRO + "/obtenerPorNumerosRuc?&ruc={ruc}", ruc)
                .retrieve()
                .body(new ParameterizedTypeReference<List<Contribuyente>>() { });
        return lista == null || lista.isEmpty() ? Optional.empty() : Optional.of(lista.get(0));
    }

    /**
     * Persona/obtenerPorTipoIdentificacion?numeroIdentificacion=&tipoIdentificacion=C
     * Devuelve el nombre completo por cedula aunque la persona no tenga RUC.
     */
    @Cacheable(cacheNames = "sri-persona", key = "#cedula", unless = "#result == null")
    public Optional<PersonaRegistro> obtenerPersonaPorCedula(String cedula) {
        try {
            PersonaRegistro p = rest.get()
                    .uri(PERSONA + "/obtenerPorTipoIdentificacion?numeroIdentificacion={c}&tipoIdentificacion=C", cedula)
                    .retrieve()
                    .body(PersonaRegistro.class);
            return Optional.ofNullable(p).filter(x -> x.nombreCompleto() != null && !x.nombreCompleto().isBlank());
        } catch (RestClientResponseException e) {
            log.info("El SRI no devolvio datos de la cedula {}: HTTP {}", cedula, e.getStatusCode().value());
            return Optional.empty();
        }
    }

    /** obtenerPorNumeroPlacaOPorNumeroCampvOPorNumeroCpn?numeroPlacaCampvCpn= */
    @Cacheable(cacheNames = "sri-vehiculo", key = "#placa", unless = "#result == null")
    public Optional<Vehiculo> obtenerVehiculo(String placa) {
        Vehiculo v = rest.get()
                .uri(VEHICULOS + "/obtenerPorNumeroPlacaOPorNumeroCampvOPorNumeroCpn?numeroPlacaCampvCpn={placa}", placa)
                .retrieve()
                .body(Vehiculo.class);
        return Optional.ofNullable(v).filter(x -> x.numeroPlaca() != null || x.codigoVehiculo() != null);
    }
}
