import { useState } from 'react';
import { consultarPuntosLicencia, obtenerVehiculo, verificarContribuyente } from './api.js';

const ORIGEN = {
  ANT: { label: 'En vivo desde la ANT', icon: '⚡', clase: 'ok' },
  CACHE: { label: 'Desde caché (consulta reciente)', icon: '🗄️', clase: 'info' },
  CACHE_RESPALDO: { label: 'Caché de respaldo · ANT no disponible', icon: '🛟', clase: 'warn' },
};

const PASOS = ['Usuario', 'Contribuyente', 'Licencia', 'Vehículo'];

function formatearFecha(valor) {
  if (!valor) return null;
  const m = String(valor).match(/^(\d{4})-(\d{2})-(\d{2})/);
  return m ? `${m[3]}/${m[2]}/${m[1]}` : valor;
}

function iniciales(nombre = '') {
  const p = nombre.trim().split(/\s+/);
  return ((p[2]?.[0] ?? p[0]?.[0] ?? '') + (p[0]?.[0] ?? '')).toUpperCase() || '?';
}

function Dato({ icon, label, value }) {
  if (value === null || value === undefined || value === '') return null;
  return (
    <div className="dato">
      <span className="dato-icon">{icon}</span>
      <div>
        <span className="dato-label">{label}</span>
        <strong className="dato-valor">{String(value)}</strong>
      </div>
    </div>
  );
}

function Tarjeta({ paso, icon, titulo, subtitulo, children, acento }) {
  return (
    <section className={`card fade-up ${acento ?? ''}`}>
      <header className="card-head">
        <div className="card-icon">{icon}</div>
        <div>
          <span className="card-paso">Paso {paso}</span>
          <h2>{titulo}</h2>
          {subtitulo && <p>{subtitulo}</p>}
        </div>
      </header>
      {children}
    </section>
  );
}

function Stepper({ actual }) {
  return (
    <ol className="stepper">
      {PASOS.map((p, i) => (
        <li key={p} className={i < actual ? 'hecho' : i === actual ? 'activo' : ''}>
          <span className="bolita">{i < actual ? '✓' : i + 1}</span>
          <span className="paso-nombre">{p}</span>
        </li>
      ))}
    </ol>
  );
}

function Skeleton({ filas = 3 }) {
  return (
    <div className="skeleton">
      {Array.from({ length: filas }).map((_, i) => <div key={i} className="sk-linea" />)}
    </div>
  );
}

function GaugePuntos({ puntos }) {
  const max = 30;
  const valor = puntos ?? 0;
  const r = 70;
  const circ = 2 * Math.PI * r;
  const color = valor > 20 ? '#16a34a' : valor > 10 ? '#f59e0b' : '#dc2626';
  return (
    <div className="gauge">
      <svg viewBox="0 0 180 180" width="180" height="180">
        <circle cx="90" cy="90" r={r} className="gauge-fondo" />
        <circle cx="90" cy="90" r={r} className="gauge-valor" stroke={color}
                strokeDasharray={circ} strokeDashoffset={circ * (1 - valor / max)} />
      </svg>
      <div className="gauge-texto">
        <strong style={{ color }}>{puntos ?? '—'}</strong>
        <span>de {max} puntos</span>
      </div>
    </div>
  );
}

function Placa({ numero }) {
  return (
    <div className="placa">
      <span className="placa-banda">ECUADOR</span>
      <span className="placa-numero">{numero}</span>
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
  const [cargandoLicencia, setCargandoLicencia] = useState(false);
  const [error, setError] = useState('');
  const [errorVehiculo, setErrorVehiculo] = useState('');
  const [errorLicencia, setErrorLicencia] = useState('');

  const pasoActual = vehiculo ? 4 : persona ? (licencia || errorLicencia ? 3 : 2) : 0;

  async function consultarLicencia(cedula, placaVehiculo = '') {
    setErrorLicencia('');
    setCargandoLicencia(true);
    try {
      setLicencia(await consultarPuntosLicencia(cedula, placaVehiculo));
    } catch (e) {
      setLicencia(null);
      setErrorLicencia(e.message);
    } finally {
      setCargandoLicencia(false);
    }
  }

  async function onVerificar(e) {
    e.preventDefault();
    setError('');
    setPersona(null);
    setVehiculo(null);
    setLicencia(null);
    setErrorLicencia('');
    setErrorVehiculo('');
    setCargando('persona');
    try {
      const data = await verificarContribuyente(ruc.trim(), email.trim());
      setPersona(data);
      // Con la cédula (10 primeros dígitos del RUC) se consultan los puntos de la licencia en la ANT.
      consultarLicencia(data.cedula);
    } catch (err) {
      setError(err.message);
    } finally {
      setCargando('');
    }
  }

  async function onVehiculo(e) {
    e.preventDefault();
    setErrorVehiculo('');
    setVehiculo(null);
    setCargando('vehiculo');
    try {
      const placaNorm = placa.trim().toUpperCase().replace('-', '');
      setVehiculo(await obtenerVehiculo(placaNorm));
      consultarLicencia(persona.cedula, placaNorm);
    } catch (err) {
      setErrorVehiculo(err.message);
    } finally {
      setCargando('');
    }
  }

  function nuevaConsulta() {
    setPersona(null);
    setVehiculo(null);
    setLicencia(null);
    setError('');
    setErrorLicencia('');
    setErrorVehiculo('');
    setRuc('');
    setPlaca('');
  }

  const c = persona?.contribuyente;
  const origen = licencia ? ORIGEN[licencia.origen] : null;

  return (
    <>
      <div className="hero">
        <div className="hero-bandera" />
        <div className="hero-contenido">
          <span className="hero-chip"><i className="mini-bandera" /> SRI · ANT · Ecuador</span>
          <h1>Consulta de contribuyente, vehículo y licencia</h1>
          <p>Verifica tu RUC en el SRI, conoce los datos de tu vehículo y los puntos de tu licencia en un solo lugar.</p>
          <Stepper actual={pasoActual} />
        </div>
      </div>

      <main>
        <Tarjeta paso={1} icon="👤" titulo="Datos del usuario"
                 subtitulo="Ingresa tu correo y el RUC de persona natural (13 dígitos terminados en 001).">
          <form onSubmit={onVerificar} className="form">
            <label className="campo">
              <span>Correo electrónico</span>
              <div className="input-icono">
                <i>✉️</i>
                <input type="email" required value={email} onChange={(e) => setEmail(e.target.value)}
                       placeholder="usuario@correo.com" />
              </div>
            </label>
            <label className="campo">
              <span>RUC (persona natural)</span>
              <div className="input-icono">
                <i>🧾</i>
                <input required pattern="\d{10}001" maxLength={13} inputMode="numeric" value={ruc}
                       onChange={(e) => setRuc(e.target.value.replace(/\D/g, ''))}
                       placeholder="1712345678001" title="13 dígitos terminados en 001" />
              </div>
              <small className="contador">{ruc.length}/13</small>
            </label>
            <button className="btn" disabled={cargando === 'persona'}>
              {cargando === 'persona' ? <><span className="spinner" /> Verificando…</> : 'Verificar en el SRI →'}
            </button>
          </form>
          {error && <div className="alerta error fade-up">⛔ {error}</div>}
        </Tarjeta>

        {cargando === 'persona' && <section className="card"><Skeleton filas={4} /></section>}

        {persona && (
          <Tarjeta paso={2} icon="🧾" titulo="Contribuyente verificado"
                   subtitulo="Información obtenida del catastro del SRI." acento="acento-verde">
            <div className="perfil">
              <div className="avatar">{iniciales(c.razonSocial)}</div>
              <div className="perfil-info">
                <h3>{c.razonSocial}</h3>
                <div className="chips">
                  <span className="chip chip-verde">✔ {c.tipoContribuyente}</span>
                  <span className={`chip ${c.estadoContribuyenteRuc === 'ACTIVO' ? 'chip-verde' : 'chip-rojo'}`}>
                    ● {c.estadoContribuyenteRuc}
                  </span>
                  {c.regimen && <span className="chip chip-azul">{c.regimen}</span>}
                </div>
              </div>
            </div>
            <div className="grid">
              <Dato icon="🆔" label="RUC" value={c.numeroRuc} />
              <Dato icon="🪪" label="Cédula" value={persona.cedula} />
              <Dato icon="✉️" label="Correo" value={persona.email} />
              <Dato icon="💼" label="Actividad económica" value={c.actividadEconomicaPrincipal} />
              <Dato icon="📒" label="Obligado a llevar contabilidad" value={c.obligadoLlevarContabilidad} />
              <Dato icon="📅" label="Inicio de actividades"
                    value={formatearFecha(c.informacionFechasContribuyente?.fechaInicioActividades)} />
            </div>
          </Tarjeta>
        )}

        {persona && (
          <Tarjeta paso={3} icon="🪪" titulo="Puntos de la licencia"
                   subtitulo="Consulta a la ANT protegida con caché en la nube (Redis)." acento="acento-azul">
            {cargandoLicencia && (
              <div className="cargando-ant"><span className="spinner oscuro" /> Consultando a la ANT…</div>
            )}

            {!cargandoLicencia && licencia && (
              <div className="licencia fade-up">
                <GaugePuntos puntos={licencia.licencia.puntosDisponibles} />
                <div className="licencia-info">
                  <span className={`origen origen-${origen.clase}`}>{origen.icon} {origen.label}</span>
                  {licencia.aviso && <p className="aviso">{licencia.aviso}</p>}
                  <div className="grid compacto">
                    <Dato icon="📋" label="Citaciones" value={licencia.licencia.totalCitaciones} />
                    <Dato icon="➖" label="Puntos descontados" value={licencia.licencia.puntosDescontados} />
                    <Dato icon="🕒" label="Información obtenida" value={new Date(licencia.obtenidoEn).toLocaleString()} />
                  </div>
                </div>
              </div>
            )}

            {!cargandoLicencia && licencia?.licencia.citaciones.length > 0 && (
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

            {!cargandoLicencia && errorLicencia && (
              <div className="ant-caida fade-up">
                <div className="ant-caida-icono">📡</div>
                <div>
                  <h3>La ANT no está disponible en este momento</h3>
                  <p>{errorLicencia}</p>
                  <p className="nota">
                    Cada vez que la ANT responda correctamente, la información se guardará en la caché en la nube
                    y se mostrará desde allí cuando el portal vuelva a fallar.
                  </p>
                  <button className="btn secundario" onClick={() => consultarLicencia(persona.cedula, vehiculo?.numeroPlaca ?? '')}>
                    ↻ Reintentar
                  </button>
                </div>
              </div>
            )}
          </Tarjeta>
        )}

        {persona && (
          <Tarjeta paso={4} icon="🚗" titulo="Matrícula de su vehículo"
                   subtitulo="Ingresa la placa para obtener la información desde el SRI." acento="acento-naranja">
            <form onSubmit={onVehiculo} className="form inline">
              <label className="campo">
                <span>Placa</span>
                <div className="input-icono">
                  <i>🔎</i>
                  <input required value={placa} maxLength={10} onChange={(e) => setPlaca(e.target.value.toUpperCase())}
                         placeholder="PBA1234" className="mayus" />
                </div>
              </label>
              <button className="btn naranja" disabled={cargando === 'vehiculo'}>
                {cargando === 'vehiculo' ? <><span className="spinner" /> Consultando…</> : 'Consultar vehículo →'}
              </button>
            </form>
            {errorVehiculo && <div className="alerta error fade-up">⛔ {errorVehiculo}</div>}
            {cargando === 'vehiculo' && <Skeleton filas={3} />}
            {vehiculo && (
              <div className="vehiculo fade-up">
                <div className="vehiculo-cabecera">
                  <Placa numero={vehiculo.numeroPlaca} />
                  <div>
                    <h3>{vehiculo.descripcionMarca} {vehiculo.descripcionModelo}</h3>
                    <div className="chips">
                      {vehiculo.anioAuto && <span className="chip chip-azul">📅 {vehiculo.anioAuto}</span>}
                      {vehiculo.ultimoAnioPagado && (
                        <span className="chip chip-verde">💳 Último pago {vehiculo.ultimoAnioPagado}</span>
                      )}
                    </div>
                  </div>
                </div>
                <div className="grid">
                  <Dato icon="🏭" label="Marca" value={vehiculo.descripcionMarca} />
                  <Dato icon="🚙" label="Modelo" value={vehiculo.descripcionModelo} />
                  <Dato icon="🌎" label="País" value={vehiculo.descripcionPais} />
                  <Dato icon="🏷️" label="CAMV / CPN" value={vehiculo.numeroCamvCpn} />
                  <Dato icon="🚦" label="Clase" value={vehiculo.nombreClase} />
                  <Dato icon="🛠️" label="Servicio" value={vehiculo.descripcionServicio} />
                  <Dato icon="⚙️" label="Cilindraje" value={vehiculo.cilindraje} />
                  <Dato icon="🎨" label="Color" value={vehiculo.colorVehiculo1} />
                  <Dato icon="📍" label="Cantón" value={vehiculo.descripcionCanton} />
                  <Dato icon="⏳" label="Caducidad matrícula" value={formatearFecha(vehiculo.fechaCaducidadMatricula)} />
                </div>
              </div>
            )}
          </Tarjeta>
        )}

        {persona && (
          <div className="acciones">
            <button className="btn fantasma" onClick={nuevaConsulta}>↺ Nueva consulta</button>
          </div>
        )}
      </main>

      <footer>
        React · Java 21 / Spring Boot (microservicios SRI y ANT) · Redis Cloud (Upstash) · Modelo C4 en IcePanel
      </footer>
    </>
  );
}
