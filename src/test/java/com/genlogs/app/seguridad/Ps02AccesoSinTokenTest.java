package com.genlogs.app.seguridad;

import com.genlogs.app.config.CorsConfig;
import com.genlogs.app.config.PasswordEncoderConfig;
import com.genlogs.app.config.SecurityConfig;
import com.genlogs.app.config.WebStorageConfig;
import com.genlogs.app.controller.AuthController;
import com.genlogs.app.controller.ClienteController;
import com.genlogs.app.controller.CotizacionController;
import com.genlogs.app.controller.DashboardController;
import com.genlogs.app.controller.FacturacionController;
import com.genlogs.app.controller.HealthController;
import com.genlogs.app.controller.OrdenCompraController;
import com.genlogs.app.controller.UsuarioController;
import com.genlogs.app.security.CustomUserDetailsService;
import com.genlogs.app.security.JwtAuthenticationFilter;
import com.genlogs.app.security.JwtUtil;
import com.genlogs.app.service.AuthService;
import com.genlogs.app.service.ClienteService;
import com.genlogs.app.service.CotizacionDocumentoService;
import com.genlogs.app.service.CotizacionService;
import com.genlogs.app.service.DashboardService;
import com.genlogs.app.service.FacturacionDocumentoService;
import com.genlogs.app.service.FacturacionService;
import com.genlogs.app.service.OrdenCompraService;
import com.genlogs.app.service.UsuarioService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * PS-02 · Acceso sin token (prueba de seguridad).
 *
 * Objetivo: comprobar que las rutas protegidas de la API rechazan cualquier
 * petición que no traiga un token JWT válido en el encabezado Authorization,
 * sin ejecutar la lógica del negocio, y que las rutas públicas (login, health)
 * siguen abiertas.
 *
 * Se levanta la cadena de seguridad real (SecurityConfig + JwtAuthenticationFilter)
 * con los controladores reales; los servicios se simulan para no depender de Supabase.
 * Sin un "authenticationEntryPoint" configurado, Spring Security responde 403 Forbidden.
 */
@WebMvcTest(
        controllers = {
                CotizacionController.class, ClienteController.class, UsuarioController.class,
                FacturacionController.class, OrdenCompraController.class, DashboardController.class,
                AuthController.class, HealthController.class
        },
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = WebStorageConfig.class))
@Import({SecurityConfig.class, CorsConfig.class, PasswordEncoderConfig.class, JwtAuthenticationFilter.class})
@TestPropertySource(properties = "app.cors.allowed-origins=http://localhost:5173")
class Ps02AccesoSinTokenTest {

    @Autowired
    private MockMvc mockMvc;

    // Seguridad: se simulan para comprobar que nunca se llega a validar un usuario.
    @MockitoBean private JwtUtil jwtUtil;
    @MockitoBean private CustomUserDetailsService userDetailsService;

    // Servicios de los controladores: ninguno debe ejecutarse sin token.
    @MockitoBean private CotizacionService cotizacionService;
    @MockitoBean private CotizacionDocumentoService cotizacionDocumentoService;
    @MockitoBean private ClienteService clienteService;
    @MockitoBean private UsuarioService usuarioService;
    @MockitoBean private FacturacionService facturacionService;
    @MockitoBean private FacturacionDocumentoService facturacionDocumentoService;
    @MockitoBean private OrdenCompraService ordenCompraService;
    @MockitoBean private DashboardService dashboardService;
    @MockitoBean private AuthService authService;

    @AfterEach
    void ningunServicioDelNegocioSeEjecuto() {
        verifyNoInteractions(cotizacionService, cotizacionDocumentoService, clienteService, usuarioService,
                facturacionService, facturacionDocumentoService, ordenCompraService, dashboardService);
    }

    @ParameterizedTest(name = "GET {0} sin token → 403")
    @ValueSource(strings = {
            "/api/cotizaciones",
            "/api/cotizaciones/1",
            "/api/clientes",
            "/api/usuarios",
            "/api/facturacion",
            "/api/ordenes-compra",
            "/api/dashboard/indicadores"
    })
    @DisplayName("PS-02.1 Consultas protegidas sin encabezado Authorization")
    void consultaSinToken(String ruta) throws Exception {
        mockMvc.perform(get(ruta)).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("PS-02.2 Crear una cotización sin token")
    void crearCotizacionSinToken() throws Exception {
        mockMvc.perform(post("/api/cotizaciones").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("PS-02.3 Crear, editar, bloquear y eliminar usuarios sin token")
    void administrarUsuariosSinToken() throws Exception {
        mockMvc.perform(post("/api/usuarios").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/usuarios/1").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(patch("/api/usuarios/1/bloqueo").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/usuarios/1"))
                .andExpect(status().isForbidden());
    }

    @ParameterizedTest(name = "Authorization: \"{0}\" → 403")
    @ValueSource(strings = {"", "Bearer", "Bearer ", "Basic YWRtaW46YWRtaW4=", "token-sin-prefijo"})
    @DisplayName("PS-02.4 Encabezado Authorization vacío o sin el formato Bearer")
    void encabezadoSinFormatoBearer(String valor) throws Exception {
        mockMvc.perform(get("/api/cotizaciones").header("Authorization", valor))
                .andExpect(status().isForbidden());

        // Sin "Bearer <token>" el filtro ni siquiera intenta leer un usuario.
        verify(userDetailsService, never()).loadUserByUsername(anyString());
    }

    @Test
    @DisplayName("PS-02.5 Control: las rutas públicas responden sin token")
    void rutasPublicasSinToken() throws Exception {
        mockMvc.perform(get("/api/health")).andExpect(status().isOk());
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombreUsuario\":\"asesor1\",\"password\":\"clave\"}"))
                .andExpect(status().isOk());
    }
}
