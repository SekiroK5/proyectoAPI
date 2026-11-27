package com.proyecto.servicios.seguridad;

import com.proyecto.servicios.dto.response.ApiResponse;
import com.proyecto.servicios.entity.sf.Cliente;
import com.proyecto.servicios.enums.Catalogos.PropositoCodigo;
import com.proyecto.servicios.repository.sf.ClienteRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * Autoriza una operacion de dinero (pago o deposito) con el codigo de la app autenticadora o con un codigo
 * pedido por correo para autorizar operaciones.
 * <ul>
 *   <li>Un codigo incorrecto cuenta para el mismo bloqueo por intentos que el inicio de sesion.</li>
 *   <li>Cada codigo autoriza una sola operacion: se marca como usado con un UPDATE condicional, asi
 *       dos peticiones iguales simultaneas (doble clic, reintento automatico) no pasan las dos.</li>
 * </ul>
 */
@Component
@Slf4j
public class ValidadorA2F {

    public static final String MSG_SIN_CONFIGURAR =
            "Configure su app autenticadora o pida un código por correo para autorizar la operación.";
    public static final String MSG_INVALIDO = "El código 2FA no es válido o ya fue utilizado.";
    public static final String MSG_EN_USO = "Ya hay una operación en proceso con este código 2FA. Consulte su saldo antes de reintentar.";

    private final Totp totp;
    private final IntentosFallidos intentosFallidos;
    private final ClienteRepository clienteRepository;
    private final VerificacionCorreo verificacionCorreo;

    public ValidadorA2F(Totp totp, IntentosFallidos intentosFallidos, ClienteRepository clienteRepository,
                        VerificacionCorreo verificacionCorreo) {
        this.totp = totp;
        this.intentosFallidos = intentosFallidos;
        this.clienteRepository = clienteRepository;
        this.verificacionCorreo = verificacionCorreo;
    }

    /** Devuelve null si el codigo autoriza la operacion; si no, la respuesta de error lista para devolver. */
    public <T> ApiResponse<T> validar(Cliente cliente, String codigo) {
        if (intentosFallidos.estaBloqueado(cliente)) {
            return intentosFallidos.respuestaBloqueo(cliente);
        }
        boolean tieneApp = Boolean.TRUE.equals(cliente.getA2fActivo()) && cliente.getA2fSecreto() != null;
        boolean tieneCodigoCorreo = verificacionCorreo.tieneCodigoVigente(cliente, PropositoCodigo.OPERACION);
        if (!tieneApp && !tieneCodigoCorreo) {
            return ApiResponse.error(HttpStatus.CONFLICT, MSG_SIN_CONFIGURAR);
        }
        if (tieneCodigoCorreo && verificacionCorreo.coincide(cliente, codigo)) {
            return consumirCodigoCorreo(cliente);
        }
        if (!tieneApp) {
            return intentosFallidos.registrarFallo(cliente, MSG_INVALIDO);
        }
        long paso = totp.verificar(cliente.getA2fSecreto(), codigo, cliente.getA2fUltimoPaso());
        if (paso < 0) {
            return intentosFallidos.registrarFallo(cliente, MSG_INVALIDO);
        }
        // Se marca en la base de datos antes de mover dinero: solo una peticion simultanea lo logra
        if (clienteRepository.marcarPasoA2F(cliente.getId(), paso) == 0) {
            log.warn("Codigo 2FA ya usado por otra peticion simultanea del cliente ID={}", cliente.getId());
            return ApiResponse.error(HttpStatus.CONFLICT, MSG_EN_USO);
        }
        cliente.setA2fUltimoPaso(paso);
        intentosFallidos.reiniciar(cliente);
        return null;
    }

    private <T> ApiResponse<T> consumirCodigoCorreo(Cliente cliente) {
        if (clienteRepository.consumirCodigoCorreo(cliente.getId(), cliente.getCorreoCodigoHash()) == 0) {
            log.warn("Codigo de correo ya usado por otra peticion simultanea del cliente ID={}", cliente.getId());
            return ApiResponse.error(HttpStatus.CONFLICT, MSG_EN_USO);
        }
        verificacionCorreo.limpiar(cliente);
        intentosFallidos.reiniciar(cliente);
        log.info("Operacion autorizada con codigo de correo para cliente ID={}", cliente.getId());
        return null;
    }
}
