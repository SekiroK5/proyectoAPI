package com.proyecto.servicios.seguridad;

import com.proyecto.servicios.entity.sf.Cliente;
import com.proyecto.servicios.enums.Catalogos.PropositoCodigo;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HexFormat;

/**
 * Codigos de 6 digitos enviados al correo del cliente: confirmar el correo, iniciar sesion o autorizar
 * un pago o deposito.
 * <ul>
 *   <li>Solo se guarda el SHA-256 del codigo, nunca el codigo.</li>
 *   <li>Cada codigo sirve solo para su proposito; pedir otro invalida el anterior.</li>
 *   <li>Vence a los {@value #MINUTOS_VIGENCIA} minutos y admite {@value #MAX_INTENTOS} intentos.</li>
 *   <li>Se puede pedir otro cada {@value #SEGUNDOS_ENTRE_ENVIOS} segundos.</li>
 * </ul>
 * No guarda: quien lo usa persiste el cliente dentro de su transaccion.
 */
@Component
public class VerificacionCorreo {

    public static final int MINUTOS_VIGENCIA = 15;
    public static final int MAX_INTENTOS = 5;
    public static final long SEGUNDOS_ENTRE_ENVIOS = 60;

    /** Resultado de comparar el codigo recibido. */
    public enum Resultado { CORRECTO, INCORRECTO, VENCIDO, SIN_INTENTOS }

    private final EnvioCorreo envioCorreo;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public VerificacionCorreo(EnvioCorreo envioCorreo, Clock clock) {
        this.envioCorreo = envioCorreo;
        this.clock = clock;
    }

    /** Marca el correo como no verificado y envia el codigo para confirmarlo (alta de cliente). */
    public boolean iniciar(Cliente cliente) {
        cliente.setCorreoVerificado(false);
        return emitir(cliente, PropositoCodigo.CONFIRMAR_CORREO);
    }

    /** Genera un codigo nuevo para el proposito indicado, guarda su hash y lo envia. False si el SMTP fallo. */
    public boolean emitir(Cliente cliente, PropositoCodigo proposito) {
        String codigo = "%06d".formatted(random.nextInt(1_000_000));
        LocalDateTime ahora = LocalDateTime.now(clock);
        cliente.setCorreoCodigoHash(hash(codigo));
        cliente.setCorreoCodigoExpira(ahora.plusMinutes(MINUTOS_VIGENCIA));
        cliente.setCorreoCodigoProposito(proposito);
        cliente.setCorreoIntentos(0);
        cliente.setCorreoUltimoEnvio(ahora);
        return envioCorreo.enviarCodigo(cliente.getCorreoElectronico(), cliente.getNombre(), codigo, MINUTOS_VIGENCIA, proposito);
    }

    /** Segundos que faltan para poder pedir otro codigo (0 si ya se puede). */
    public long segundosParaReenviar(Cliente cliente) {
        if (cliente.getCorreoUltimoEnvio() == null) {
            return 0;
        }
        long transcurridos = Duration.between(cliente.getCorreoUltimoEnvio(), LocalDateTime.now(clock)).getSeconds();
        return Math.max(0, SEGUNDOS_ENTRE_ENVIOS - transcurridos);
    }

    /** true si hay un codigo vigente (sin vencer y con intentos) para ese proposito. */
    public boolean tieneCodigoVigente(Cliente cliente, PropositoCodigo proposito) {
        return cliente.getCorreoCodigoHash() != null
                && proposito == cliente.getCorreoCodigoProposito()
                && cliente.getCorreoCodigoExpira() != null
                && !LocalDateTime.now(clock).isAfter(cliente.getCorreoCodigoExpira())
                && intentos(cliente) < MAX_INTENTOS;
    }

    /**
     * Compara el codigo contra el vigente del proposito. Si es correcto borra el codigo (un solo uso) y, si
     * era para confirmar el correo, lo marca como verificado. Un codigo de otro proposito cuenta como vencido.
     */
    public Resultado verificar(Cliente cliente, String codigo, PropositoCodigo proposito) {
        if (cliente.getCorreoCodigoHash() == null || proposito != cliente.getCorreoCodigoProposito()
                || cliente.getCorreoCodigoExpira() == null
                || LocalDateTime.now(clock).isAfter(cliente.getCorreoCodigoExpira())) {
            return Resultado.VENCIDO;
        }
        int intentos = intentos(cliente);
        if (intentos >= MAX_INTENTOS) {
            return Resultado.SIN_INTENTOS;
        }
        if (!coincide(cliente, codigo)) {
            cliente.setCorreoIntentos(intentos + 1);
            return cliente.getCorreoIntentos() >= MAX_INTENTOS ? Resultado.SIN_INTENTOS : Resultado.INCORRECTO;
        }
        if (proposito == PropositoCodigo.CONFIRMAR_CORREO) {
            cliente.setCorreoVerificado(true);
        }
        limpiar(cliente);
        return Resultado.CORRECTO;
    }

    /** Compara en tiempo constante sin modificar nada (para consumirlo despues de forma atomica). */
    public boolean coincide(Cliente cliente, String codigo) {
        return codigo != null && cliente.getCorreoCodigoHash() != null && MessageDigest.isEqual(
                hash(codigo).getBytes(StandardCharsets.US_ASCII),
                cliente.getCorreoCodigoHash().getBytes(StandardCharsets.US_ASCII));
    }

    /** Borra el codigo vigente del cliente en memoria (ya se uso). */
    public void limpiar(Cliente cliente) {
        cliente.setCorreoCodigoHash(null);
        cliente.setCorreoCodigoExpira(null);
        cliente.setCorreoCodigoProposito(null);
        cliente.setCorreoIntentos(0);
    }

    private static int intentos(Cliente cliente) {
        return cliente.getCorreoIntentos() == null ? 0 : cliente.getCorreoIntentos();
    }

    static String hash(String codigo) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(codigo.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 no disponible", ex);
        }
    }
}
