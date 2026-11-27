package com.genlogs.app.seguridad;

import com.genlogs.app.controller.AuthController;
import com.genlogs.app.exception.GlobalExceptionHandler;
import com.genlogs.app.model.Rol;
import com.genlogs.app.model.Usuario;
import com.genlogs.app.repository.UsuarioRepository;
import com.genlogs.app.security.JwtUtil;
import com.genlogs.app.service.AuthService;
import com.genlogs.app.service.MailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * PS-01 · Login con credenciales incorrectas (prueba de seguridad).
 *
 * Objetivo: comprobar que POST /api/auth/login rechaza credenciales
 * incorrectas sin entregar un token y sin revelar si el usuario existe
 * (mismo mensaje para "usuario no existe" y "contraseña incorrecta"),
 * y que a la quinta contraseña incorrecta seguida el usuario se bloquea (RF-28).
 *
 * Se prueba el controlador real con el AuthService real; solo se simulan
 * la base de datos (UsuarioRepository), el AuthenticationManager y el JWT,
 * así la prueba no depende de Supabase.
 */
class Ps01LoginCredencialesInvalidasTest {

    private static final String URL_LOGIN = "/api/auth/login";
    private static final String MENSAJE_GENERICO = "Usuario o contraseña incorrectos";

    private AuthenticationManager authenticationManager;
    private UsuarioRepository usuarioRepository;
    private JwtUtil jwtUtil;
    private MockMvc mockMvc;

    @BeforeEach
    void preparar() {
        authenticationManager = mock(AuthenticationManager.class);
        usuarioRepository = mock(UsuarioRepository.class);
        jwtUtil = mock(JwtUtil.class);

        AuthService authService = new AuthService(authenticationManager, usuarioRepository, jwtUtil,
                mock(PasswordEncoder.class), mock(MailService.class));
        ReflectionTestUtils.setField(authService, "maxIntentosFallidos", 5);
        ReflectionTestUtils.setField(authService, "frontendUrl", "http://localhost:5173");

        mockMvc = MockMvcBuilders.standaloneSetup(new AuthController(authService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        // Cualquier combinación usuario/contraseña de estas pruebas es incorrecta.
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenThrow(new BadCredentialsException("Bad credentials"));
    }

    private static String cuerpo(String usuario, String password) {
        return "{\"nombreUsuario\":\"" + usuario + "\",\"password\":\"" + password + "\"}";
    }

    private static Usuario usuarioExistente(String nombreUsuario, int intentosPrevios) {
        Rol rol = new Rol();
        rol.setNombreRol("Asesor Comercial");
        Usuario usuario = new Usuario();
        usuario.setNombreUsuario(nombreUsuario);
        usuario.setPasswordHash("$2a$10$hashDePrueba");
        usuario.setRol(rol);
        usuario.setBloqueado(false);
        usuario.setIntentosFallidos((short) intentosPrevios);
        return usuario;
    }

    @Test
    @DisplayName("PS-01.1 Contraseña incorrecta: 400, mensaje genérico y sin token")
    void contrasenaIncorrecta() throws Exception {
        when(usuarioRepository.findByNombreUsuario("asesor1"))
                .thenReturn(Optional.of(usuarioExistente("asesor1", 0)));

        mockMvc.perform(post(URL_LOGIN).contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo("asesor1", "claveIncorrecta")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(MENSAJE_GENERICO))
                .andExpect(jsonPath("$.token").doesNotExist());

        verify(jwtUtil, never()).generarToken(any());
    }

    @Test
    @DisplayName("PS-01.2 Usuario inexistente: 400, mismo mensaje genérico y sin token")
    void usuarioInexistente() throws Exception {
        when(usuarioRepository.findByNombreUsuario("noexiste")).thenReturn(Optional.empty());

        mockMvc.perform(post(URL_LOGIN).contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo("noexiste", "cualquiera")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(MENSAJE_GENERICO))
                .andExpect(jsonPath("$.token").doesNotExist());

        verify(jwtUtil, never()).generarToken(any());
        verify(usuarioRepository, never()).save(any());
    }

    @Test
    @DisplayName("PS-01.3 La respuesta no revela si el usuario existe")
    void mismaRespuestaExistaONoElUsuario() throws Exception {
        when(usuarioRepository.findByNombreUsuario("asesor1"))
                .thenReturn(Optional.of(usuarioExistente("asesor1", 0)));
        when(usuarioRepository.findByNombreUsuario("noexiste")).thenReturn(Optional.empty());

        MvcResult existe = mockMvc.perform(post(URL_LOGIN).contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo("asesor1", "claveIncorrecta"))).andReturn();
        MvcResult noExiste = mockMvc.perform(post(URL_LOGIN).contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo("noexiste", "claveIncorrecta"))).andReturn();

        assertThat(existe.getResponse().getStatus()).isEqualTo(noExiste.getResponse().getStatus());
        // El cuerpo solo difiere en el timestamp; se compara sin él.
        String sinFecha = "\"timestamp\":\"[^\"]*\"";
        assertThat(existe.getResponse().getContentAsString().replaceAll(sinFecha, ""))
                .isEqualTo(noExiste.getResponse().getContentAsString().replaceAll(sinFecha, ""));
    }

    @Test
    @DisplayName("PS-01.4 Cada intento fallido se registra en el usuario")
    void registraIntentoFallido() throws Exception {
        Usuario usuario = usuarioExistente("asesor1", 0);
        when(usuarioRepository.findByNombreUsuario("asesor1")).thenReturn(Optional.of(usuario));

        mockMvc.perform(post(URL_LOGIN).contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo("asesor1", "claveIncorrecta")));

        assertThat(usuario.getIntentosFallidos()).isEqualTo((short) 1);
        assertThat(usuario.getBloqueado()).isFalse();
        verify(usuarioRepository).save(usuario);
    }

    @Test
    @DisplayName("PS-01.5 A la quinta contraseña incorrecta seguida el usuario queda bloqueado (RF-28)")
    void bloqueaAlQuintoIntento() throws Exception {
        Usuario usuario = usuarioExistente("asesor1", 4);
        when(usuarioRepository.findByNombreUsuario("asesor1")).thenReturn(Optional.of(usuario));

        mockMvc.perform(post(URL_LOGIN).contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo("asesor1", "claveIncorrecta")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(MENSAJE_GENERICO));

        assertThat(usuario.getIntentosFallidos()).isEqualTo((short) 5);
        assertThat(usuario.getBloqueado()).isTrue();
    }

    @Test
    @DisplayName("PS-01.6 Usuario o contraseña vacíos: 400 sin llegar a autenticar")
    void camposVacios() throws Exception {
        mockMvc.perform(post(URL_LOGIN).contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo("", "")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.token").doesNotExist());

        verify(authenticationManager, never()).authenticate(any());
    }
}
