# Evidencia APF2 (2026-10-29)

| Caso | Comando | Resultado |
|---|---|---|
| CP-XML-01 | `mvn -B -pl sfe-core test "-Dtest=EmisionPipelineTest"` | 6 pruebas, 0 fallos |
| CP-SIGN-02 | misma ejecución | firma verificada; 1 byte alterado invalida la firma |
| CP-SOAP-03 | `select ... from comprobante` | 16 comprobantes ACEPTADO, CDR 0 (SUNAT BETA) |
| CP-CDR-04 | `mvn -B -pl sfe-core test "-Dtest=CdrYPoliticaTest"` | 11 pruebas, 0 fallos |
| CP-AUTH-08 | `mvn -B -pl sfe-core test "-Dtest=DominioTest#cp_auth_08_credencialesBetaSegunManual"` | 1 prueba, 0 fallos |

Estados distintos de ACEPTADO (políticas verificadas): F001-8/9 RECHAZADO (2992, sin reintentos), F001-1/2 EXCEPCION (Client.0160; cola COMPLETADO, sin reintentos desde la corrección del hallazgo 3).