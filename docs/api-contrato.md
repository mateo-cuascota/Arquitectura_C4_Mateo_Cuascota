# Contrato de la API

Dos microservicios REST (Java 21 / Spring Boot). Documentación interactiva (OpenAPI/Swagger):

- SRI Service → http://localhost:8081/swagger-ui.html
- ANT Service → http://localhost:8082/swagger-ui.html

Todas las respuestas son JSON (`application/json`). Los errores usan **RFC 9457 Problem Details** con campos propios.

## Formato de error (igual en ambos servicios)

```json
{
  "type": "about:blank",
  "title": "Bad Request",
  "status": 400,
  "detail": "La cédula 1726617748 no es válida (dígito verificador incorrecto)",
  "instance": "/api/sri/contribuyentes/1726617748",
  "codigo": "CEDULA_INVALIDA",
  "mensaje": "La cédula 1726617748 no es válida (dígito verificador incorrecto)",
  "timestamp": "2026-11-17T23:10:00Z"
}
```

| Campo | Significado |
|---|---|
| `status` | Código HTTP |
| `codigo` | Código estable para que el front end decida qué hacer (ver tablas) |
| `mensaje` / `detail` | Mensaje en español para mostrar al usuario |
| `instance` | Ruta que produjo el error |
| `timestamp` | Momento del error (UTC) |

---

## SRI Service (puerto 8081)

### `GET /api/sri/contribuyentes/{identificacion}`

Verifica si la persona es contribuyente del SRI y persona natural.

| Parámetro | Dónde | Obligatorio | Regla |
|---|---|---|---|
| `identificacion` | ruta | sí | Cédula (10 dígitos) o RUC (13 dígitos terminado en `001`); se valida el dígito verificador |
| `email` | query | sí | Correo electrónico válido |
| `consentimiento` | query | no (`false`) | `true` permite consultar el Registro Civil (Didit) si el SRI no conoce la cédula |

Flujo interno: `existePorNumeroRuc` → `obtenerPorNumerosRuc` → validación `PERSONA NATURAL`. El nombre por cédula sale de `Persona/obtenerPorTipoIdentificacion`.

**200 OK**
```json
{
  "ruc": "1726617747001",
  "cedula": "1726617747",
  "email": "usuario@correo.com",
  "esContribuyente": true,
  "esPersonaNatural": true,
  "persona": { "identificacion": "1726617747", "nombreCompleto": "APELLIDOS NOMBRES", "tipoPersona": "NAT", "fuente": "SRI" },
  "contribuyente": { "numeroRuc": "1726617747001", "razonSocial": "APELLIDOS NOMBRES", "tipoContribuyente": "PERSONA NATURAL", "estadoContribuyenteRuc": "ACTIVO", "regimen": "RIMPE", "...": "..." }
}
```
Con solo la cédula y sin RUC: `200` con `esContribuyente: false`, `ruc: null` y `contribuyente: null`; el flujo continúa.

| HTTP | `codigo` | Cuándo |
|---|---|---|
| 400 | `CORREO_REQUERIDO` | Falta el correo |
| 400 | `CORREO_INVALIDO` | El correo no tiene formato válido |
| 400 | `IDENTIFICACION_INVALIDA` | No son 10 o 13 dígitos (13 deben terminar en 001) |
| 400 | `CEDULA_INVALIDA` | Dígito verificador, provincia o tercer dígito incorrectos |
| 404 | `RUC_NO_REGISTRADO` | Se ingresó un RUC que no existe en el SRI |
| 404 | `CONTRIBUYENTE_SIN_DATOS` | El SRI dice que existe pero no devuelve datos |
| 422 | `NO_ES_PERSONA_NATURAL` | El RUC es de una sociedad u otro tipo |
| 502 | `SRI_NO_DISPONIBLE` | El SRI no respondió |

### `GET /api/sri/vehiculos/{placa}`

Datos del vehículo (`BaseVehiculo/obtenerPorNumeroPlacaOPorNumeroCampvOPorNumeroCpn`).

| Parámetro | Regla |
|---|---|
| `placa` | 5 a 20 caracteres alfanuméricos (se aceptan guiones, se normaliza a mayúsculas) |

**200 OK** → `{ "numeroPlaca": "PBA1234", "descripcionMarca": "FORD", "descripcionModelo": "...", "anioAuto": 2006, "ultimoAnioPagado": 2026, ... }`

| HTTP | `codigo` | Cuándo |
|---|---|---|
| 400 | `PLACA_INVALIDA` | Formato incorrecto |
| 404 | `VEHICULO_NO_ENCONTRADO` | El SRI no tiene esa placa |
| 502 | `SRI_NO_DISPONIBLE` | El SRI no respondió |

### `GET /api/sri/estado`
`200` → `{ "sriDisponible": true, "latenciaMs": 345, "verificadoEn": "..." }` (se mide cada 60 s como máximo).

---

## ANT Service (puerto 8082)

### `GET /api/ant/licencias/{cedula}?placa=`

Puntos de la licencia con el patrón **Cache-Aside + stale-if-error + Retry + Circuit Breaker** (Redis en Upstash).

| Parámetro | Regla |
|---|---|
| `cedula` | 10 dígitos |
| `placa` | opcional, alfanumérica |

**200 OK**
```json
{
  "licencia": {
    "cedula": "1726617747", "placa": "", "puntosDisponibles": 21, "puntosDescontados": 9.0, "totalCitaciones": 3,
    "citaciones": [ { "Placa": "...", "Fecha": "2026-09-25", "Infracción": "...", "Puntos": "1.5", "Valor": "$ 46.00", "Estado": "PENDIENTE" } ]
  },
  "origen": "ANT | CACHE | CACHE_RESPALDO",
  "obtenidoEn": "2026-11-17T23:15:04Z",
  "antiguedadSegundos": 125,
  "aviso": "La ANT no está disponible; se muestra la última información válida almacenada",
  "simulado": false
}
```

| `origen` | Significado |
|---|---|
| `ANT` | La ANT respondió; el dato se guardó en Redis y se devolvió desde allí |
| `CACHE` | Copia de menos de 30 min: no se llamó a la ANT |
| `CACHE_RESPALDO` | La ANT falló: se devuelve la última copia válida (hasta 7 días). **No es un dato reciente**; `obtenidoEn` y `antiguedadSegundos` dicen de cuándo es |

| HTTP | `codigo` | Cuándo |
|---|---|---|
| 400 | `CEDULA_INVALIDA` | La cédula no tiene 10 dígitos |
| 400 | `PLACA_INVALIDA` | Placa con caracteres no permitidos |
| 503 | `ANT_NO_DISPONIBLE` | La ANT falló y no hay copia en caché. Incluye cabecera `Retry-After: 60` y `reintentarEnSegundos`; la consulta queda en cola y se reintenta cada 2 min |

### `GET /api/ant/estado`
Estado del circuit breaker, conexión a Redis, consultas en caché/cola, métricas y políticas de TTL.

### `GET /api/ant/demo` · `POST /api/ant/demo`
Modo demostración (simulador de la ANT). Cuerpo: `{ "activo": true, "antCaida": false }`. Cuerpo inválido → `400 CUERPO_INVALIDO`.

---

## Errores comunes a ambos servicios

| HTTP | `codigo` | Cuándo |
|---|---|---|
| 400 | `PARAMETRO_FALTANTE` / `PARAMETRO_INVALIDO` | Falta un parámetro o tiene un tipo incorrecto |
| 404 | `RUTA_NO_ENCONTRADA` | La ruta no existe |
| 405 | `METODO_NO_PERMITIDO` | Verbo HTTP incorrecto (por ejemplo `POST` en una consulta) |
| 500 | `ERROR_INTERNO` | Error no previsto (se registra en el log, no se exponen detalles) |

Verbos: todas las consultas son `GET` (seguras e idempotentes); solo el cambio de modo demo es `POST`.

Las pruebas `SriContratoApiTest` y `AntContratoApiTest` verifican este contrato en cada build (GitHub Actions).
