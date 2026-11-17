# Modelo C4 – Sistema de Consulta Vehicular

Diagramas en sintaxis Mermaid C4 (se ven directamente en GitHub). El mismo modelo está publicado en IcePanel.

## Nivel 1 – Contexto

```mermaid
C4Context
title Contexto - Sistema de Consulta Vehicular
Person(usuario, "Usuario", "Persona natural que consulta su RUC, su vehículo y los puntos de su licencia")
System(sistema, "Sistema de Consulta Vehicular", "Verifica contribuyentes, vehículos y puntos de licencia")
System_Ext(sri, "SRI - Servicios REST", "Catastro de contribuyentes y base vehicular")
System_Ext(ant, "ANT - Portal Web", "Citaciones y puntos de licencia (baja disponibilidad)")
Rel(usuario, sistema, "Ingresa correo, RUC y placa", "HTTPS")
Rel(sistema, sri, "Consulta contribuyente y vehículo", "HTTPS/JSON")
Rel(sistema, ant, "Consulta citaciones/puntos", "HTTPS/HTML")
```

## Nivel 2 – Contenedores

```mermaid
C4Container
title Contenedores - Sistema de Consulta Vehicular
Person(usuario, "Usuario")
System_Boundary(s, "Sistema de Consulta Vehicular") {
  Container(web, "Web App", "React + Vite", "Formulario de correo/RUC, datos del contribuyente, vehículo y licencia")
  Container(sriSvc, "SRI Service", "Java 21 / Spring Boot", "Verifica contribuyente persona natural y obtiene datos del vehículo")
  Container(antSvc, "ANT Service", "Java 21 / Spring Boot + Resilience4j", "Consulta puntos de licencia con Cache-Aside + Circuit Breaker")
  ContainerDb(cache, "Caché Cloud", "Redis (Redis Cloud / Azure Cache for Redis)", "Últimas respuestas válidas de la ANT")
}
System_Ext(sri, "SRI - Servicios REST")
System_Ext(ant, "ANT - Portal Web")
Rel(usuario, web, "Usa", "HTTPS")
Rel(web, sriSvc, "GET /api/sri/...", "JSON/HTTPS")
Rel(web, antSvc, "GET /api/ant/licencias/{cedula}", "JSON/HTTPS")
Rel(sriSvc, sri, "existePorNumeroRuc, obtenerPorNumerosRuc, BaseVehiculo", "HTTPS/JSON")
Rel(antSvc, ant, "clp_grid_citaciones.jsp", "HTTPS/HTML")
Rel(antSvc, cache, "Lee / guarda respuestas", "RESP/TLS")
```

## Nivel 3 – Componentes del ANT Service

```mermaid
C4Component
title Componentes - ANT Service
Container_Boundary(antSvc, "ANT Service") {
  Component(ctrl, "LicenciaController", "Spring REST", "GET /api/ant/licencias/{cedula}")
  Component(svc, "LicenciaService", "Spring Service", "Orquesta el patrón Cache-Aside + stale-if-error")
  Component(client, "AntClient", "RestClient + Resilience4j", "Retry y Circuit Breaker hacia la ANT")
  Component(parser, "AntResponseParser", "Jsoup", "Convierte la grilla HTML/XML en puntos y citaciones")
  Component(repo, "LicenciaCacheRepository", "Spring Data Redis", "Guarda/lee CachedLicencia con TTL")
  Component(job, "ReintentoAntJob", "@Scheduled", "Reintenta cada 2 min las consultas en cola ant:pendientes")
}
System_Ext(ant, "ANT - Portal Web")
ContainerDb(cache, "Caché Cloud", "Redis")
Rel(ctrl, svc, "consultar(cedula, placa)")
Rel(svc, repo, "find / save")
Rel(svc, client, "consultarCitaciones")
Rel(svc, parser, "parse")
Rel(client, ant, "HTTPS")
Rel(repo, cache, "GET/SET con TTL")
Rel(job, svc, "consultarAntYGuardar")
```

## Nivel 3 – Componentes del SRI Service

```mermaid
C4Component
title Componentes - SRI Service
Container_Boundary(sriSvc, "SRI Service") {
  Component(ctrl, "SriController", "Spring REST", "/api/sri/contribuyentes/{ruc}, /api/sri/vehiculos/{placa}")
  Component(cs, "ContribuyenteService", "Spring Service", "Valida contribuyente y PERSONA NATURAL")
  Component(vs, "VehiculoService", "Spring Service", "Obtiene datos del vehículo por placa")
  Component(client, "SriClient", "Spring RestClient", "Invoca los endpoints REST del SRI")
}
System_Ext(sri, "SRI - Servicios REST")
Rel(ctrl, cs, "verificar")
Rel(ctrl, vs, "obtener")
Rel(cs, client, "existePorNumeroRuc / obtenerPorNumerosRuc")
Rel(vs, client, "obtenerVehiculo")
Rel(client, sri, "HTTPS/JSON")
```

## Patrón de resiliencia para la ANT

**Cache-Aside con respaldo (stale-if-error) + Retry + Circuit Breaker**

1. `LicenciaService` busca primero en Redis. Si la copia tiene menos de `fresh-ttl` (30 min) se devuelve sin tocar la ANT → `origen = CACHE`.
2. Si no hay copia reciente se llama a la ANT (`AntClient`, con Retry ×2 y Circuit Breaker).
3. **Cada respuesta adecuada** (HTTP 2xx, con contenido, sin página de error) se guarda en Redis con TTL de 7 días → `origen = ANT`.
4. Si la ANT falla o el circuito está abierto, se devuelve la última copia válida → `origen = CACHE_RESPALDO`, con aviso al usuario.
5. Si tampoco hay copia en caché, la consulta se encola en `ant:pendientes`, `ReintentoAntJob` la reintenta cada 2 min hasta guardarla y se responde `503`.
