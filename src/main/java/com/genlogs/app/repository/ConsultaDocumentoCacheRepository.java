package com.genlogs.app.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.genlogs.app.model.ConsultaDocumentoCache;

@Repository
public interface ConsultaDocumentoCacheRepository extends JpaRepository<ConsultaDocumentoCache, Long> {

    Optional<ConsultaDocumentoCache> findByNumeroDocumento(String numeroDocumento);
}