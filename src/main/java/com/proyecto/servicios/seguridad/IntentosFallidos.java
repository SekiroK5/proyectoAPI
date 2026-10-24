package com.proyecto.servicios.seguridad;

import com.proyecto.servicios.dto.response.ApiResponse;
import com.proyecto.servicios.entity.sf.Cliente;
import com.proyecto.servicios.repository.sf.ClienteRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Bloqueo temporal por intentos fallidos consecutivos. Lo comparten el inicio de sesion y la
 * autorizacion de pagos: un codigo A2F incorrecto cuenta igual en ambos lugares.
 */
@Component
@Slf4j
public class IntentosFallidos {

    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm");

    private final ClienteRepository clienteRepository;
    private final Clock clock;
    private final int maxIntentos;
    private final long minutosBloqueo;

    public IntentosFallidos(ClienteRepository clienteRepository,
                            Clock clock,
                            @Value("${seguridad.login.max-intentos}") int maxIntentos,
                            @Value("${seguridad.login.bloqueo-minutos}") long minutosBloqueo) {
        this.clienteRepository = clienteRepository;
        this.clock = clock;
        this.maxIntentos = maxIntentos;
        this.minutosBloqueo = minutosBloqueo;
    }

    public boolean estaBloqueado(Cliente cliente) {
        return cliente.getBloqueadoHasta() != null && cliente.getBloqueadoHasta().isAfter(LocalDateTime.now(clock));
    }

    public <T> ApiResponse<T> respuestaBloqueo(Cliente cliente) {
        return ApiResponse.error(HttpStatus.LOCKED, "Demasiados intentos fallidos. El inicio de sesión está bloqueado hasta las "
                + HORA.format(cliente.getBloqueadoHasta()) + ".");
    }

    /** Cuenta el intento fallido y bloquea al llegar al maximo. Devuelve 401 con el mensaje, o 423 si bloqueo. */
    public <T> ApiResponse<T> registrarFallo(Cliente cliente, String mensaje) {
        int intentos = cliente.getIntentosFallidos() == null ? 1 : cliente.getIntentosFallidos() + 1;
        if (intentos >= maxIntentos) {
            cliente.setIntentosFallidos(0);
            cliente.setBloqueadoHasta(LocalDateTime.now(clock).plusMinutes(minutosBloqueo));
            clienteRepository.save(cliente);
            log.warn("Cliente ID={} bloqueado {} min por intentos fallidos", cliente.getId(), minutosBloqueo);
            return respuestaBloqueo(cliente);
        }
        cliente.setIntentosFallidos(intentos);
        clienteRepository.save(cliente);
        log.warn("Intento fallido {}/{} para cliente ID={}", intentos, maxIntentos, cliente.getId());
        return ApiResponse.error(HttpStatus.UNAUTHORIZED, mensaje);
    }

    public void reiniciar(Cliente cliente) {
        cliente.setIntentosFallidos(0);
        cliente.setBloqueadoHasta(null);
        clienteRepository.save(cliente);
    }
}
