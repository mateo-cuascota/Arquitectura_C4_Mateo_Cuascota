// URLs de los microservicios. En desarrollo se usan rutas relativas (proxy de Vite).
const SRI_URL = import.meta.env.VITE_SRI_URL ?? '';
const ANT_URL = import.meta.env.VITE_ANT_URL ?? '';

async function getJson(url, opciones) {
  const res = await fetch(url, opciones);
  const body = await res.json().catch(() => null);
  if (!res.ok) {
    throw new Error(body?.detail ?? `Error ${res.status} al consultar el servicio`);
  }
  return body;
}

/** Acepta cédula (10 dígitos) o RUC de persona natural (13 dígitos). */
export function verificarContribuyente(identificacion, email, consentimiento = false) {
  const q = new URLSearchParams({ email, consentimiento: String(consentimiento) });
  return getJson(`${SRI_URL}/api/sri/contribuyentes/${encodeURIComponent(identificacion)}?${q}`);
}

export function obtenerVehiculo(placa) {
  return getJson(`${SRI_URL}/api/sri/vehiculos/${encodeURIComponent(placa)}`);
}

export function consultarPuntosLicencia(cedula, placa = '') {
  const q = new URLSearchParams({ placa });
  return getJson(`${ANT_URL}/api/ant/licencias/${encodeURIComponent(cedula)}?${q}`);
}

export function estadoSri() {
  return getJson(`${SRI_URL}/api/sri/estado`);
}

export function estadoAnt() {
  return getJson(`${ANT_URL}/api/ant/estado`);
}

export function configurarDemo(activo, antCaida) {
  return getJson(`${ANT_URL}/api/ant/demo`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ activo, antCaida }),
  });
}
