package com.proyecto.servicios.seguridad;

import com.proyecto.servicios.enums.Catalogos.PropositoCodigo;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;

import java.io.ByteArrayOutputStream;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("EnvioCorreo - envío del código por SMTP (HTML) o en consola")
class EnvioCorreoTest {

    @SuppressWarnings("unchecked")
    private static ObjectProvider<JavaMailSender> proveedor(JavaMailSender sender) {
        ObjectProvider<JavaMailSender> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(sender);
        when(provider.getObject()).thenReturn(sender);
        return provider;
    }

    private static JavaMailSender senderConMensajes() {
        JavaMailSender sender = mock(JavaMailSender.class);
        when(sender.createMimeMessage()).thenAnswer(i -> new MimeMessage(Session.getInstance(new Properties())));
        return sender;
    }

    @Test
    @DisplayName("Sin cuenta configurada: modo desarrollo, no envía y responde true")
    void sinConfigurar() {
        JavaMailSender sender = mock(JavaMailSender.class);
        EnvioCorreo envio = new EnvioCorreo(proveedor(sender), " ");

        assertThat(envio.configurado()).isFalse();
        assertThat(envio.enviarCodigo("noe@correo.com", "Noe", "123456", 15, PropositoCodigo.CONFIRMAR_CORREO)).isTrue();
        assertThat(new EnvioCorreo(proveedor(null), null).configurado()).isFalse();
    }

    @Test
    @DisplayName("Con cuenta configurada: envía HTML + texto, con remitente, asunto y el código")
    void configurado() throws Exception {
        JavaMailSender sender = senderConMensajes();
        EnvioCorreo envio = new EnvioCorreo(proveedor(sender), "plataforma@gmail.com");

        assertThat(envio.enviarCodigo("noe@correo.com", "Noe", "123456", 15, PropositoCodigo.INICIO_SESION)).isTrue();

        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(sender).send(captor.capture());
        MimeMessage mensaje = captor.getValue();
        assertThat(mensaje.getAllRecipients()[0]).hasToString("noe@correo.com");
        assertThat(mensaje.getFrom()[0].toString()).contains("plataforma@gmail.com").contains("GestoPago");
        assertThat(mensaje.getSubject()).isEqualTo("Tu código para iniciar sesión en GestoPago");
        ByteArrayOutputStream contenido = new ByteArrayOutputStream();
        mensaje.writeTo(contenido);
        assertThat(contenido.toString()).contains("text/html").contains("text/plain");
    }

    @ParameterizedTest
    @EnumSource(PropositoCodigo.class)
    @DisplayName("Cada propósito tiene su asunto y el HTML/texto incluyen el código, la vigencia y el nombre escapado")
    void plantillas(PropositoCodigo proposito) {
        EnvioCorreo.Plantilla p = EnvioCorreo.Plantilla.de(proposito);
        String html = EnvioCorreo.html(p, "Noé <b>", "654321", 15);
        String texto = EnvioCorreo.texto(p, "Noé", "654321", 15);

        assertThat(p.asunto()).isNotBlank();
        assertThat(html).contains("654&nbsp;321").contains("15 minutos").doesNotContain("<b>").contains("&lt;b&gt;");
        assertThat(texto).contains("654321").contains("15 minutos").contains(p.explicacion());
    }

    @Test
    @DisplayName("Si el SMTP falla responde false")
    void falla() {
        JavaMailSender sender = senderConMensajes();
        doThrow(new MailSendException("sin conexion")).when(sender).send(any(MimeMessage.class));

        assertThat(new EnvioCorreo(proveedor(sender), "plataforma@gmail.com")
                .enviarCodigo("noe@correo.com", "Noe", "123456", 15, PropositoCodigo.OPERACION)).isFalse();
    }

    @Test
    @DisplayName("Enmascara el correo en los logs")
    void enmascarar() {
        assertThat(EnvioCorreo.enmascarar("noe.ch@gmail.com")).isEqualTo("n***@gmail.com");
        assertThat(EnvioCorreo.enmascarar("sinarroba")).isEqualTo("***");
        assertThat(EnvioCorreo.enmascarar(null)).isEqualTo("***");
    }
}
