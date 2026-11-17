import { useState } from 'react';
import { consultarPuntosLicencia, obtenerVehiculo, verificarContribuyente } from './api.js';

const ORIGEN_LABEL = {
  ANT: 'En vivo desde la ANT',
  CACHE: 'Desde caché (consulta reciente)',
  CACHE_RESPALDO: 'Desde caché de respaldo (ANT no disponible)',
};

function Campo({ label, value }) {
  if (value === null || value === undefined || value === '') return null;
  return (
    <div className="campo">
      <span>{label}</span>
      <strong>{String(value)}</strong>
    </div>
  );
}

export default function App() {
  const [email, setEmail] = useState('');
  const [ruc, setRuc] = useState('');
  const [placa, setPlaca] = useState('');

  const [persona, setPersona] = useState(null);
  const [vehiculo, setVehiculo] = useState(null);
  const [licencia, setLicencia] = useState(null);

  const [cargando, setCargando] = useState('');
  const [error, setError] = useState('');
  const [errorLicencia, setErrorLicencia] = useState('');

  async function consultarLicencia(cedula, placaVehiculo = '') {
    setErrorLicencia('');
    try {
      setLicencia(await consultarPuntosLicencia(cedula, placaVehiculo));
    } catch (e) {
      setErrorLicencia(e.message);
    }
  }

  async function onVerificar(e) {
    e.preventDefault();
    setError('');
    setPersona(null);
    setVehiculo(null);
    setLicencia(null);
    setErrorLicencia('');
    setCargando('persona');
    try {
      const data = await verificarContribuyente(ruc.trim(), email.trim());
      setPersona(data);
      // Con la cedula (10 primeros digitos del RUC) se consultan los puntos de la licencia en la ANT.
      consultarLicencia(data.cedula);
    } catch (err) {
      setError(err.message);
    } finally {
      setCargando('');
    }
  }

  async function onVehiculo(e) {
    e.preventDefault();
    setError('');
    setVehiculo(null);
    setCargando('vehiculo');
    try {
      const placaNorm = placa.trim().toUpperCase();
      setVehiculo(await obtenerVehiculo(placaNorm));
      consultarLicencia(persona.cedula, placaNorm);
    } catch (err) {
      setError(err.message);
    } finally {
      setCargando('');
    }
  }

  const c = persona?.contribuyente;

  return (
    <main>
      <header>
        <h1>Consulta de contribuyente, vehículo y licencia</h1>
        <p>Servicios del SRI y la ANT · Ecuador</p>
      </header>

      <section className="card">
        <h2>1. Datos del usuario</h2>
        <form onSubmit={onVerificar} className="form">
          <label>
            Correo electrónico
            <input type="email" required value={email} onChange={(e) => setEmail(e.target.value)}
                   placeholder="usuario@correo.com" />
          </label>
          <label>
            RUC (persona natural)
            <input required pattern="\d{10}001" maxLength={13} value={ruc}
                   onChange={(e) => setRuc(e.target.value.replace(/\D/g, ''))}
                   placeholder="1712345678001" title="13 dígitos terminados en 001" />
          </label>
          <button disabled={cargando === 'persona'}>
            {cargando === 'persona' ? 'Verificando…' : 'Verificar en el SRI'}
          </button>
        </form>
      </section>

      {error && <div className="alert error">{error}</div>}

      {persona && (
        <section className="card">
          <h2>2. Contribuyente verificado ✔</h2>
          <div className="grid">
            <Campo label="Nombre / Razón social" value={c.razonSocial} />
            <Campo label="RUC" value={c.numeroRuc} />
            <Campo label="Cédula" value={persona.cedula} />
            <Campo label="Correo" value={persona.email} />
            <Campo label="Tipo" value={c.tipoContribuyente} />
            <Campo label="Estado" value={c.estadoContribuyenteRuc} />
            <Campo label="Régimen" value={c.regimen} />
            <Campo label="Actividad económica" value={c.actividadEconomicaPrincipal} />
            <Campo label="Obligado a llevar contabilidad" value={c.obligadoLlevarContabilidad} />
            <Campo label="Inicio de actividades" value={c.informacionFechasContribuyente?.fechaInicioActividades} />
          </div>
        </section>
      )}

      {persona && (
        <section className="card">
          <h2>3. Puntos de la licencia (ANT)</h2>
          {!licencia && !errorLicencia && <p>Consultando la ANT…</p>}
          {errorLicencia && <div className="alert warn">{errorLicencia}</div>}
          {licencia && (
            <>
              <span className={`badge ${licencia.origen.toLowerCase()}`}>{ORIGEN_LABEL[licencia.origen]}</span>
              {licencia.aviso && <p className="aviso">{licencia.aviso}</p>}
              <div className="puntos">
                <strong>{licencia.licencia.puntosDisponibles ?? '—'}</strong>
                <span>/ 30 puntos</span>
              </div>
              <div className="grid">
                <Campo label="Citaciones" value={licencia.licencia.totalCitaciones} />
                <Campo label="Puntos descontados" value={licencia.licencia.puntosDescontados} />
                <Campo label="Información obtenida" value={new Date(licencia.obtenidoEn).toLocaleString()} />
              </div>
              {licencia.licencia.citaciones.length > 0 && (
                <div className="tabla">
                  <table>
                    <thead>
                      <tr>{Object.keys(licencia.licencia.citaciones[0]).map((k) => <th key={k}>{k}</th>)}</tr>
                    </thead>
                    <tbody>
                      {licencia.licencia.citaciones.map((fila, i) => (
                        <tr key={i}>{Object.values(fila).map((v, j) => <td key={j}>{v}</td>)}</tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}
            </>
          )}
        </section>
      )}

      {persona && (
        <section className="card">
          <h2>4. Matrícula de su vehículo</h2>
          <form onSubmit={onVehiculo} className="form inline">
            <label>
              Placa
              <input required value={placa} maxLength={10} onChange={(e) => setPlaca(e.target.value)}
                     placeholder="PBA1234" />
            </label>
            <button disabled={cargando === 'vehiculo'}>
              {cargando === 'vehiculo' ? 'Consultando…' : 'Consultar vehículo'}
            </button>
          </form>
          {vehiculo && (
            <div className="grid">
              <Campo label="Placa" value={vehiculo.numeroPlaca} />
              <Campo label="Marca" value={vehiculo.descripcionMarca} />
              <Campo label="Modelo" value={vehiculo.descripcionModelo} />
              <Campo label="Año" value={vehiculo.anioAuto} />
              <Campo label="País" value={vehiculo.descripcionPais} />
              <Campo label="Clase" value={vehiculo.nombreClase} />
              <Campo label="Servicio" value={vehiculo.descripcionServicio} />
              <Campo label="Cilindraje" value={vehiculo.cilindraje} />
              <Campo label="Color" value={vehiculo.colorVehiculo1} />
              <Campo label="CAMV / CPN" value={vehiculo.numeroCamvCpn} />
              <Campo label="Último año pagado" value={vehiculo.ultimoAnioPagado} />
              <Campo label="Caducidad matrícula" value={vehiculo.fechaCaducidadMatricula} />
              <Campo label="Cantón" value={vehiculo.descripcionCanton} />
            </div>
          )}
        </section>
      )}
    </main>
  );
}
