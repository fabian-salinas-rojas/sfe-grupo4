# Trazabilidad: requisito (APF1/APF2/Stack/Manual) → código → prueba

Los IDs son los de tus documentos (APF1 V3, APF2 V1, Stack Ideal). "✅" = cubierto y probado en el sandbox; "⚠️" = escrito pero no ejecutado; "❌" = fuera de este incremento.

## Requisitos funcionales (APF1)

| ID | Requisito | Código | Prueba | |
|---|---|---|---|---|
| RF-01 | XML UBL 2.1 válido | `UblXmlBuilder`, `Ubl*`, `XsdValidator` | `EmisionPipelineTest.facturaBoletaNotasGeneranXmlFirmadoValido…` (4 tipos vs XSD oficial) | ✅ |
| RF-02 | XMLDSig en `ExtensionContent`, X.509 v3 | `XmlSigner`, `SigningKey` | `firmaEnvelopedUnicaConId…`, `alterarUnByte…`, `xmlDelZipEsIdentico…`, `sha1Configurable…` | ✅ |
| RF-03 | ZIP + SOAP `wsse:Security` + `sendBill` | `ZipPackager`, `SunatSoapPayloads`, `SunatSoapClient` | `EmpaquetadoYSoapTest` (ZIP/payloads) · cliente Spring‑WS ejecutado contra BETA (F001‑3, B001‑1, FC01‑1 aceptados) | ✅ |
| RF-04 | Procesar CDR y registrar estado | `CdrParser`, `EnvioClassifier`, `JdbcOutbox` | `CdrYPoliticaTest` · `FlujoEmisionIT` | ✅ |
| RF-05 | Contingencia: timeout (>10 s) o caída → cola de reintentos sin bloquear la caja | outbox + `ProcesarEnviosService`, `Backoff`, timeouts 5 s/10 s | `CasosDeUsoTest.workerReprograma…`, SQL real (SKIP LOCKED, lease, fencing); prueba en vivo: worker detenido → F001‑4/5/6 PENDIENTE → ACEPTADO solos | ✅ |
| RF-06 | PDF + QR | — | — | ❌ |
| RF-07 | Envío por correo | — | — | ❌ |
| RF-08 | Autenticación BETA `[RUC]MODDATOS` | `SunatCredentials.beta` | `DominioTest.cp_auth_08…` (credencial) · login real en BETA (`20608945123MODDATOS`) | ✅ |
| RF-09 | Notas de crédito/débito (Cat. 09/10, motivo, referencia) | `ReglasNegocio.validarNota`, `UblXmlBuilder` | `facturaBoletaNotas…`, `DominioTest.rn03…` | ✅ |
| RF-10 | Reporte de contingencia | métricas `sfe.outbox.*` | — | ❌ (parcial) |

## Reglas de negocio

| ID | Regla | Implementación | Prueba |
|---|---|---|---|
| RN-01 | Factura a cliente con RUC, serie F | `ReglasNegocio`, `SolicitudEmision.letraSerie` | `DominioTest.rn01…` |
| RN-02 | Boleta; identificar al adquirente si > S/ 700 | `ReglasNegocio.validarBoleta` | `DominioTest.rn02…` |
| RN-03 | Nota: referencia, tipo, motivo; serie hereda F/B | `ReglasNegocio.validarNota` | `DominioTest.rn03…` |
| RN-04 | Total = Σ valores de venta + IGV, regla única | `CalculadoraTotales` | `DominioTest.totalesCuadran…` |
| RN-05 | ACEPTADO solo con CDR `0`; código ≠ 0 ⇒ RECHAZADO | `EnvioClassifier.deCdr` | `CdrYPoliticaTest.rn05…` |
| RN-06 | Enviar hasta el 3.er día calendario siguiente | `plazo_limite` (+3 días), métrica `sfe.comprobantes.plazo_vencido` | `CasosDeUsoTest.emisionNumera…` |
| RN-07 | Sin respuesta/conexión: conservar en cola y reenviar hasta tener CDR | outbox + `Backoff` (tope 15 min) | `workerReprograma…` |
| RN-08 | Firmar con certificado vigente | `SigningKey.assertVigente` (se llama antes de numerar) | `EmpaquetadoYSoapTest.certificadoVencido…`, `CasosDeUsoTest.sinSaltos…` |

## No funcionales

| ID | Requisito | Cómo | Evidencia |
|---|---|---|---|
| RNF-01 | XML+firma+ensamble ≤ 1,2 s con 50 concurrentes | pipeline puro, `JAXBContext`/`Schema` compartidos | `RendimientoTest` (p95 80–440 ms en 1 vCPU) ✅ |
| RNF-02 | Clave SOL y llave privada cifradas AES‑256; TLS ≥ 1.2 | `AesGcmCipher` (AES‑256‑GCM + AAD), PKCS#12 AES‑256‑CBC/PBKDF2, `jdk.tls.client.protocols` | `DominioTest.aesGcm…`; `openssl pkcs12 -info` ✅ · TLS real ✅ (HTTPS contra SUNAT BETA) |
| RNF-03 | Cola sigue aceptando; tras reinicio retoma ≤ 60 s sin perder nada | outbox transaccional, lease 30 s + sondeo 2 s ≈ 32 s | SQL real (lease/fencing) ✅ |
| RNF-04 | Cumplir validaciones del Anexo I/VIII | XSD oficial antes de firmar | `EmisionPipelineTest` ✅ (el XSD no cubre todas las reglas de negocio de SUNAT) |
| RNF-05 | BETA ↔ producción solo por configuración | `emisor.entorno` + URLs en propiedades | ✅ `Rnf05EntornoTest` (credenciales por entorno) + revisión de código (URL por entorno en `SunatSoapClient`); sin envío a producción |

## Casos de prueba del APF2

| Caso | Descripción | Equivalente verificado | Estado real |
|---|---|---|---|
| CP-XML-01 | Validación contra XSD | `EmisionPipelineTest` | ✅ |
| CP-SIGN-02 | Hash y digestValue | verificación XMLDSig de los bytes finales; 1 byte alterado ⇒ falla | ✅ |
| CP-SOAP-03 | `sendBill` exitoso (BETA) | `sendBill` real contra BETA: F001‑3, B001‑1, FC01‑1, FD01‑1 con CDR 0 | ✅ |
| CP-CDR-04 | Lectura de CDR aceptado y rechazado | `CdrYPoliticaTest` con los ejemplos C.1/C.2 del manual | ✅ |
| CP-AUTH-08 | Login `[RUC]MODDATOS` | login real en BETA aceptado (contraseña `moddatos`) | ✅ |

## Evidencia en SUNAT BETA (2026‑10‑29)

| Caso | Comprobante | Resultado |
|---|---|---|
| Factura, boleta, NC, ND | F001‑3, B001‑1, FC01‑1, FD01‑1 | ACEPTADO, CDR 0 |
| USD y tildes/ñ | F001‑7 | ACEPTADO |
| Exonerado / inafecto | F001‑10, F001‑11 | ACEPTADO (F001‑8/9 rechazados por `2992` antes de corregir `cbc:Percent`) |
| Rechazo 2xxx no se reintenta | F001‑8, F001‑9 | RECHAZADO, `intentos=1` |
| Fault Client no se reintenta | F001‑1, F001‑2 | EXCEPCION (`Client.0160`) |
| Contingencia (RF‑05) | F001‑4/5/6 | PENDIENTE sin worker → ACEPTADO al reiniciarlo |


## Reglas "no negociables" del Stack (§6 y §8)

| Regla | Dónde / prueba |
|---|---|
| Reintentar solo red/timeout/Fault Server; 2000‑3999 jamás | `EnvioClassifier` · `redTimeoutYFaultServer…`, `faultConCodigoDeRechazo…`, `workerNuncaReprograma…` |
| Excepciones 0100‑1999 = no informado | `EXCEPCION` · `excepcionDelContribuyente…` |
| Observaciones 4000+ ⇒ aceptado con advertencias (notas del CDR) | `cdr_observaciones` · `cdrAceptadoConObservacionesDelManual` |
| Ni un byte tras firmar | un solo `toBytes`; `xmlDelZipEsIdentico…`; trigger `comprobante_inmutable` (SQL real) |
| `getStatusCdr` solo producción; en BETA se lee la CDR de `sendBill` | `BillClient.getStatusCdr` no lo usa el worker |
| Nunca registrar Clave SOL/certificado | `Secret`, `SunatCredentials.toString`, `LogMasker` (`LogMaskerTest`, Logback real) |
| No concatenar strings para el XML | JAXB + DOM en todo el módulo (incluidos los cuerpos SOAP) |
| `StreamResult(ByteArrayOutputStream)` sin `Writer` | `XmlSerializer`; `zipNoSeTrunca…` |
| Id de firma referenciado desde `cac:Signature` | `#SignatureSP` · `firmaEnvelopedUnicaConId…` |
| Parser sin DOCTYPE (XXE) | `SecureXml` · `parserRechazaDoctype…`, `cdrConDoctypeNoSeProcesa` |
| Sanear a Latin‑1 | `TextSanitizer` · `sanitizadorLatin1`, `tildesEne…` |
| Dummy y algoritmo configurables | `sfe.zip.dummy-folder`, `sfe.signing.algorithm` · `sha1Configurable…` |
