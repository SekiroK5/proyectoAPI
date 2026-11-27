import type { Transaccion } from '../api/tipos'
import { Estatus, Logo, agruparCuenta, dinero, fecha } from './Comunes'

/** Datos de una transacción: se usa en el resultado del pago y en el detalle del historial. */
export function DetalleTransaccion({ tx, leyenda }: { tx: Transaccion; leyenda?: string }) {
  const folio = tx.numAutorizacion && tx.numAutorizacion !== '-1' && tx.numAutorizacion !== '0' ? tx.numAutorizacion : null
  return (
    <div className="detalle-tx">
      <div className="detalle-encabezado">
        <Logo url={tx.logoUrl} nombre={tx.servicio} tamanio={56} />
        <div>
          <h2>{tx.servicio ?? `Servicio ${tx.idServicio}`}</h2>
          <p className="tenue">{tx.producto}</p>
        </div>
        <Estatus estatus={tx.estatus} />
      </div>
      <div className="resumen-total">
        <span className="tenue">Importe</span>
        <strong className="importe grande">{dinero(tx.monto)}</strong>
      </div>
      <dl className="lista-datos">
        <dt>Fecha</dt>
        <dd>{fecha(tx.fecha)}</dd>
        {tx.referencia && (
          <>
            <dt>Referencia</dt>
            <dd className="mono">{tx.referencia}</dd>
          </>
        )}
        {tx.telefono && !tx.referencia && !tx.telefono.includes('1111111111') && (
          <>
            <dt>Teléfono</dt>
            <dd>{tx.telefono}</dd>
          </>
        )}
        <dt>Folio de autorización</dt>
        <dd className="mono">{folio ?? 'Sin folio'}</dd>
        {tx.idTx && tx.idTx !== '-1' && (
          <>
            <dt>Id de transacción GestoPago</dt>
            <dd className="mono">{tx.idTx}</dd>
          </>
        )}
        <dt>Identificador (UPC)</dt>
        <dd className="mono">{tx.upc}</dd>
        {tx.numeroCuenta && (
          <>
            <dt>Cuenta de cargo</dt>
            <dd className="mono">{agruparCuenta(tx.numeroCuenta)}</dd>
          </>
        )}
        {tx.pin && (
          <>
            <dt>PIN / folio de canje</dt>
            <dd className="mono">
              {/^https?:\/\//.test(tx.pin.trim())
                ? <a href={tx.pin.trim()} target="_blank" rel="noreferrer noopener">Descargar comprobante</a>
                : tx.pin}
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
      {tx.pin && leyenda && (
        <div>
          <h3 style={{ marginBottom: '.4rem' }}>Instrucciones de canje</h3>
          <p className="leyenda">{leyenda}</p>
        </div>
      )}
    </div>
  )
}
