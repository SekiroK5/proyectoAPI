package com.proyecto.servicios.model.producto;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@XmlRootElement(name = "RESPONSE")
@XmlAccessorType(XmlAccessType.FIELD)
public class ProductoListResponse {

    @XmlElement(name = "MENSAJE")
    private MensajeXml mensaje;

    @XmlElement(name = "PRODUCTOS")
    private ProductosXml productos;

    public List<ProductoXml> getListaProductos() {
        if (productos == null) return List.of();
        return productos.getProductos() != null ? productos.getProductos() : List.of();
    }
}
