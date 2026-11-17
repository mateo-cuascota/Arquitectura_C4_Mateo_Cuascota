package ec.edu.c4.ant.service;

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

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LicenciaServiceTest {

    private static final String CED = "1712345678";
    private static final String KEY = LicenciaCacheRepository.key(CED, "");

    private AntClient ant;
    private LicenciaCacheRepository cache;
    private ConsultasPendientesRepository pendientes;
    private LicenciaService service;

    @BeforeEach
    void setUp() {
        ant = mock(AntClient.class);
        cache = mock(LicenciaCacheRepository.class);
        pendientes = mock(ConsultasPendientesRepository.class);
        service = new LicenciaService(ant, new AntResponseParser(), cache, pendientes, Duration.ofMinutes(30));
    }

    @Test
    void guardaEnCacheCuandoLaAntRespondeBien() {
        when(cache.find(KEY)).thenReturn(Optional.empty());
        when(ant.consultarCitaciones(CED, "")).thenReturn(
                "<table><tr><th>Placa</th><th>Puntos</th></tr><tr><td>PBA1234</td><td>6</td></tr></table>");

        var r = service.consultar(CED, "");

        assertThat(r.origen()).isEqualTo(Origen.ANT);
        assertThat(r.licencia().puntosDescontados()).isEqualTo(6.0);
        assertThat(r.licencia().puntosDisponibles()).isEqualTo(24);
        verify(cache).save(eq(KEY), any(LicenciaInfo.class));
    }

    @Test
    void devuelveCacheRecienteSinLlamarALaAnt() {
        var info = new LicenciaInfo(CED, "", 30, 0.0, 0, List.of());
        when(cache.find(KEY)).thenReturn(Optional.of(new CachedLicencia(info, Instant.now())));

        var r = service.consultar(CED, "");

        assertThat(r.origen()).isEqualTo(Origen.CACHE);
        verify(ant, never()).consultarCitaciones(anyString(), anyString());
    }

    @Test
    void devuelveCacheDeRespaldoSiLaAntEstaCaida() {
        var info = new LicenciaInfo(CED, "", 20, 10.0, 2, List.of());
        when(cache.find(KEY)).thenReturn(Optional.of(new CachedLicencia(info, Instant.now().minus(Duration.ofDays(2)))));
        when(ant.consultarCitaciones(CED, "")).thenThrow(new AntUnavailableException("502 Bad Gateway"));

        var r = service.consultar(CED, "");

        assertThat(r.origen()).isEqualTo(Origen.CACHE_RESPALDO);
        assertThat(r.licencia().puntosDisponibles()).isEqualTo(20);
    }

    @Test
    void falla503SiLaAntEstaCaidaYNoHayCache() {
        when(cache.find(KEY)).thenReturn(Optional.empty());
        when(ant.consultarCitaciones(CED, "")).thenThrow(new AntUnavailableException("timeout"));

        assertThatThrownBy(() -> service.consultar(CED, "")).isInstanceOf(AntUnavailableException.class);
        verify(pendientes).agregar(CED, "");
    }
}
