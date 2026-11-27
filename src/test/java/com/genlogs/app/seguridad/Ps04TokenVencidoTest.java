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
import io.jsonwebtoken.Claims;
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
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * PS-04 · Token vencido (prueba de seguridad).
 *
 * Objetivo: comprobar que un token JWT firmado por el servidor deja de servir
 * apenas pasa su fecha de expiración (claim "exp"), aunque la firma sea válida,
 * y que no se puede "revivir" cambiando esa fecha. También se comprueba que la
 * sesión dura lo configurado en jwt.expiration (24 horas en producción).
 *
 * Se usa el JwtUtil real con la misma duración que application.properties;
 * solo se simulan la carga del usuario y los servicios, para no depender de Supabase.
 */
@WebMvcTest(
        controllers = CotizacionController.class,
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = WebStorageConfig.class))
@Import({SecurityConfig.class, CorsConfig.class, PasswordEncoderConfig.class,
        JwtAuthenticationFilter.class, JwtUtil.class})
@TestPropertySource(properties = {
        "app.cors.allowed-origins=http://localhost:5173",
        "jwt.secret=" + Ps04TokenVencidoTest.CLAVE_SERVIDOR,
        "jwt.expiration=" + Ps04TokenVencidoTest.DURACION_SESION_MS
})
class Ps04TokenVencidoTest {

    static final String CLAVE_SERVIDOR = "clave-de-prueba-del-servidor-genlogs-ps04-0123456789";
    /** Igual que jwt.expiration en application.properties: 24 horas. */
    static final long DURACION_SESION_MS = 86_400_000L;
    private static final String RUTA = "/api/cotizaciones";

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtUtil jwtUtil;

    @MockitoBean private CustomUserDetailsService userDetailsService;
    @MockitoBean private CotizacionService cotizacionService;
    @MockitoBean private CotizacionDocumentoService cotizacionDocumentoService;

    @BeforeEach
    void preparar() {
        UserDetails asesor = User.builder().username("asesor1").password("$2a$10$hashDePrueba")
                .roles("ASESOR COMERCIAL").build();
        when(userDetailsService.loadUserByUsername("asesor1")).thenReturn(asesor);
    }

    private static SecretKey claveServidor() {
        return Keys.hmacShaKeyFor(CLAVE_SERVIDOR.getBytes(StandardCharsets.UTF_8));
    }

    /** Token firmado con la clave del servidor, emitido y vencido en las fechas indicadas. */
    private static String tokenFirmado(long emitidoHaceMs, long venceEnMs) {
        long ahora = System.currentTimeMillis();
        return Jwts.builder()
                .subject("asesor1")
                .issuedAt(new Date(ahora - emitidoHaceMs))
                .expiration(new Date(ahora + venceEnMs))
                .signWith(claveServidor())
                .compact();
    }

    private void rechazado(String token) throws Exception {
        mockMvc.perform(get(RUTA).header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
        verifyNoInteractions(cotizacionService, cotizacionDocumentoService);
    }

    private void aceptado(String token) throws Exception {
        mockMvc.perform(get(RUTA).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        verify(cotizacionService).listar(any(), any(), any(), any(Pageable.class));
    }

    @Test
    @DisplayName("PS-04.0 Control: un token recién emitido da acceso (200)")
    void tokenVigente() throws Exception {
        aceptado(jwtUtil.generarToken(User.builder().username("asesor1")
                .password("$2a$10$hashDePrueba").roles("ASESOR COMERCIAL").build()));
    }

    @Test
    @DisplayName("PS-04.1 Token de una sesión de ayer (venció hace 1 hora): 403")
    void tokenVencidoHaceUnaHora() throws Exception {
        rechazado(tokenFirmado(DURACION_SESION_MS + 3_600_000L, -3_600_000L));
    }

    @Test
    @DisplayName("PS-04.2 Token que venció hace 2 segundos: 403 (no hay margen de tolerancia)")
    void tokenRecienVencido() throws Exception {
        rechazado(tokenFirmado(DURACION_SESION_MS, -2_000L));
    }

    @Test
    @DisplayName("PS-04.3 Control: un token a 1 minuto de vencer todavía da acceso (200)")
    void tokenPorVencer() throws Exception {
        aceptado(tokenFirmado(DURACION_SESION_MS - 60_000L, 60_000L));
    }

    @Test
    @DisplayName("PS-04.4 Token vencido al que se le cambia la fecha de expiración sin volver a firmar: 403")
    void fechaDeExpiracionAlterada() throws Exception {
        String[] partes = tokenFirmado(DURACION_SESION_MS + 3_600_000L, -3_600_000L).split("\\.");
        String payload = new String(Base64.getUrlDecoder().decode(partes[1]), StandardCharsets.UTF_8);
        long manana = System.currentTimeMillis() / 1000 + 86_400;
        String payloadAlterado = payload.replaceAll("\"exp\":\\d+", "\"exp\":" + manana);
        assertThat(payloadAlterado).isNotEqualTo(payload);

        rechazado(partes[0] + "."
                + Base64.getUrlEncoder().withoutPadding()
                        .encodeToString(payloadAlterado.getBytes(StandardCharsets.UTF_8))
                + "." + partes[2]);
    }

    @Test
    @DisplayName("PS-04.5 La sesión que emite el login dura 24 horas")
    void duracionDeLaSesion() {
        String token = jwtUtil.generarToken(User.builder().username("asesor1")
                .password("$2a$10$hashDePrueba").roles("ASESOR COMERCIAL").build());

        Claims claims = Jwts.parser().verifyWith(claveServidor()).build()
                .parseSignedClaims(token).getPayload();

        assertThat(claims.getExpiration()).isNotNull();
        long duracion = claims.getExpiration().getTime() - claims.getIssuedAt().getTime();
        // iat y exp se guardan en segundos: se admite 1 segundo de redondeo.
        assertThat(duracion).isBetween(DURACION_SESION_MS - 1_000L, DURACION_SESION_MS + 1_000L);
    }
}
