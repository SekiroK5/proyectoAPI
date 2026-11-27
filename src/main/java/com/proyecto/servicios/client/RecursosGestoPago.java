package com.proyecto.servicios.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Datos de presentacion de los productos de GestoPago segun su documentacion: URL publica del logotipo
 * del servicio, imagen de ayuda de la referencia y nombre de la categoria (idCatTipoServicio).
 * Las imagenes son archivos estaticos de GestoPago; los descarga cada app, no este servidor, por lo que
 * no cuentan en el cupo de llamadas a la API.
 */
@Component
public class RecursosGestoPago {

    /** Tabla "Tipo Servicio (idCatTipoServicio)" de la documentacion de PuntoRed. */
    private static final Map<Integer, String> CATEGORIAS = Map.ofEntries(
            Map.entry(1, "Movistar"),
            Map.entry(2, "Telcel"),
            Map.entry(3, "AT&T"),
            Map.entry(4, "Unefon"),
            Map.entry(5, "Pago de Servicios"),
            Map.entry(6, "Nextel"),
            Map.entry(7, "Pago de Impuestos / Gobierno"),
            Map.entry(8, "Productos Financieros"),
            Map.entry(9, "Productos por Catálogo"),
            Map.entry(10, "Servicios de Prepago"),
            Map.entry(11, "Entretenimiento"),
            Map.entry(12, "Virgin"),
            Map.entry(13, "Otras Recargas"),
            Map.entry(14, "Venta de Seguros"),
            Map.entry(15, "Pago de Derechos de Agua"),
            Map.entry(16, "Juegos y Sorteos"),
            Map.entry(17, "MazTiempo"),
            Map.entry(18, "Servicios de Autopistas y Transportes"),
            Map.entry(19, "Internet de Prepago"));

    private final String urlBase;

    public RecursosGestoPago(@Value("${gestopago.auth.url}") String urlBase) {
        this.urlBase = urlBase.endsWith("/") ? urlBase.substring(0, urlBase.length() - 1) : urlBase;
    }

    public String logoUrl(Integer idServicio) {
        return idServicio == null ? null : urlBase + "/sistema/images/gestopago/servicios/" + idServicio + ".png";
    }

    /** Imagen de ayuda que muestra donde encontrar la referencia en el recibo (solo algunos servicios la tienen). */
    public String imagenReferenciaUrl(Integer idServicio) {
        return idServicio == null ? null : urlBase + "/sistema/images/gestopago/referencias/" + idServicio + ".jpg";
    }

    public String categoria(Integer idCatTipoServicio) {
        return idCatTipoServicio == null ? "Otros" : CATEGORIAS.getOrDefault(idCatTipoServicio, "Otros");
    }

    /** Tipo de operacion del producto segun su tipoFront. */
    public static String tipoOperacion(Integer tipoFront) {
        if (tipoFront == null) {
            return "NO_SOPORTADA";
        }
        return switch (tipoFront) {
            case 1 -> "RECARGA";
            case 2, 4 -> "PAGO_SERVICIO";
            default -> "NO_SOPORTADA";
        };
    }
}
