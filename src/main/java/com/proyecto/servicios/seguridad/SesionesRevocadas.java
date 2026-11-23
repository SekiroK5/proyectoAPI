package com.proyecto.servicios.seguridad;

import com.proyecto.servicios.entity.sf.TokenRevocado;
import com.proyecto.servicios.repository.sf.TokenRevocadoRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;

/**
 * Cierre de sesion con JWT: el token de acceso no guarda estado en el servidor, asi que al cerrar sesion
 * su identificador (jti) se guarda como revocado hasta que vence y el decoder lo rechaza. Asi un token
 * copiado no sigue sirviendo despues de cerrar sesion en la web o en el celular.
 */
@Component
@Slf4j
public class SesionesRevocadas {

    private final TokenRevocadoRepository repository;
    private final Clock clock;

    public SesionesRevocadas(TokenRevocadoRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    public void revocar(String jti, Instant expira) {
        if (jti == null) {
            return;
        }
        TokenRevocado revocado = new TokenRevocado();
        revocado.setJti(jti);
        revocado.setExpira(LocalDateTime.ofInstant(expira != null ? expira : clock.instant(), clock.getZone()));
        repository.save(revocado);
    }

    public boolean estaRevocado(String jti) {
        return jti != null && repository.existsById(jti);
    }

    @Scheduled(cron = "${seguridad.sesiones.limpieza-cron:0 30 3 * * *}")
    public void limpiarVencidos() {
        int borrados = repository.borrarVencidos(LocalDateTime.now(clock));
        if (borrados > 0) {
            log.info("Se eliminaron {} tokens revocados ya vencidos", borrados);
        }
    }
}
