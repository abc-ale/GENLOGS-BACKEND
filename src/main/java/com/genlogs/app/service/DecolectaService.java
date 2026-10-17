package com.genlogs.app.service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.genlogs.app.dto.ConsultaDocumentoResponse;
import com.genlogs.app.exception.BusinessException;
import com.genlogs.app.model.ConsultaDocumentoCache;
import com.genlogs.app.repository.ConsultaDocumentoCacheRepository;

/**
 * Autocompletado de RUC/DNI contra la API de Decolecta (RF-08).
 *
 * Si DECOLECTA_API_KEY no esta configurada, responde con datos simulados
 * en vez de fallar, para no bloquear el trabajo del frontend.
 */
@Service
public class DecolectaService {

    /** Los RUC cambian de estado/dirección: se vuelven a consultar pasado este plazo. El DNI no caduca. */
    private static final long DIAS_VIGENCIA_RUC = 30;

    private final ConsultaDocumentoCacheRepository cacheRepository;

    public DecolectaService(ConsultaDocumentoCacheRepository cacheRepository) {
        this.cacheRepository = cacheRepository;
    }

    private static final Logger log = LoggerFactory.getLogger(DecolectaService.class);

    // Proveedor: Decolecta (https://decolecta.com). Variable de entorno: DECOLECTA_API_KEY.
    @Value("${decolecta.api-key:${DECOLECTA_API_KEY:}}")
    private String apiKey;

    @Value("${decolecta.base-url:https://api.decolecta.com/v1}")
    private String baseUrl;

    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private final ObjectMapper mapper = new ObjectMapper();

    public ConsultaDocumentoResponse consultarPorNumero(String numero) {
        if (numero == null) {
            throw new BusinessException("Debe indicar el número de documento");
        }
        String limpio = numero.trim();

        String tipo = switch (limpio.length()) {
            case 11 -> "RUC";
            case 8 -> "DNI";
            default -> throw new BusinessException(
                    "El número debe tener 11 dígitos (RUC) u 8 dígitos (DNI)");
        };
        if (!limpio.matches("\\d+")) {
            throw new BusinessException("El número de documento debe ser numérico");
        }

        // 1) Primero la base de datos propia: si ya se consultó, no se gasta la API.
        ConsultaDocumentoResponse enCache = buscarEnCache(limpio, tipo);
        if (enCache != null) {
            return enCache;
        }

        if (apiKey == null || apiKey.isBlank()) {
            log.warn("DECOLECTA_API_KEY no configurada. Devolviendo datos simulados para {} {}", tipo, limpio);
            return simulado(limpio, tipo);
        }

        // 2) Si no estaba, se consulta Decolecta y se guarda el resultado.
        ConsultaDocumentoResponse r = tipo.equals("RUC") ? consultarRuc(limpio) : consultarDni(limpio);
        guardarEnCache(r);
        return r;
    }

    private ConsultaDocumentoResponse buscarEnCache(String numero, String tipo) {
        try {
            return cacheRepository.findByNumeroDocumento(numero)
                    .filter(c -> !"RUC".equals(tipo)
                            || c.getFechaConsulta().isAfter(LocalDateTime.now().minusDays(DIAS_VIGENCIA_RUC)))
                    .map(c -> ConsultaDocumentoResponse.builder()
                            .numeroDocumento(c.getNumeroDocumento())
                            .tipoDocumento(c.getTipoDocumento())
                            .razonSocial(c.getRazonSocial())
                            .direccion(c.getDireccion())
                            .nombres(c.getNombres())
                            .apellidoPaterno(c.getApellidoPaterno())
                            .apellidoMaterno(c.getApellidoMaterno())
                            .simulado(false)
                            .build())
                    .orElse(null);
        } catch (Exception e) {
            // Si la cache falla, se sigue consultando a la API en vez de romper la búsqueda.
            log.warn("No se pudo leer la cache de documentos: {}", e.getMessage());
            return null;
        }
    }

    private void guardarEnCache(ConsultaDocumentoResponse r) {
        try {
            ConsultaDocumentoCache c = cacheRepository.findByNumeroDocumento(r.getNumeroDocumento())
                    .orElseGet(ConsultaDocumentoCache::new);
            c.setNumeroDocumento(r.getNumeroDocumento());
            c.setTipoDocumento(r.getTipoDocumento());
            c.setRazonSocial(r.getRazonSocial());
            c.setDireccion(r.getDireccion());
            c.setNombres(r.getNombres());
            c.setApellidoPaterno(r.getApellidoPaterno());
            c.setApellidoMaterno(r.getApellidoMaterno());
            c.setFechaConsulta(LocalDateTime.now());
            cacheRepository.save(c);
        } catch (Exception e) {
            log.warn("No se pudo guardar en la cache de documentos: {}", e.getMessage());
        }
    }

    private ConsultaDocumentoResponse consultarRuc(String ruc) {
        JsonNode data = consultar("/sunat/ruc?numero=" + ruc);

        String razonSocial = primero(data, "razon_social", "nombre_o_razon_social", "nombre");
        String direccion = primero(data, "direccion", "domicilio_fiscal");

        return ConsultaDocumentoResponse.builder()
                .numeroDocumento(ruc)
                .tipoDocumento("RUC")
                .razonSocial(razonSocial)
                .direccion(direccion)
                .simulado(false)
                .build();
    }

    private ConsultaDocumentoResponse consultarDni(String dni) {
        JsonNode data = consultar("/reniec/dni?numero=" + dni);

        String nombre = primero(data, "first_name", "nombres");
        String apPaterno = primero(data, "first_last_name", "apellido_paterno");
        String apMaterno = primero(data, "second_last_name", "apellido_materno");
        String nombres = primero(data, "full_name", "nombre_completo");
        if (nombres == null || nombres.isBlank()) {
            nombres = String.join(" ",
                    valorOVacio(nombre), valorOVacio(apPaterno), valorOVacio(apMaterno)).trim();
        }

        return ConsultaDocumentoResponse.builder()
                .numeroDocumento(dni)
                .tipoDocumento("DNI")
                .razonSocial(nombres)
                .nombres(nombre)
                .apellidoPaterno(apPaterno)
                .apellidoMaterno(apMaterno)
                .direccion(null)
                .simulado(false)
                .build();
    }

    private JsonNode consultar(String path) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + path))
                    .timeout(Duration.ofSeconds(15))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Accept", "application/json")
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 300) {
                log.error("Decolecta respondió {}: {}", response.statusCode(), response.body());
                throw new BusinessException("No se pudo consultar el documento en Decolecta");
            }

            JsonNode body = mapper.readTree(response.body());
            JsonNode data = body.has("data") ? body.get("data") : body;

            if (data == null || data.isMissingNode() || data.isNull()) {
                throw new BusinessException("No se encontraron datos para ese documento");
            }
            return data;
        } catch (BusinessException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException("La consulta a Decolecta fue interrumpida");
        } catch (Exception e) {
            log.error("Error consultando Decolecta", e);
            throw new BusinessException("No se pudo consultar el documento en Decolecta");
        }
    }

    private String primero(JsonNode data, String... campos) {
        for (String campo : campos) {
            if (data.hasNonNull(campo) && !data.get(campo).asText().isBlank()) {
                return data.get(campo).asText();
            }
        }
        return null;
    }

    private String valorOVacio(String valor) {
        return valor == null ? "" : valor;
    }

    private ConsultaDocumentoResponse simulado(String numero, String tipo) {
        String razonSocial = tipo.equals("RUC")
                ? "Empresa de Prueba S.A.C. (simulado)"
                : "Nombre Apellido de Prueba (simulado)";
        String direccion = tipo.equals("RUC") ? "Av. Simulación 123, Lima (simulado)" : null;

        return ConsultaDocumentoResponse.builder()
                .numeroDocumento(numero)
                .tipoDocumento(tipo)
                .razonSocial(razonSocial)
                .nombres(tipo.equals("DNI") ? "Nombre" : null)
                .apellidoPaterno(tipo.equals("DNI") ? "Apellido" : null)
                .apellidoMaterno(tipo.equals("DNI") ? "Prueba" : null)
                .direccion(direccion)
                .simulado(true)
                .build();
    }
}