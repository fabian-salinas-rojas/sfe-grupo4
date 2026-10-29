package pe.sfe.core;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import pe.sfe.core.security.LogMasker;

/** Regla no negociable #3: nada de credenciales en logs. */
class LogMaskerTest {

    @Test
    void enmascaraSecretosEnFormasComunes() {
        assertEquals("login password=*** usuario=juan", LogMasker.mask("login password=SuperSecreta123 usuario=juan"));
        assertEquals("cfg clave_sol: ***, otro=1", LogMasker.mask("cfg clave_sol: MiClaveSOL99, otro=1"));
        assertEquals("{\"password\":\"***\"}", LogMasker.mask("{\"password\":\"hunter2\"}"));
        assertEquals("x-api-key=*** fin", LogMasker.mask("x-api-key=abc123XYZ fin"));
        assertEquals("Authorization: ***", LogMasker.mask("Authorization: Bearer eyJhbGciOi.abc.def"));
    }

    @Test
    void enmascaraWsSecurityYLlavesPem() {
        String soap = "<wsse:UsernameToken><wsse:Username>20608945123MODDATOS</wsse:Username>"
                + "<wsse:Password Type=\"PasswordText\">miClaveReal</wsse:Password></wsse:UsernameToken>";
        String m = LogMasker.mask(soap);
        assertFalse(m.contains("miClaveReal"), m);
        assertTrue(m.contains("<wsse:Password Type=\"PasswordText\">***</wsse:Password>"), m);
        String pem = "key -----BEGIN PRIVATE KEY-----\nMIIEvQIBADANBgkq\nabc\n-----END PRIVATE KEY----- fin";
        assertEquals("key ***PRIVATE KEY*** fin", LogMasker.mask(pem));
    }

    @Test
    void noTocaMensajesNormales() {
        String ok = "Factura F001-1 aceptada por SUNAT en 120 ms (intento 2)";
        assertEquals(ok, LogMasker.mask(ok));
        assertNull(LogMasker.mask(null));
        assertEquals("", LogMasker.mask(""));
    }
}
