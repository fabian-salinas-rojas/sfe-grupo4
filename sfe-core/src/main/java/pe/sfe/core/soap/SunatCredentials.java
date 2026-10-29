package pe.sfe.core.soap;

import pe.sfe.core.security.Secret;

/**
 * Credenciales WS-Security UsernameToken. La Clave SOL tiene 3 campos (RUC, usuario, contrasena) pero el token solo
 * admite 2: se concatenan RUC+usuario en Username (manual, 2.2). toString nunca muestra la contrasena.
 */
public record SunatCredentials(String username, Secret password) {

    /**
     * BETA: Usuario = [RUC]MODDATOS. La contrasena es ambigua en las fuentes: el texto del manual (2.3) y el APF1 (RF-08)
     * dicen "MODDATOS" y los ejemplos SOAP del manual usan "moddatos". Por defecto "moddatos"; es configurable
     * (sfe.sunat.beta-password) para probar ambas en BETA sin recompilar.
     */
    public static SunatCredentials beta(String ruc) {
        return beta(ruc, "moddatos");
    }

    public static SunatCredentials beta(String ruc, String password) {
        return new SunatCredentials(ruc + "MODDATOS", Secret.of(password));
    }

    public static SunatCredentials produccion(String ruc, String usuarioSol, Secret claveSol) {
        return new SunatCredentials(ruc + usuarioSol, claveSol);
    }

    @Override
    public String toString() {
        return "SunatCredentials[username=" + username + ", password=***]";
    }
}
