package pe.sfe.core;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import pe.sfe.core.security.Secret;
import pe.sfe.core.soap.SunatCredentials;

class Rnf05EntornoTest {

    private static final String RUC = "20608945123";

    @Test
    void betaYProduccionProducenCredencialesDistintasSinRecompilar() {
        SunatCredentials beta = SunatCredentials.beta(RUC, "moddatos");
        SunatCredentials prod = SunatCredentials.produccion(RUC, "USUARIO1", Secret.of("ClaveSecreta9"));

        assertEquals(RUC + "MODDATOS", beta.username());
        assertTrue(prod.username().startsWith(RUC));
        assertTrue(prod.username().endsWith("USUARIO1"));
        assertNotEquals(beta.username(), prod.username());
        assertNotEquals(beta.password().reveal(), prod.password().reveal());
    }

    @Test
    void laClaveDeProduccionNuncaSeImprime() {
        SunatCredentials prod = SunatCredentials.produccion(RUC, "USUARIO1", Secret.of("ClaveSecreta9"));
        assertFalse(prod.toString().contains("ClaveSecreta9"));
    }
}