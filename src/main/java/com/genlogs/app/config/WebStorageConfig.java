package com.genlogs.app.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.genlogs.app.service.AlmacenamientoArchivoService;

import lombok.RequiredArgsConstructor;

/** Expone la carpeta de archivos subidos en /uploads/** para que el navegador pueda mostrarlos. */
@Configuration
@RequiredArgsConstructor
public class WebStorageConfig implements WebMvcConfigurer {

    private final AlmacenamientoArchivoService almacenamiento;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations(almacenamiento.getRaiz().toUri().toString());
    }
}