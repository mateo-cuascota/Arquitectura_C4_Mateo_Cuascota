package ec.edu.c4.ant.cache;

import ec.edu.c4.ant.model.LicenciaInfo;

import java.time.Instant;

/** Sobre que se guarda en Redis: el dato y cuando se obtuvo de la ANT. */
public record CachedLicencia(LicenciaInfo data, Instant cachedAt) { }
