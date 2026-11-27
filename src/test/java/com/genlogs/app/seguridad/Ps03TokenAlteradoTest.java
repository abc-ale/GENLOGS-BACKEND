package com.genlogs.app.seguridad;

import com.genlogs.app.config.CorsConfig;
import com.genlogs.app.config.PasswordEncoderConfig;
import com.genlogs.app.config.SecurityConfig;
import com.genlogs.app.config.WebStorageConfig;
import com.genlogs.app.controller.CotizacionController;
import com.genlogs.app.security.CustomUserDetailsService;
import com.genlogs.app.security.JwtAuthenticationFilter;
import com.genlogs.app.security.JwtUtil;
import com.genlogs.app.service.CotizacionDocumentoService;
import com.genlogs.app.service.CotizacionService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Date;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * PS-03 · Token alterado o inválido (prueba de seguridad).
 *
 * Objetivo: comprobar que la API solo acepta tokens JWT firmados con la clave
 * del servidor y sin modificaciones. Un token con el contenido cambiado, con la
 * firma alterada, firmado con otra clave, sin firma ("alg": "none"), mal formado,
 * de recuperación de contraseña o de un usuario que ya no existe se rechaza
 * (403) sin ejecutar la lógica del negocio ni provocar un error 500.
 *
 * Se usa el JwtUtil real con una clave de prueba; solo se simulan la carga
 * del usuario y los servicios, para no depender de Supabase.
 */
@WebMvcTest(
        controllers = CotizacionController.class,
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = WebStorageConfig.class))
@Import({SecurityConfig.class, CorsConfig.class, PasswordEncoderConfig.class,
        JwtAuthenticationFilter.class, JwtUtil.class})
@TestPropertySource(properties = {
        "app.cors.allowed-origins=http://localhost:5173",
        "jwt.secret=" + Ps03TokenAlteradoTest.CLAVE_SERVIDOR,
        "jwt.expiration=3600000"
})
class Ps03TokenAlteradoTest {

    static final String CLAVE_SERVIDOR = "clave-de-prueba-del-servidor-genlogs-ps03-0123456789";
    private static final String CLAVE_ATACANTE = "clave-que-no-es-del-servidor-genlogs-ps03-9876543210";
    private static final String RUTA = "/api/cotizaciones";

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtUtil jwtUtil;

    @MockitoBean private CustomUserDetailsService userDetailsService;
    @MockitoBean private CotizacionService cotizacionService;
    @MockitoBean private CotizacionDocumentoService cotizacionDocumentoService;

    private static UserDetails usuario(String nombre, String rol) {
        return User.builder().username(nombre).password("$2a$10$hashDePrueba").roles(rol).build();
    }

    @BeforeEach
    void preparar() {
        when(userDetailsService.loadUserByUsername("asesor1")).thenReturn(usuario("asesor1", "ASESOR COMERCIAL"));
        when(userDetailsService.loadUserByUsername("admin")).thenReturn(usuario("admin", "ADMINISTRADOR"));
    }

    private void rechazado(String token) throws Exception {
        mockMvc.perform(get(RUTA).header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
        verifyNoInteractions(cotizacionService, cotizacionDocumentoService);
    }

    private static String b64(String json) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }

    private static String decodificar(String parte) {
        return new String(Base64.getUrlDecoder().decode(parte), StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("PS-03.0 Control: un token válido del servidor sí da acceso (200)")
    void tokenValido() throws Exception {
        String token = jwtUtil.generarToken(usuario("asesor1", "ASESOR COMERCIAL"));

        mockMvc.perform(get(RUTA).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        verify(cotizacionService).listar(any(), any(), any(), any(Pageable.class));
    }

    @Test
    @DisplayName("PS-03.1 Contenido modificado: se cambia el usuario a 'admin' sin volver a firmar")
    void contenidoModificado() throws Exception {
        String[] partes = jwtUtil.generarToken(usuario("asesor1", "ASESOR COMERCIAL")).split("\\.");
        String payloadAlterado = b64(decodificar(partes[1]).replace("\"asesor1\"", "\"admin\""));

        rechazado(partes[0] + "." + payloadAlterado + "." + partes[2]);
    }

    @Test
    @DisplayName("PS-03.2 Firma alterada: se cambia un carácter de la firma")
    void firmaAlterada() throws Exception {
        String token = jwtUtil.generarToken(usuario("asesor1", "ASESOR COMERCIAL"));
        int i = token.lastIndexOf('.') + 5;
        char reemplazo = token.charAt(i) == 'A' ? 'B' : 'A';

        rechazado(token.substring(0, i) + reemplazo + token.substring(i + 1));
    }

    @Test
    @DisplayName("PS-03.3 Token firmado con una clave que no es la del servidor")
    void firmadoConOtraClave() throws Exception {
        String token = Jwts.builder()
                .subject("admin")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 3_600_000))
                .signWith(Keys.hmacShaKeyFor(CLAVE_ATACANTE.getBytes(StandardCharsets.UTF_8)))
                .compact();

        rechazado(token);
    }

    @Test
    @DisplayName("PS-03.4 Token sin firma (\"alg\": \"none\")")
    void tokenSinFirma() throws Exception {
        long exp = System.currentTimeMillis() / 1000 + 3600;
        String token = b64("{\"alg\":\"none\"}") + "." + b64("{\"sub\":\"admin\",\"exp\":" + exp + "}") + ".";

        rechazado(token);
    }

    @Test
    @DisplayName("PS-03.5 Tokens mal formados: no provocan error 500")
    void tokensMalFormados() throws Exception {
        for (String token : new String[] {"abc", "abc.def.ghi", "...", "eyJhbGciOiJIUzI1NiJ9.%%%.xyz"}) {
            rechazado(token);
        }
    }

    @Test
    @DisplayName("PS-03.6 Un token de recuperación de contraseña no sirve para iniciar sesión")
    void tokenDeRecuperacion() throws Exception {
        rechazado(jwtUtil.generarTokenReset("asesor1", "$2a$10$hashDePrueba"));
    }

    @Test
    @DisplayName("PS-03.7 Token válido de un usuario que ya no existe")
    void usuarioEliminado() throws Exception {
        when(userDetailsService.loadUserByUsername("eliminado"))
                .thenThrow(new UsernameNotFoundException("Usuario no encontrado: eliminado"));

        rechazado(jwtUtil.generarToken(usuario("eliminado", "ASESOR COMERCIAL")));
    }
}
