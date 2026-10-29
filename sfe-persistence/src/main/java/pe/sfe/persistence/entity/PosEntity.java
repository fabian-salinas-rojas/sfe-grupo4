package pe.sfe.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

/** Caja registrada. Solo lectura desde la aplicacion: el alta de cajas es una tarea administrativa (consola, incremento 3). */
@Entity
@Immutable
@Table(name = "pos")
public class PosEntity {

    @Id
    private Long id;

    @Column(name = "emisor_id", nullable = false)
    private Long emisorId;

    @Column(nullable = false)
    private String nombre;

    @Column(name = "api_key_hash", nullable = false)
    private String apiKeyHash;

    @Column(nullable = false)
    private boolean activo;

    protected PosEntity() {
    }

    public Long getId() { return id; }
    public Long getEmisorId() { return emisorId; }
    public String getNombre() { return nombre; }
    public boolean isActivo() { return activo; }
}
