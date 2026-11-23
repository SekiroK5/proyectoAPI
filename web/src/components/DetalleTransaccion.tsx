import type { Transaccion } from '../api/tipos'
import { Estatus, Logo, dinero, fecha } from './Comunes'

/** Datos de una transacción: se usa en el resultado del pago y en el detalle del historial. */
export function DetalleTransaccion({ tx }: { tx: Transaccion }) {
  return (
    <div className="detalle-tx">
      <div className="detalle-encabezado">
        <Logo url={tx.logoUrl} nombre={tx.servicio} tamanio={64} />
        <div>
          <h2>{tx.servicio ?? `Servicio ${tx.idServicio}`}</h2>
          <p className="tenue">{tx.producto}</p>
        </div>
        <Estatus estatus={tx.estatus} />
      </div>
      <dl className="lista-datos">
        <dt>Importe</dt>
        <dd className="importe">{dinero(tx.monto)}</dd>
        <dt>Fecha</dt>
        <dd>{fecha(tx.fecha)}</dd>
        {tx.referencia && (
          <>
            <dt>Referencia</dt>
            <dd>{tx.referencia}</dd>
          </>
        )}
        {tx.telefono && !tx.referencia && !tx.telefono.includes('1111111111') && (
          <>
            <dt>Teléfono</dt>
            <dd>{tx.telefono}</dd>
          </>
        )}
        <dt>Folio de autorización</dt>
        <dd>{tx.numAutorizacion && tx.numAutorizacion !== '-1' ? tx.numAutorizacion : 'Sin folio'}</dd>
        {tx.idTx && (
          <>
            <dt>Id de transacción GestoPago</dt>
            <dd>{tx.idTx}</dd>
          </>
        )}
        <dt>Identificador (UPC)</dt>
        <dd className="mono">{tx.upc}</dd>
        {tx.pin && (
          <>
            <dt>PIN / folio de canje</dt>
            <dd className="mono">
              {tx.pin.startsWith('http') ? <a href={tx.pin} target="_blank" rel="noreferrer">Descargar comprobante</a> : tx.pin}
            </dd>
          </>
        )}
        {tx.saldoCuenta && (
          <>
            <dt>Saldo de la cuenta</dt>
            <dd>{dinero(tx.saldoCuenta)}</dd>
          </>
        )}
        {tx.mensajeGestoPago && (
          <>
            <dt>Respuesta de GestoPago</dt>
            <dd>{tx.mensajeGestoPago}</dd>
          </>
        )}
      </dl>
    </div>
  )
}
