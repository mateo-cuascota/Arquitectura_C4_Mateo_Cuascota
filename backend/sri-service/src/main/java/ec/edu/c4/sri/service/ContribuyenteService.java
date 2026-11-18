package ec.edu.c4.sri.service;

import ec.edu.c4.sri.client.RegistroCivilClient;
import ec.edu.c4.sri.client.SriClient;
import ec.edu.c4.sri.model.Contribuyente;
import ec.edu.c4.sri.model.ContribuyenteResponse;
import ec.edu.c4.sri.model.PersonaRegistro;
import org.springframework.stereotype.Service;

@Service
public class ContribuyenteService {

    private final SriClient sri;
    private final RegistroCivilClient registroCivil;

    public ContribuyenteService(SriClient sri, RegistroCivilClient registroCivil) {
        this.sri = sri;
        this.registroCivil = registroCivil;
    }

    public ContribuyenteResponse verificar(String identificacion, String email) {
        return verificar(identificacion, email, false);
    }

    /**
     * Acepta cedula o RUC de persona natural.
     * <ol>
     *   <li>Verifica con existePorNumeroRuc si es contribuyente del SRI (con la cedula se usa cedula + 001).</li>
     *   <li>Si lo es, obtiene su informacion (obtenerPorNumerosRuc) y valida que sea PERSONA NATURAL.</li>
     *   <li>Si se ingreso solo la cedula y no tiene RUC, se informa que no es contribuyente y el flujo
     *       continua (la consulta de licencia y vehiculo solo necesita la cedula).</li>
     * </ol>
     */
    public ContribuyenteResponse verificar(String identificacion, String email, boolean consentimiento) {
        Identificacion id = Identificacion.de(identificacion);
        // Nombre completo por cedula (funciona aunque no tenga RUC).
        PersonaRegistro persona = buscarPersona(id.cedula(), consentimiento);

        if (!sri.existePorNumeroRuc(id.ruc())) {
            if (id.ingresoRuc()) {
                throw new NotFoundException("RUC_NO_REGISTRADO", "El RUC " + id.ruc() + " no está registrado como contribuyente en el SRI");
            }
            return new ContribuyenteResponse(null, id.cedula(), email, false, persona == null || persona.esPersonaNatural(), persona, null);
        }
        Contribuyente c = sri.obtenerPorNumeroRuc(id.ruc())
                .orElseThrow(() -> new NotFoundException("CONTRIBUYENTE_SIN_DATOS", "No se encontró información del RUC " + id.ruc()));
        if (!c.esPersonaNatural()) {
            throw new BusinessRuleException("NO_ES_PERSONA_NATURAL", "El RUC " + id.ruc() + " corresponde a " + c.tipoContribuyente()
                    + "; se requiere una PERSONA NATURAL");
        }
        return new ContribuyenteResponse(id.ruc(), id.cedula(), email, true, true, persona, c);
    }

    /**
     * Nombre por cedula: primero el SRI; si el SRI no conoce a la persona (sin RUC) y el titular dio su
     * consentimiento, el Registro Civil a traves de Didit (solo si DIDIT_API_KEY esta configurada).
     * Es informativo: si las fuentes fallan, la verificacion continua sin el nombre.
     */
    private PersonaRegistro buscarPersona(String cedula, boolean consentimiento) {
        try {
            var sriPersona = sri.obtenerPersonaPorCedula(cedula);
            if (sriPersona.isPresent()) {
                return sriPersona.get().conFuente("SRI");
            }
        } catch (RuntimeException ignorado) {
            // se intenta la siguiente fuente
        }
        if (!consentimiento) {
            return null;
        }
        try {
            return registroCivil.buscarPorCedula(cedula).orElse(null);
        } catch (RuntimeException e) {
            return null;
        }
    }
}
