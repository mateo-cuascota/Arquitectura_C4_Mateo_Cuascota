package ec.edu.c4.ant.service;

import ec.edu.c4.ant.cache.ConsultasPendientesRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Reintento en segundo plano: cada {@code ant.reintento.intervalo} recorre las consultas que fallaron
 * sin copia en cache y vuelve a invocar a la ANT. Cuando la ANT responde, la informacion queda en el
 * cache y el usuario la obtiene desde alli en su proxima consulta (aunque la ANT vuelva a caerse).
 */
@Component
public class ReintentoAntJob {

    private static final Logger log = LoggerFactory.getLogger(ReintentoAntJob.class);

    private final ConsultasPendientesRepository pendientes;
    private final LicenciaService service;

    public ReintentoAntJob(ConsultasPendientesRepository pendientes, LicenciaService service) {
        this.pendientes = pendientes;
        this.service = service;
    }

    @Scheduled(fixedDelayString = "${ant.reintento.intervalo:PT2M}", initialDelayString = "${ant.reintento.intervalo:PT2M}")
    public void reintentarPendientes() {
        if (service.modoDemoActivo()) {
            return; // la cola real solo se reintenta contra el portal real
        }
        Set<String> lista = pendientes.listar();
        if (lista.isEmpty()) {
            return;
        }
        log.info("Reintentando {} consulta(s) pendiente(s) a la ANT", lista.size());
        for (String item : lista) {
            String[] partes = item.split(":", 2);
            String cedula = partes[0];
            String placa = partes.length > 1 && !"-".equals(partes[1]) ? partes[1] : "";
            try {
                service.consultarAntYGuardar(cedula, placa);
                log.info("ANT respondio para {}: informacion guardada en cache", item);
            } catch (Exception e) {
                log.info("La ANT sigue sin responder ({}); se reintentara luego", e.getMessage());
                return; // si la ANT esta caida no tiene sentido seguir con el resto en esta ronda
            }
        }
    }
}
