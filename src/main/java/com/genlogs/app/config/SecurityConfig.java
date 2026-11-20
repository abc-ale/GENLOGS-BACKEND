package com.genlogs.app.config;

import com.genlogs.app.security.CustomUserDetailsService;
import com.genlogs.app.security.JwtAuthenticationFilter;
import com.genlogs.app.security.Roles;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfigurationSource;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    /** Catálogos maestros y tablas de apoyo (monedas, condiciones, estados, tipos…), CU-14. */
    private static final String[] MAESTROS = {
            "/api/catalogos/**", "/api/tipos-comprobante/**", "/api/tipos-documento/**",
            "/api/estados-cotizacion/**", "/api/estados-facturacion/**", "/api/estados-orden-compra/**" };

    /** Contactos de clientes (CU-05) y de proveedores (CU-08). */
    private static final String[] CONTACTOS = { "/api/terceros/**", "/api/contactos/**" };

    /** Proveedores (CU-08) y productos con su información técnica y proveedores (CU-09 a CU-11). */
    private static final String[] ALMACEN = {
            "/api/proveedores/**", "/api/productos/**", "/api/catalogo-productos/**" };

    /** Métodos que modifican datos; las lecturas (GET) se controlan aparte. */
    private static final HttpMethod[] ESCRITURA = {
            HttpMethod.POST, HttpMethod.PUT, HttpMethod.PATCH, HttpMethod.DELETE };

    private final CustomUserDetailsService userDetailsService;
    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final CorsConfigurationSource corsConfigurationSource;
    private final PasswordEncoder passwordEncoder;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .cors(cors -> cors.configurationSource(corsConfigurationSource))
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> {
                auth
                    .requestMatchers("/error").permitAll()
                    .requestMatchers("/api/health").permitAll()
                    .requestMatchers("/api/auth/**").permitAll()
                    // Catálogo web público (RF-16): solo la lectura de productos visibles en la web.
                    .requestMatchers(HttpMethod.GET, "/api/productos/web").permitAll()
                    .requestMatchers(HttpMethod.GET, "/uploads/**").permitAll();

                // ---- Permisos por rol (RF-31): los 3 trabajadores del negocio (sección 2.1.2). ----
                // Se evalúan en orden: lo específico va primero.
                String asesor = Roles.ASESOR_COMERCIAL;
                String jefe = Roles.JEFE_ADMINISTRATIVO;
                String contador = Roles.CONTADOR_GENERAL;

                // CU-03 Gestionar usuarios (incluye la lista de roles): Jefe Administrativo.
                auth.requestMatchers("/api/usuarios/**", "/api/roles/**").hasRole(jefe);

                // CU-14 Mantener catálogos maestros: todos leen, el Jefe Administrativo modifica.
                escritura(auth, MAESTROS, jefe);

                // CU-04 clientes · CU-05/CU-08 contactos de clientes y proveedores · CU-06 empresas mineras.
                escritura(auth, new String[] { "/api/clientes/**" }, Roles.ASESOR_Y_JEFE);
                escritura(auth, CONTACTOS, Roles.ASESOR_Y_JEFE);
                escritura(auth, new String[] { "/api/empresas-mineras/**" }, asesor);

                // CU-08 a CU-11 proveedores y productos: Jefe · CU-13 servicios: Asesor.
                escritura(auth, ALMACEN, jefe);
                escritura(auth, new String[] { "/api/servicios/**" }, asesor);
                auth.requestMatchers(HttpMethod.POST, "/api/archivos/**").hasAnyRole(Roles.ASESOR_Y_JEFE);

                // CU-19 Adjuntar archivos a la cotización (antes que la regla general de cotizaciones).
                escritura(auth, new String[] { "/api/cotizaciones/*/adjuntos/**" }, Roles.ASESOR_Y_JEFE);
                // CU-15, CU-16 y CU-18: registrar, cambiar estado y enviar la cotización.
                escritura(auth, new String[] { "/api/cotizaciones/**" }, asesor);

                // CU-20 a CU-22 órdenes de compra: registro, estado y archivo del cliente.
                escritura(auth, new String[] { "/api/ordenes-compra/**" }, jefe);

                // CU-23 y CU-24 facturación y pagos: Contador · CU-25 consulta: Contador y Jefe.
                escritura(auth, new String[] { "/api/facturacion/**" }, contador);
                auth.requestMatchers(HttpMethod.GET, "/api/facturacion/**").hasAnyRole(Roles.JEFE_Y_CONTADOR);

                // CU-26 Generar reportes y CU-27 Consultar indicadores del tablero.
                auth.requestMatchers("/api/reportes/**", "/api/dashboard/**").hasAnyRole(Roles.JEFE_Y_CONTADOR);

                // Consultas generales y catálogos que usan los formularios: los 3 roles.
                auth.requestMatchers(HttpMethod.GET, "/api/**").hasAnyRole(Roles.TODOS);

                // Cualquier otra operación no prevista queda solo para el Jefe Administrativo.
                auth.anyRequest().hasRole(jefe);
            })
            .authenticationProvider(authenticationProvider())
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /** Restringe POST, PUT, PATCH y DELETE de las rutas a los roles indicados. */
    private static void escritura(
            AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizationManagerRequestMatcherRegistry auth,
            String[] rutas, String... roles) {
        for (HttpMethod metodo : ESCRITURA) {
            auth.requestMatchers(metodo, rutas).hasAnyRole(roles);
        }
    }

    @Bean
    public DaoAuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return provider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }
}