package com.proyecto.servicios.config;

import com.proyecto.servicios.controller.AuthController;
import com.proyecto.servicios.controller.ClienteController;
import com.proyecto.servicios.controller.CuentaController;
import com.proyecto.servicios.controller.TransaccionController;
import com.proyecto.servicios.handler.SendTxComando;
import com.proyecto.servicios.handler.SendTxHandler;
import com.proyecto.servicios.dto.response.TransaccionResponse;
import com.proyecto.servicios.dto.response.ApiResponse;
import com.proyecto.servicios.dto.response.ClienteResponse;
import com.proyecto.servicios.dto.response.LoginResponse;
import com.proyecto.servicios.dto.response.PaginaResponse;
import com.proyecto.servicios.enums.Catalogos.MetodoA2F;
import com.proyecto.servicios.enums.Catalogos.PasoLogin;
import com.proyecto.servicios.seguridad.TokenJwt;
import com.proyecto.servicios.service.AuthService;
import com.proyecto.servicios.service.ClienteService;
import com.proyecto.servicios.service.CuentaService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Cadena de seguridad real (sin base de datos): que rutas son publicas y que tokens se aceptan.
 */
@WebMvcTest(controllers = {ClienteController.class, CuentaController.class, AuthController.class, TransaccionController.class})
@Import({SeguridadConfig.class, TokenJwt.class})
@DisplayName("SeguridadConfig - rutas protegidas y tokens")
class SeguridadConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TokenJwt tokenJwt;

    @MockBean
    private ClienteService clienteService;

    @MockBean
    private CuentaService cuentaService;

    @MockBean
    private AuthService authService;

    @MockBean
    private SendTxHandler sendTxHandler;

    private void listarDevuelvePaginaVacia() {
        when(clienteService.listar()).thenReturn(ApiResponse.exito(HttpStatus.OK, "ok",
                PaginaResponse.of(new PageImpl<ClienteResponse>(List.of()), r -> r)));
    }

    @Test
    @DisplayName("Sin token, un endpoint protegido responde 401 con el formato {codigo, mensaje}")
    void sinToken_401() throws Exception {
        mockMvc.perform(get("/api/v1/clientes"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value(1))
                .andExpect(jsonPath("$.mensaje").value(SeguridadConfig.MSG_SIN_SESION));
        mockMvc.perform(post("/api/v1/cuentas/consulta").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Con token de acceso válido, el endpoint protegido responde")
    void tokenAcceso_200() throws Exception {
        listarDevuelvePaginaVacia();

        mockMvc.perform(get("/api/v1/clientes").header("Authorization", "Bearer " + tokenJwt.emitirAcceso(1, "juanperez95")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.codigo").value(0));
    }

    @Test
    @DisplayName("El token temporal del paso A2F no abre endpoints protegidos")
    void tokenTemporal_401() throws Exception {
        mockMvc.perform(get("/api/v1/clientes").header("Authorization", "Bearer " + tokenJwt.emitirA2F(1, "juanperez95")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Un token alterado se rechaza")
    void tokenAlterado_401() throws Exception {
        String token = tokenJwt.emitirAcceso(1, "juanperez95");
        String alterado = token.substring(0, token.length() - 2) + (token.endsWith("A") ? "BB" : "AA");

        mockMvc.perform(get("/api/v1/clientes").header("Authorization", "Bearer " + alterado))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("El inicio de sesión es público")
    void login_publico() throws Exception {
        when(authService.iniciarSesion(any())).thenReturn(ApiResponse.exito(HttpStatus.OK, "ok",
                new LoginResponse(PasoLogin.CONFIGURAR_A2F, MetodoA2F.TOTP, "t", 300L)));

        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usuario\":\"juanperez95\",\"contrasena\":\"Segura#2026\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.datos.siguientePaso").value("CONFIGURAR_A2F"));
    }

    @Test
    @DisplayName("El alta de clientes es pública: llega a la validación sin token")
    void alta_publica() throws Exception {
        mockMvc.perform(post("/api/v1/clientes").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value(1));
    }

    private static final String PAGO = "{\"numeroCuenta\":\"4152313748382907\",\"idServicio\":133,\"idProducto\":582,"
            + "\"monto\":10.00,\"telefono\":\"5555555551\",\"2fa\":\"123456\"}";

    @Test
    @DisplayName("POST /api/v1/transaccion sin token: 401 y no llega al handler")
    void transaccion_sinToken_401() throws Exception {
        mockMvc.perform(post("/api/v1/transaccion").contentType(MediaType.APPLICATION_JSON).content(PAGO))
                .andExpect(status().isUnauthorized());
        org.mockito.Mockito.verifyNoInteractions(sendTxHandler);
    }

    @Test
    @DisplayName("POST /api/v1/transaccion: el cliente sale del token y acepta el campo \"2fa\"")
    void transaccion_clienteDelToken() throws Exception {
        when(sendTxHandler.handle(any())).thenReturn(ApiResponse.exito(HttpStatus.OK, "Pago exitoso.", new TransaccionResponse()));

        mockMvc.perform(post("/api/v1/transaccion").contentType(MediaType.APPLICATION_JSON).content(PAGO)
                        .header("Authorization", "Bearer " + tokenJwt.emitirAcceso(7, "juanperez95")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mensaje").value("Pago exitoso."));

        ArgumentCaptor<SendTxComando> comando = ArgumentCaptor.forClass(SendTxComando.class);
        verify(sendTxHandler).handle(comando.capture());
        assertThat(comando.getValue().clienteId()).isEqualTo(7);
        assertThat(comando.getValue().request().getCodigoA2F()).isEqualTo("123456");
    }
}
