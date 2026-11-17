package ec.edu.c4.ant.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import ec.edu.c4.ant.cache.CachedLicencia;
import ec.edu.c4.ant.cache.ConsultasPendientesRepository;
import ec.edu.c4.ant.cache.LicenciaCacheRepository;
import ec.edu.c4.ant.client.AntClient;
import ec.edu.c4.ant.client.AntResponseParser;
import ec.edu.c4.ant.client.AntUnavailableException;
import ec.edu.c4.ant.model.LicenciaInfo;
import ec.edu.c4.ant.model.LicenciaResponse.Origen;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Flujo completo del requisito con una ANT simulada por HTTP y un cache en memoria:
 * la ANT responde bien -> se guarda en cache -> la ANT cae -> se devuelve desde el cache.
 */
class FlujoCacheAntTest {

    private static final String CED = "1726617747";
    private static final String URL = "https://consultaweb.ant.gob.ec/PortalWEB/paginas/clientes/clp_grid_citaciones.jsp"
            + "?ps_tipo_identificacion=CED&ps_identificacion=" + CED + "&ps_placa=";
    private static final String GRILLA_JSON = """
            {"page":"1","total":1,"records":"2","rows":[
              {"id":"1","cell":["PBT1774","2026-05-10","EXCESO DE VELOCIDAD","6","$ 230.00"]},
              {"id":"2","cell":["PBT1774","2026-08-21","USO DE CELULAR","3","$ 115.00"]}]}
            """;

    private MockRestServiceServer antSimulada;
    private CacheEnMemoria cache;
    private PendientesEnMemoria pendientes;
    private LicenciaService service;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://consultaweb.ant.gob.ec");
        antSimulada = MockRestServiceServer.bindTo(builder).build();
        cache = new CacheEnMemoria();
        pendientes = new PendientesEnMemoria();
        // fresh-ttl = 0: obliga a llamar a la ANT en cada consulta para probar el respaldo
        service = new LicenciaService(new AntClient(builder.build()), new AntResponseParser(), cache, pendientes, Duration.ZERO);
    }

    @Test
    void guardaCadaRespuestaAdecuadaYLaDevuelveDesdeElCacheCuandoLaAntCae() {
        antSimulada.expect(requestTo(URL)).andRespond(withSuccess(GRILLA_JSON, MediaType.APPLICATION_JSON));
        antSimulada.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.BAD_GATEWAY).body("<h1>502 Bad Gateway</h1>"));

        // 1) La ANT responde: se guarda en cache y se devuelve desde alli
        var vivo = service.consultar(CED, "");
        assertThat(vivo.origen()).isEqualTo(Origen.ANT);
        assertThat(vivo.licencia().totalCitaciones()).isEqualTo(2);
        assertThat(cache.datos).containsKey(LicenciaCacheRepository.key(CED, ""));

        // 2) La ANT cae (502): se devuelve la informacion desde el cache
        var respaldo = service.consultar(CED, "");
        assertThat(respaldo.origen()).isEqualTo(Origen.CACHE_RESPALDO);
        assertThat(respaldo.licencia()).isEqualTo(vivo.licencia());
        assertThat(pendientes.items).isEmpty();
        antSimulada.verify();
    }

    @Test
    void sinCacheLaConsultaQuedaEnColaYElReintentoLaGuardaCuandoLaAntVuelve() {
        antSimulada.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.BAD_GATEWAY));
        antSimulada.expect(requestTo(URL)).andRespond(withSuccess(GRILLA_JSON, MediaType.APPLICATION_JSON));

        // ANT caida y sin copia: 503 y la cedula queda en cola
        assertThatThrownBy(() -> service.consultar(CED, "")).isInstanceOf(AntUnavailableException.class);
        assertThat(pendientes.items).containsExactly(CED + ":-");

        // El job en segundo plano reintenta; la ANT ya responde y se guarda en cache
        new ReintentoAntJob(pendientes, service).reintentarPendientes();
        assertThat(cache.datos).containsKey(LicenciaCacheRepository.key(CED, ""));
        assertThat(pendientes.items).isEmpty();
        antSimulada.verify();
    }

    @Test
    void unaPaginaSinGrillaNoSeConsideraAdecuadaYNoSeGuarda() {
        antSimulada.expect(requestTo(URL)).andRespond(
                withSuccess("<html><body>Sesion expirada</body></html>", MediaType.TEXT_HTML));

        assertThatThrownBy(() -> service.consultar(CED, "")).isInstanceOf(AntUnavailableException.class);
        assertThat(cache.datos).isEmpty();
    }

    /** Cache en memoria que reemplaza a Redis en la prueba (serializa igual que en produccion). */
    static class CacheEnMemoria extends LicenciaCacheRepository {
        final Map<String, String> datos = new HashMap<>();
        private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());

        CacheEnMemoria() {
            super(null, new ObjectMapper(), Duration.ofDays(7));
        }

        @Override
        public void save(String key, LicenciaInfo info) {
            try {
                datos.put(key, mapper.writeValueAsString(new CachedLicencia(info, java.time.Instant.now())));
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }

        @Override
        public Optional<CachedLicencia> find(String key) {
            try {
                String json = datos.get(key);
                return json == null ? Optional.empty() : Optional.of(mapper.readValue(json, CachedLicencia.class));
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }
    }

    static class PendientesEnMemoria extends ConsultasPendientesRepository {
        final Set<String> items = new HashSet<>();

        PendientesEnMemoria() {
            super(null);
        }

        @Override
        public void agregar(String cedula, String placa) {
            items.add(cedula + ":" + (placa.isBlank() ? "-" : placa));
        }

        @Override
        public void quitar(String cedula, String placa) {
            items.remove(cedula + ":" + (placa.isBlank() ? "-" : placa));
        }

        @Override
        public Set<String> listar() {
            return Set.copyOf(items);
        }
    }
}
