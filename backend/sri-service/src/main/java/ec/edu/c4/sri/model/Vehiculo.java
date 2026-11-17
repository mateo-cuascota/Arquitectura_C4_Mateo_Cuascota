package ec.edu.c4.sri.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Respuesta de BaseVehiculo/obtenerPorNumeroPlacaOPorNumeroCampvOPorNumeroCpn del SRI. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record Vehiculo(
        Long codigoVehiculo,
        String numeroPlaca,
        String numeroCamvCpn,
        String colorVehiculo1,
        String colorVehiculo2,
        String cilindraje,
        String nombreClase,
        String descripcionMarca,
        String descripcionModelo,
        Integer anioAuto,
        String descripcionPais,
        String fechaUltimaMatricula,
        String fechaCaducidadMatricula,
        String fechaCompraRegistro,
        String descripcionCanton,
        String descripcionServicio,
        Integer ultimoAnioPagado,
        String prohibidoEnajenar,
        String observacion,
        String estadoExoneracion
) { }
