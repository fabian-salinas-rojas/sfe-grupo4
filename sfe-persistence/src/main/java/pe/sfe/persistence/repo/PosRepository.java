package pe.sfe.persistence.repo;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import pe.sfe.persistence.entity.PosEntity;

public interface PosRepository extends JpaRepository<PosEntity, Long> {

    /** La API Key llega en claro solo por TLS; aqui se busca por su SHA-256 (hex). */
    Optional<PosEntity> findByApiKeyHashAndActivoTrue(String apiKeyHash);
}
