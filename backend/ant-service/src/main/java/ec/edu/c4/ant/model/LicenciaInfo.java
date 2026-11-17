package ec.edu.c4.ant.model;

import java.util.List;
import java.util.Map;

/**
 * Informacion de la licencia obtenida de la ANT.
 *
 * @param puntosDisponibles  puntos que conserva la licencia (maximo 30) o null si no se pudo determinar
 * @param puntosDescontados  suma de puntos perdidos por citaciones (si la ANT los reporta)
 * @param citaciones         filas de la grilla de citaciones (columna -> valor)
 */
public record LicenciaInfo(
        String cedula,
        String placa,
        Integer puntosDisponibles,
        Double puntosDescontados,
        int totalCitaciones,
        List<Map<String, String>> citaciones
) {
    public static final int PUNTOS_MAXIMOS = 30;
}
