package pe.sfe.api.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Un solo destinatario, sin espacios, comillas, comas ni signos de menor/mayor (evita multiples destinos e inyeccion de cabeceras). */
public record EnviarCorreoRequest(
        @NotBlank
        @Size(max = 254)
        @Pattern(regexp = "^[^\\s<>\",;]+@[^\\s<>\",;]+\\.[A-Za-z]{2,}$", message = "correo invalido")
        String destinatario) {
}
