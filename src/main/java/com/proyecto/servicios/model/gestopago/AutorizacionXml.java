package com.proyecto.servicios.model.gestopago;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Segundo formato de respuesta de sendTx que GestoPago devuelve en algunos rechazos y que no viene
 * en su documentacion, por ejemplo cuando el carrier no responde:
 * <pre>
 * &lt;AUTORIZACION&gt;
 *     &lt;NUM_AUTORIZACION&gt;-1&lt;/NUM_AUTORIZACION&gt;
 *     &lt;CODIGO_MENSAJE&gt;03&lt;/CODIGO_MENSAJE&gt;
 *     &lt;MENSAJE&gt;Por el momento el carrier no responde, favor de intentar mas tarde.&lt;/MENSAJE&gt;
 * &lt;/AUTORIZACION&gt;
 * </pre>
 * Se convierte a {@link SendTxResponseXml} para que el resto del flujo lo trate igual.
 */
@Getter
@Setter
@NoArgsConstructor
@XmlRootElement(name = "AUTORIZACION")
@XmlAccessorType(XmlAccessType.FIELD)
public class AutorizacionXml {

    @XmlElement(name = "NUM_AUTORIZACION")
    private String numAutorizacion;

    @XmlElement(name = "CODIGO_MENSAJE")
    private String codigoMensaje;

    @XmlElement(name = "MENSAJE")
    private String mensaje;

    public SendTxResponseXml comoRespuesta() {
        SendTxResponseXml.Mensaje m = new SendTxResponseXml.Mensaje();
        m.setCodigo(codigoMensaje);
        m.setTexto(mensaje);
        SendTxResponseXml respuesta = new SendTxResponseXml();
        respuesta.setNumAutorizacion(numAutorizacion);
        respuesta.setMensaje(m);
        return respuesta;
    }
}
