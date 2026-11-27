package com.proyecto.servicios.seguridad;

import com.proyecto.servicios.enums.Catalogos.PropositoCodigo;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;

/**
 * Envia los codigos de la plataforma por SMTP (por ejemplo Gmail con una contrasena de aplicacion), en HTML
 * con la imagen de la marca y una version de texto plano para los clientes de correo que no muestran HTML.
 * <p>
 * Si no hay cuenta de correo configurada ({@code MAIL_USERNAME} vacio) se trabaja en modo desarrollo:
 * el codigo se escribe en la consola de la API para poder probar el flujo en local sin enviar correos.
 */
@Component
@Slf4j
public class EnvioCorreo {

    static final String REMITENTE_NOMBRE = "GestoPago Pagos";

    private final ObjectProvider<JavaMailSender> mailSender;
    private final String remitente;

    public EnvioCorreo(ObjectProvider<JavaMailSender> mailSender,
                       @Value("${spring.mail.username:}") String remitente) {
        this.mailSender = mailSender;
        this.remitente = remitente == null ? "" : remitente.trim();
    }

    /** true si hay cuenta SMTP configurada; false en modo desarrollo (codigo en consola). */
    public boolean configurado() {
        return !remitente.isEmpty() && mailSender.getIfAvailable() != null;
    }

    /**
     * Envia el codigo para el proposito indicado.
     *
     * @return true si el correo se envio (o se mostro en consola en modo desarrollo); false si el SMTP fallo
     */
    public boolean enviarCodigo(String destino, String nombre, String codigo, long minutosVigencia, PropositoCodigo proposito) {
        if (!configurado()) {
            log.warn("[MODO DESARROLLO] Correo sin configurar (MAIL_USERNAME). Codigo {} para {} <{}>: {}",
                    proposito, nombre, enmascarar(destino), codigo);
            return true;
        }
        Plantilla plantilla = Plantilla.de(proposito);
        try {
            JavaMailSender sender = mailSender.getObject();
            MimeMessage mensaje = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mensaje, true, StandardCharsets.UTF_8.name());
            helper.setFrom(new InternetAddress(remitente, REMITENTE_NOMBRE, StandardCharsets.UTF_8.name()));
            helper.setTo(destino);
            helper.setSubject(plantilla.asunto());
            helper.setText(texto(plantilla, nombre, codigo, minutosVigencia), html(plantilla, nombre, codigo, minutosVigencia));
            sender.send(mensaje);
            log.info("Codigo {} enviado a {}", proposito, enmascarar(destino));
            return true;
        } catch (MailException | MessagingException | UnsupportedEncodingException ex) {
            log.error("No se pudo enviar el correo a {}: {}", enmascarar(destino), ex.getClass().getSimpleName());
            return false;
        }
    }

    /** Textos de cada tipo de correo. */
    record Plantilla(String asunto, String titulo, String explicacion, String aviso) {

        static Plantilla de(PropositoCodigo proposito) {
            return switch (proposito) {
                case CONFIRMAR_CORREO -> new Plantilla(
                        "Confirma tu correo en GestoPago Pagos",
                        "Confirma tu correo",
                        "Gracias por crear tu cuenta. Escribe este código en la aplicación para confirmar que este correo es tuyo.",
                        "Si tú no creaste una cuenta en GestoPago Pagos, ignora este mensaje: nadie podrá usar tu correo sin este código.");
                case INICIO_SESION -> new Plantilla(
                        "Tu código para iniciar sesión en GestoPago",
                        "Código de inicio de sesión",
                        "Recibimos una solicitud para iniciar sesión en tu cuenta. Escribe este código para continuar.",
                        "Si tú no intentaste iniciar sesión, alguien conoce tu contraseña: cámbiala lo antes posible.");
                case OPERACION -> new Plantilla(
                        "Código para autorizar tu operación en GestoPago",
                        "Autoriza tu operación",
                        "Escribe este código para autorizar el pago o depósito que estás haciendo en GestoPago Pagos.",
                        "Si tú no estás haciendo una operación, no compartas este código con nadie y cambia tu contraseña.");
            };
        }
    }

    static String texto(Plantilla p, String nombre, String codigo, long minutos) {
        return """
                Hola %s:

                %s

                Tu código: %s

                Vence en %d minutos y solo se puede usar una vez.

                %s

                Nunca te pediremos este código por teléfono ni por mensaje.
                — GestoPago Pagos
                """.formatted(nombre, p.explicacion(), codigo, minutos, p.aviso());
    }

    static String html(Plantilla p, String nombre, String codigo, long minutos) {
        String codigoSeparado = codigo.substring(0, 3) + "&nbsp;" + codigo.substring(3);
        return """
                <!DOCTYPE html>
                <html lang="es">
                <head><meta charset="UTF-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>%1$s</title></head>
                <body style="margin:0;padding:0;background:#f3f5fa;font-family:'Segoe UI',Roboto,Helvetica,Arial,sans-serif;color:#121a2b;">
                  <span style="display:none;max-height:0;overflow:hidden;opacity:0;">Tu código es %2$s. Vence en %3$d minutos.</span>
                  <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" style="background:#f3f5fa;padding:32px 12px;">
                    <tr><td align="center">
                      <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" style="max-width:520px;background:#ffffff;border-radius:18px;overflow:hidden;border:1px solid #e1e6ef;">
                        <tr>
                          <td bgcolor="#2754e6" style="background:#2754e6;background-image:linear-gradient(135deg,#2754e6,#6b3ff0);padding:26px 32px;">
                            <table role="presentation" cellpadding="0" cellspacing="0"><tr>
                              <td style="width:40px;height:40px;border-radius:12px;background:rgba(255,255,255,0.2);color:#ffffff;font-weight:800;font-size:20px;text-align:center;">G</td>
                              <td style="padding-left:12px;color:#ffffff;font-size:20px;font-weight:700;">GestoPago Pagos</td>
                            </tr></table>
                          </td>
                        </tr>
                        <tr>
                          <td style="padding:32px;">
                            <h1 style="margin:0 0 12px;font-size:22px;line-height:1.3;color:#121a2b;">%1$s</h1>
                            <p style="margin:0 0 6px;font-size:15px;line-height:1.55;">Hola %4$s,</p>
                            <p style="margin:0 0 24px;font-size:15px;line-height:1.55;color:#3b4659;">%5$s</p>
                            <table role="presentation" width="100%%" cellpadding="0" cellspacing="0">
                              <tr><td align="center" style="background:#eaf0ff;border-radius:14px;padding:22px 12px;">
                                <div style="font-size:13px;color:#5b667d;margin-bottom:6px;">Tu código de verificación</div>
                                <div style="font-family:'Courier New',Consolas,monospace;font-size:36px;font-weight:700;letter-spacing:6px;color:#2754e6;">%6$s</div>
                                <div style="font-size:13px;color:#5b667d;margin-top:8px;">Vence en %3$d minutos · solo se puede usar una vez</div>
                              </td></tr>
                            </table>
                            <p style="margin:24px 0 0;font-size:13px;line-height:1.55;color:#5b667d;">%7$s</p>
                            <p style="margin:12px 0 0;font-size:13px;line-height:1.55;color:#5b667d;">Nunca te pediremos este código por teléfono ni por mensaje.</p>
                          </td>
                        </tr>
                        <tr>
                          <td style="padding:18px 32px;background:#f8f9fc;border-top:1px solid #e1e6ef;font-size:12px;color:#8a93a6;text-align:center;">
                            Este es un mensaje automático de GestoPago Pagos. No respondas a este correo.
                          </td>
                        </tr>
                      </table>
                    </td></tr>
                  </table>
                </body>
                </html>
                """.formatted(
                HtmlUtils.htmlEscape(p.titulo()), codigo, minutos, HtmlUtils.htmlEscape(nombre == null ? "" : nombre),
                HtmlUtils.htmlEscape(p.explicacion()), codigoSeparado, HtmlUtils.htmlEscape(p.aviso()));
    }

    /** n***@gmail.com: el correo completo no se escribe en los logs. */
    static String enmascarar(String correo) {
        if (correo == null || !correo.contains("@")) {
            return "***";
        }
        int arroba = correo.indexOf('@');
        return correo.charAt(0) + "***" + correo.substring(arroba);
    }
}
