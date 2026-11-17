package ec.edu.c4.sri.service;

import ec.edu.c4.sri.client.SriClient;
import ec.edu.c4.sri.model.Contribuyente;
import ec.edu.c4.sri.model.ContribuyenteResponse;
import ec.edu.c4.sri.model.PersonaRegistro;
import org.springframework.stereotype.Service;

@Service
public class ContribuyenteService {

    private final SriClient sri;

    public ContribuyenteService(SriClient sri) {
        this.sri = sri;
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
    public ContribuyenteResponse verificar(String identificacion, String email) {
        Identificacion id = Identificacion.de(identificacion);
        // Nombre completo por cedula (funciona aunque no tenga RUC).
        PersonaRegistro persona = buscarPersona(id.cedula());

        if (!sri.existePorNumeroRuc(id.ruc())) {
            if (id.ingresoRuc()) {
                throw new NotFoundException("El RUC " + id.ruc() + " no está registrado como contribuyente en el SRI");
            }
            return new ContribuyenteResponse(null, id.cedula(), email, false, persona == null || persona.esPersonaNatural(), persona, null);
        }
        Contribuyente c = sri.obtenerPorNumeroRuc(id.ruc())
                .orElseThrow(() -> new NotFoundException("No se encontró información del RUC " + id.ruc()));
        if (!c.esPersonaNatural()) {
            throw new BusinessRuleException("El RUC " + id.ruc() + " corresponde a " + c.tipoContribuyente()
                    + "; se requiere una PERSONA NATURAL");
        }
        return new ContribuyenteResponse(id.ruc(), id.cedula(), email, true, true, persona, c);
    }

    /** El nombre por cedula es informativo: si el SRI no responde, la verificacion continua sin el. */
    private PersonaRegistro buscarPersona(String cedula) {
        try {
            return sri.obtenerPersonaPorCedula(cedula).orElse(null);
        } catch (RuntimeException e) {
            return null;
        }
    }
}
