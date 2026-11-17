// URLs de los microservicios. En desarrollo se usan rutas relativas (proxy de Vite).
const SRI_URL = import.meta.env.VITE_SRI_URL ?? '';
const ANT_URL = import.meta.env.VITE_ANT_URL ?? '';

async function getJson(url) {
  const res = await fetch(url);
  const body = await res.json().catch(() => null);
  if (!res.ok) {
    throw new Error(body?.detail ?? `Error ${res.status} al consultar el servicio`);
  }
  return body;
}

export function verificarContribuyente(ruc, email) {
  const q = new URLSearchParams({ email });
  return getJson(`${SRI_URL}/api/sri/contribuyentes/${encodeURIComponent(ruc)}?${q}`);
}

export function obtenerVehiculo(placa) {
  return getJson(`${SRI_URL}/api/sri/vehiculos/${encodeURIComponent(placa)}`);
}

export function consultarPuntosLicencia(cedula, placa = '') {
  const q = new URLSearchParams({ placa });
  return getJson(`${ANT_URL}/api/ant/licencias/${encodeURIComponent(cedula)}?${q}`);
}
