package ec.edu.c4.sri.service;

import ec.edu.c4.sri.client.SriClient;
import ec.edu.c4.sri.model.Contribuyente;
import ec.edu.c4.sri.model.ContribuyenteResponse;
import org.springframework.stereotype.Service;

@Service
public class ContribuyenteService {

    private final SriClient sri;

    public ContribuyenteService(SriClient sri) {
        this.sri = sri;
    }

    /**
     * 1) Verifica que el RUC sea contribuyente del SRI (existePorNumeroRuc).
     * 2) Obtiene su informacion (obtenerPorNumerosRuc) y valida que sea PERSONA NATURAL.
     */
    public ContribuyenteResponse verificar(String ruc, String email) {
        if (!sri.existePorNumeroRuc(ruc)) {
            throw new NotFoundException("El RUC " + ruc + " no está registrado como contribuyente en el SRI");
        }
        Contribuyente c = sri.obtenerPorNumeroRuc(ruc)
                .orElseThrow(() -> new NotFoundException("No se encontró información del RUC " + ruc));
        if (!c.esPersonaNatural()) {
            throw new BusinessRuleException("El RUC " + ruc + " corresponde a " + c.tipoContribuyente()
                    + "; se requiere una PERSONA NATURAL");
        }
        String cedula = ruc.substring(0, 10);
        return new ContribuyenteResponse(ruc, cedula, email, true, true, c);
    }
}
