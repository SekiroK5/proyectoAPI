package com.proyecto.servicios.model.gestopago;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Respuesta XML de sendTx.do y confirmTx.do. Los valores numericos llegan como texto porque SALDO
 * puede venir en notacion cientifica y los codigos traen ceros a la izquierda ("01").
 */
@Getter
@Setter
@NoArgsConstructor
@XmlRootElement(name = "RESPONSE")
@XmlAccessorType(XmlAccessType.FIELD)
public class SendTxResponseXml {

    @XmlElement(name = "ID_TX")
    private String idTx;

    @XmlElement(name = "NUM_AUTORIZACION")
    private String numAutorizacion;

    @XmlElement(name = "SALDO")
    private String saldo;

    @XmlElement(name = "COMISION")
    private String comision;

    @XmlElement(name = "FECHA")
    private String fecha;

    @XmlElement(name = "MONTO")
    private String monto;

    @XmlElement(name = "MENSAJE")
    private Mensaje mensaje;

    @Getter
    @Setter
    @NoArgsConstructor
    @XmlAccessorType(XmlAccessType.FIELD)
    public static class Mensaje {

        /** "01" = exito en sendTx; en confirmTx "06" = aplicada y "70" = no aplicada. */
        @XmlElement(name = "CODIGO")
        private String codigo;

        @XmlElement(name = "TEXTO")
        private String texto;

        @XmlElement(name = "REFERENCIA")
        private String referencia;

        /** PIN o folio de tarjetas de regalo; puede ser una URL de descarga. */
        @XmlElement(name = "PIN")
        private String pin;

        @XmlElement(name = "legend")
        private String legend;

        /** confirmTx devuelve el ID_TX dentro de MENSAJE. */
        @XmlElement(name = "ID_TX")
        private String idTx;
    }

    public String codigo() {
        return mensaje != null && mensaje.getCodigo() != null ? mensaje.getCodigo().trim() : null;
    }

    public String texto() {
        return mensaje != null && mensaje.getTexto() != null ? mensaje.getTexto().trim() : null;
    }

    /** ID_TX de la raiz (sendTx) o de MENSAJE (confirmTx). */
    public String idTransaccion() {
        if (idTx != null && !idTx.isBlank()) {
            return idTx.trim();
        }
        return mensaje != null && mensaje.getIdTx() != null ? mensaje.getIdTx().trim() : null;
    }
}
