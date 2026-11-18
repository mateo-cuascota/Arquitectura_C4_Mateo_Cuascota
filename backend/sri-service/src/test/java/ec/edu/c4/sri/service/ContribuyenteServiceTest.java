package ec.edu.c4.sri.service;

import ec.edu.c4.sri.client.RegistroCivilClient;
import ec.edu.c4.sri.client.SriClient;
import ec.edu.c4.sri.model.Contribuyente;
import ec.edu.c4.sri.model.PersonaRegistro;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ContribuyenteServiceTest {

    private SriClient sri;
    private RegistroCivilClient registroCivil;
    private ContribuyenteService service;

    @BeforeEach
    void setUp() {
        sri = mock(SriClient.class);
        registroCivil = mock(RegistroCivilClient.class);
        service = new ContribuyenteService(sri, registroCivil);
    }

    private static Contribuyente persona(String ruc, String tipo) {
        return new Contribuyente(ruc, "PEREZ JUAN", "ACTIVO", "SERVICIOS", tipo,
                "RIMPE", null, "NO", "NO", "NO", null, null, "NO", "NO");
    }

    @Test
    void validaElDigitoVerificadorDeLaCedula() {
        assertThat(Identificacion.cedulaValida("1726617747")).isTrue();
        assertThat(Identificacion.cedulaValida("1710034065")).isTrue();
        assertThat(Identificacion.cedulaValida("1726617748")).isFalse();
        assertThat(Identificacion.cedulaValida("9926617747")).isFalse();
    }

    @Test
    void conCedulaYRucRegistradoDevuelveLaInformacion() {
        when(sri.existePorNumeroRuc("1726617747001")).thenReturn(true);
        when(sri.obtenerPorNumeroRuc("1726617747001")).thenReturn(Optional.of(persona("1726617747001", "PERSONA NATURAL")));

        var r = service.verificar("1726617747", "a@b.com");

        assertThat(r.esContribuyente()).isTrue();
        assertThat(r.ruc()).isEqualTo("1726617747001");
        assertThat(r.cedula()).isEqualTo("1726617747");
    }

    @Test
    void conCedulaSinRucContinuaComoNoContribuyente() {
        when(sri.existePorNumeroRuc("1710034065001")).thenReturn(false);
        when(sri.obtenerPersonaPorCedula("1710034065"))
                .thenReturn(Optional.of(new PersonaRegistro("1710034065", "PEREZ LOPEZ ANA", "NAT")));

        var r = service.verificar("1710034065", "a@b.com");
        assertThat(r.persona().nombreCompleto()).isEqualTo("PEREZ LOPEZ ANA");

        assertThat(r.esContribuyente()).isFalse();
        assertThat(r.cedula()).isEqualTo("1710034065");
        assertThat(r.contribuyente()).isNull();
    }

    @Test
    void siElSriNoConoceLaCedulaUsaElRegistroCivilConConsentimiento() {
        when(sri.existePorNumeroRuc("1710034065001")).thenReturn(false);
        when(sri.obtenerPersonaPorCedula("1710034065")).thenReturn(Optional.empty());
        when(registroCivil.buscarPorCedula("1710034065"))
                .thenReturn(Optional.of(new PersonaRegistro("1710034065", "PEREZ LOPEZ ANA", "NAT", RegistroCivilClient.FUENTE)));

        var sinConsentimiento = service.verificar("1710034065", "a@b.com", false);
        var conConsentimiento = service.verificar("1710034065", "a@b.com", true);

        assertThat(sinConsentimiento.persona()).isNull();
        assertThat(conConsentimiento.persona().fuente()).isEqualTo(RegistroCivilClient.FUENTE);
        assertThat(conConsentimiento.persona().nombreCompleto()).isEqualTo("PEREZ LOPEZ ANA");
    }

    @Test
    void conRucNoRegistradoFalla() {
        when(sri.existePorNumeroRuc("1710034065001")).thenReturn(false);

        assertThatThrownBy(() -> service.verificar("1710034065001", null)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void cedulaInvalidaFalla() {
        assertThatThrownBy(() -> service.verificar("1726617748", null)).isInstanceOf(ValidacionException.class);
    }
}
