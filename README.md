# SFE Grupo 4 — Prototipo perfeccionado (Java 21 · Spring Boot 3 · PostgreSQL 16)

Sistema de Facturación Electrónica (SEE – Sistemas del Contribuyente, SUNAT) según el **Manual del Programador (mayo 2021)**,
el **Stack Ideal**, el **APF1** y el **APF2** del Grupo 4. Monolito modular en **2 procesos** (`sfe-api`, `sfe-worker`), sin microservicios.

> **Léelo primero — qué está verificado y qué no** (sección 2). El núcleo (XML, firma, ZIP, CDR, reintentos, casos de uso) y el SQL
> están probados con pruebas automáticas; el stack completo (api ×2, worker ×2, PostgreSQL, Nginx) se ejecutó con Docker Compose y se
> **validó contra SUNAT BETA** (factura, boleta, notas de crédito y débito; ver §2 y §8). Sigue sin probarse producción.

## 1. Módulos

| Módulo | Contenido | Spring |
|---|---|---|
| `sfe-core` | Dominio y reglas RN-01..04, UBL 2.1 con **JAXB**, validación **XSD**, **XMLDSig** enveloped, ZIP, parser de CDR (anti‑XXE), política de reintentos, backoff, casos de uso (`EmitirComprobanteService`, `ProcesarEnviosService`) y puertos | No |
| `sfe-persistence` | Migraciones **Flyway** V1/V2 (+ datos dev), consultas SQL (outbox `SKIP LOCKED`, correlativo sin saltos, fencing), adaptadores JDBC/JPA, logback con enmascaramiento de secretos | Sí |
| `sfe-api` | `POST /api/v1/comprobantes` → **202 Accepted**, API Key por POS, idempotencia, errores `problem+json` | Sí |
| `sfe-worker` | Sondeo del outbox, cliente **Spring‑WS + WSS4J** (UsernameToken, timeouts 5 s / 10 s), métricas | Sí |

```
 POS ──HTTPS + X-API-Key──▶ sfe-api ──(1 transacción)──▶ PostgreSQL 16
                              │  valida RN-01..03 · numera sin saltos · UBL (JAXB) · XSD · XMLDSig · ZIP
                              ▼  comprobante + outbox + bitácora                      ▲
                          202 Accepted                          FOR UPDATE SKIP LOCKED │ lease + fencing
                                                                                       │
                                                           sfe-worker ×N ──SOAP/WS-Security──▶ SUNAT (BETA/PROD)
                                                              sendBill → CDR → ACEPTADO / RECHAZADO / contingencia
```

## 2. Estado de verificación (sin adornos)

| Pieza | Estado | Evidencia |
|---|---|---|
| `sfe-core` (UBL, XSD, firma, ZIP, CDR, política, casos de uso) | ✅ **53 pruebas JUnit pasan** (+ 5 de integración = 58) | `docs/VERIFICACION.md` |
| Facturas, boletas, NC y ND contra los **XSD oficiales UBL 2.1** + firma verificada | ✅ | `EmisionPipelineTest`, ejemplos en `docs/ejemplos/` |
| SQL del outbox, lease, fencing, correlativo sin saltos, inmutabilidad | ✅ **PostgreSQL 16.15 real**, sesiones concurrentes | `docs/VERIFICACION.md` |
| Enmascaramiento de secretos en logs | ✅ **Logback real** + `LogMaskerTest` | `docs/VERIFICACION.md` |
| RNF-01: 50 emisiones concurrentes (XML+firma+ZIP) | ✅ p95 = 90 ms (16 CPU, local) y 33 ms (4 CPU, CI); máx. < 0,1 s (límite 1,2 s) | `RendimientoTest` |
| `sfe-persistence` / `sfe-api` / `sfe-worker` (código Spring) | ✅ **Compila y se ejecuta** en Docker Compose (2 api + 2 worker + PostgreSQL + Nginx): emisión 202, outbox, worker y CDR reales | Salidas de `docker compose` y consultas SQL del 2026‑10‑29 |
| JAXB | ✅ Runtime `jakarta` 4.x (Spring Boot 3) ejercitado: los XML generados por la API fueron aceptados por SUNAT BETA | Comprobantes ACEPTADOS en §8 |
| Versiones de Spring Boot 3.5.6 / WSS4J 3.0.4 | ✅ Resuelven y compilan (`mvn -B -q -pl sfe-worker -am -DskipTests compile` sin errores) | `pom.xml` |
| **SUNAT BETA** (`sendBill` real, CDR real) | ✅ **Probado** (2026‑10‑29): factura, boleta, NC y ND aceptadas con CDR 0; USD, tildes/ñ, exonerado e inafecto; rechazo real 2992 clasificado sin reintentos; contingencia sin worker | CP‑SOAP‑03; ver §8 |
| `FlujoEmisionIT` (Testcontainers) | ✅ 5/5 con PostgreSQL 16 real (Testcontainers). En Docker Desktop 29 hace falta `~/.docker-java.properties` con `api.version=1.44` | `mvn -B verify` |
| Producción (Clave SOL, certificado real, getStatusCdr) | ❌ No probado | — |

**Primer comando en tu máquina:** `mvn -B verify` (necesita Docker para `FlujoEmisionIT`; con Docker Desktop 29 crea antes `~/.docker-java.properties` con `api.version=1.44`).

## 3. Decisiones de diseño que importan

| Decisión | Por qué |
|---|---|
| **Modelo JAXB de UBL escrito a mano** (subconjunto) en vez de generar ~1500 clases con `xjc` | Más pequeño y legible. La conformidad la garantiza validar contra el **XSD oficial antes de firmar** y de nuevo el XML firmado completo (incluye el esquema `xmldsig`) |
| XSD valida una **copia** con marcador en `ext:ExtensionContent` | UBL exige un hijo en ese nodo (`xsd:any`, mín. 1) y antes de firmar está vacío. El original queda intacto para firmarlo |
| `DOMSource` → `Transformer` → **`StreamResult(ByteArrayOutputStream)`** (sin `Writer`) | Evita el XML vacío/truncado (Stack §8). El XML se serializa **una sola vez**; esos bytes son los firmados, los del ZIP y los de la BD |
| Autoverificación de la firma sobre los bytes finales + test "1 byte alterado ⇒ no verifica" | Garantiza que nada rompe la firma entre firmar y empaquetar |
| `ignoreLineBreaks` para el Base64 de la firma | Sin él, el JDK emite CRLF que se serializa como `&#13;` |
| **Tabla `serie` con `UPDATE … RETURNING`** y no una `SEQUENCE` | Una secuencia deja huecos al hacer rollback; SUNAT exige correlativos sin saltos. Probado: 40 transacciones, 10 con rollback ⇒ confirmadas = 1..30 exactas |
| Outbox: **claim corto + SOAP fuera de transacción + lease + fencing** | No se retienen filas ni conexiones durante la llamada a SUNAT. Si un worker muere, otro retoma al vencer el lease (30 s + sondeo 2 s ≈ 32 s < 60 s, RNF‑03). El *fencing* (`intentos`) impide que un worker lento pise el resultado del que retomó |
| **Idempotencia** por `(pos, idVentaExterno)` | Un reintento del POS por timeout no consume otro correlativo |
| Trigger de **inmutabilidad** + bitácora solo‑agregar | El comprobante firmado no puede cambiar ni borrarse (ni por error ni por SQL directo) |
| Estado extra `EXCEPCION` | Excepción del contribuyente (Fault `Client` / 1000‑1999): "no informado, corregir y reenviar" (manual 4.1). No es rechazo ni se reintenta a ciegas |

### Política de reintentos (regla #1)

| Resultado de SUNAT | Estado | ¿Reintenta? |
|---|---|---|
| Red, timeout, TLS, HTTP 5xx, Fault `Server` (0100‑0999) | `EN_CONTINGENCIA` | Sí, backoff 10 s × 2ⁿ, tope 15 min, ±20 % jitter |
| CDR código `0` sin notas / con notas (4000+) | `ACEPTADO` / `ACEPTADO_CON_OBSERVACIONES` | No |
| CDR o Fault con código **2000‑3999** | `RECHAZADO` | **Nunca** (el número ya está consumido) |
| Fault `Client` o 1000‑1999 | `EXCEPCION` | No (corregir y reenviar) |
| CDR con otro código ≠ 0 (RN‑05) | `RECHAZADO` | No |
| CDR ilegible | `EN_CONTINGENCIA` | Sí |

El código numérico manda sobre el prefijo: un `Server.2800` es rechazo y **no** se reintenta.

## 4. Cómo ejecutarlo

```bash
# Núcleo + pruebas (requiere JDK 21 y Maven)
mvn -B -pl sfe-core test

# Todo (incluye FlujoEmisionIT con Testcontainers → necesita Docker)
mvn -B verify

# Stack local: PostgreSQL 16 + 2×api + 2×worker + Nginx
cd deploy
cp .env.example .env            # completa SFE_MASTER_KEY con: openssl rand -base64 32
./gen-dev-cert.sh               # certificado AUTOFIRMADO de desarrollo (RUC en OU)
docker compose up --build
```

Emitir una factura (la API Key de desarrollo está en `V900__datos_dev.sql`; **solo perfil `dev`**):

```bash
curl -i -X POST http://localhost:8080/api/v1/comprobantes \
  -H 'X-API-Key: dev-key-caja-01-NO-USAR-EN-PROD' -H 'Content-Type: application/json' \
  -d '{
    "idVentaExterno": "CAJA01-000123", "tipo": "01", "moneda": "PEN",
    "adquirente": {"tipoDocumento": "6", "numeroDocumento": "20512345678", "denominacion": "CLIENTE EJEMPLO SAC"},
    "items": [{"codigo": "P001", "descripcion": "Cuaderno A4", "unidadMedida": "NIU",
               "cantidad": 2, "valorUnitario": 50.00, "afectacionIgv": "10"}]
  }'
# → 202 Accepted · Location: /api/v1/comprobantes/{id} · {"estado":"PENDIENTE","serie":"F001","correlativo":1,"importeTotal":118.00,...}
curl -H 'X-API-Key: ...' http://localhost:8080/api/v1/comprobantes/{id}      # estado y CDR
```

La caja **no** envía serie, correlativo, fecha ni totales: los asigna el servidor (RN‑04). Boleta sin `adquirente` ⇒ consumidor final
(RN‑02 exige identificarlo si supera S/ 700). Notas: `tipo` 07/08 + `referencia` + `motivo`.
`422` = regla de negocio · `400` = formato · `401` = API Key · `503` = certificado no vigente (no se consume correlativo).

## 5. Configuración (solo variables de entorno; BETA ↔ PROD sin recompilar, RNF‑05)

| Variable / propiedad | Uso |
|---|---|
| `SFE_DB_URL`, `SFE_DB_USER`, `SFE_DB_PASSWORD` | PostgreSQL |
| `SFE_MASTER_KEY` | Base64 de 32 bytes (AES‑256‑GCM) para cifrar la Clave SOL en reposo. Obligatoria: sin ella no arranca |
| `SFE_KEYSTORE_PATH`, `SFE_KEYSTORE_PASSWORD` | PKCS#12 del certificado (montado como secreto) |
| `sfe.signing.algorithm` | `SHA256` (defecto) o `SHA1` — confirmar en BETA |
| `sfe.zip.dummy-folder` | `false` (valor fijado: BETA rechaza el ZIP con carpeta `dummy` con `Client.0160 "El archivo XML esta vacio"`); `true` solo si el manual 1.3 lo exigiera en producción |
| `emisor.entorno` (tabla) | `BETA` → `[RUC]MODDATOS`/`moddatos` y URL beta; `PRODUCCION` → RUC+usuario SOL y Clave SOL descifrada |
| `sfe.sunat.url-beta` / `url-produccion` | Endpoints `billService` |
| `sfe.worker.lease`, `batch-size`, `backoff-*` | Cola |

## 6. Seguridad (RNF‑02 y reglas no negociables)

- **Clave SOL**: AES‑256‑GCM con AAD por emisor (`emisor:{ruc}:sol`); la clave maestra solo en entorno. Nunca en claro en BD.
- **Llave privada**: PKCS#12 cifrado con **AES‑256‑CBC / PBKDF2‑HMAC‑SHA256** (verificado con `openssl pkcs12 -info`). Fuera de la imagen y del repo.
- **Logs**: `Secret.toString()="***"`, `SunatCredentials.toString()` sin contraseña, y `MaskedMessageConverter` (probado con Logback) enmascara
  `password`/`clave*`/`api-key`/`<wsse:Password>`/PEM aunque alguien los registre por error. Trazas de `MessageTracing` y WSS4J en INFO/WARN.
- **XXE**: todo parseo de XML externo (CDR, respuestas SOAP) con `disallow-doctype-decl`, sin entidades externas ni XInclude; XSD sin acceso a red.
- **API**: API Key por POS (solo se guarda su SHA‑256), sin estado/sesión, `denyAll` por defecto, una caja solo ve sus comprobantes, errores sin trazas.
  Actuator en otro puerto (8081, no publicado). Rate‑limit por caja en Nginx. TLS 1.2+ (`jdk.tls.client.protocols`) hacia SUNAT.
- ⚠️ El perfil `dev` siembra una API Key conocida: **jamás** activarlo en producción.

## 7. Estructura

```
sfe-core/         src/main/java/pe/sfe/core/{domain,xml,sign,pack,cdr,soap,policy,security,text,app}  +  xsd/ubl21/ (XSD oficiales)
sfe-persistence/  db/migration (Flyway) · db/queries (SQL validado) · adaptadores · logback-spring.xml · src/test/sql/probar-sql.sh
sfe-api/          controlador, seguridad, config · FlujoEmisionIT
sfe-worker/       SunatSoapClient (Spring-WS+WSS4J) · OutboxPoller · OutboxMetrics
deploy/           docker-compose, nginx, .env.example, gen-dev-cert.sh      docs/  TRAZABILIDAD · VERIFICACION · ejemplos/
```

## 8. Resultado de la validación contra SUNAT BETA (CP‑SOAP‑03)

Que un XML sea válido contra el XSD **no** garantiza que SUNAT lo acepte (aplica reglas de negocio adicionales). Resultados de las pruebas del 2026‑10‑29:

| # | Punto a confirmar | Resultado |
|---|---|---|
| 1 | `sendBill` real con `20608945123MODDATOS` / `moddatos` y lectura de `applicationResponse` | ✅ CDR recibido y leído |
| 2 | SHA‑256 vs SHA‑1 | ✅ SHA‑256 (valor por defecto) aceptado |
| 2b | ZIP con/sin carpeta `dummy` | ✅ **Sin carpeta** (`sfe.zip.dummy-folder=false`). Con carpeta: `Client.0160` |
| 3 | `cac:PaymentTerms`, `CustomizationID=2.0`, `InvoiceTypeCode@listID=0101`, leyenda 1000, categorías `S/E/O` | ✅ Aceptados (G/exportación no probada) |
| 4 | Boletas por `sendBill` | ✅ B001‑1 aceptada; no hizo falta resumen diario |
| 5 | Certificado autofirmado en BETA | ✅ Aceptado |

### Comprobantes de prueba

| Comprobante | Caso | Estado |
|---|---|---|
| F001‑3 | Factura PEN gravada | ACEPTADO, CDR 0 |
| B001‑1 | Boleta | ACEPTADO, CDR 0 |
| FC01‑1 | Nota de crédito | ACEPTADO, CDR 0 |
| F001‑7 | USD, `NIÑO PEÑA & CÍA S.A.C.`, tildes y raya | ACEPTADO, CDR 0 |
| F001‑8, F001‑9 | Exonerado (20) e inafecto (30), versión inicial | **RECHAZADO** `2992` (sin reintentos, `intentos=1`) |
| F001‑10, F001‑11 | Exonerado e inafecto tras corregir el XML | ACEPTADO, CDR 0 |
| FD01‑1 | Nota de débito sobre F001‑3 | ACEPTADO, CDR 0 |
| FC01‑2 | NC que referencia F001‑999 (inexistente) | ACEPTADO (BETA no valida la existencia del referenciado) |
| F001‑1, F001‑2 | ZIP con carpeta `dummy` | EXCEPCION (`Client.0160`), no se reintentan |
| F001‑4/5/6 | Contingencia (RF‑05): worker detenido | PENDIENTE → ACEPTADO solos al reiniciar el worker |

### Hallazgos

1. **Error 2992** (`El XML no contiene el tag de la tasa del tributo de la línea`): el XML omitía `cbc:Percent` en líneas no gravadas. El XSD de UBL no lo exige, SUNAT sí. Corregido en `UblXmlBuilder` (`0.00` para exonerado/inafecto). Evidencia de que el XSD no cubre todas las reglas de SUNAT.
2. **ZIP**: BETA exige el ZIP sin carpeta `dummy`, contrario a la lectura literal del manual 1.3.
3. **Fault no reconocido**: Spring‑WS no trataba esta respuesta como Fault y el worker intentaba decodificarla como CDR; ahora `SunatSoapClient.invocar` la clasifica y queda `EXCEPCION`/`RECHAZADO` según el código.
4. **Codificación**: al probar con PowerShell 5.1 hay que enviar el cuerpo como bytes UTF‑8 con `charset=utf-8`; de lo contrario la API responde 400 con tildes/ñ.
5. **RUC ficticios**: 20608945123 (emisor) y 20512345678 (cliente) tienen 11 dígitos y prefijo 20, pero su dígito verificador (módulo 11) no es válido; la Consulta RUC de SUNAT los rechaza como inválidos, por lo que no corresponden a ningún contribuyente. BETA no valida el dígito verificador y los acepta; producción sí los rechazaría.

## 9. Alcance actual y siguientes incrementos

Implementado: factura, boleta, NC y ND onerosas (IGV 10/20/30/40), contado, PEN/USD, `sendBill` síncrono, CDR, contingencia, reporte de contingencia (`GET /api/v1/contingencia`, RF-10), idempotencia.
**No incluido** (declarado, no escondido): RF‑06 (PDF + QR), RF‑07 (correo al cliente), resumen diario / comunicación de baja (`sendSummary` + `getStatus` existen en el cliente, pero falta
el XML `SummaryDocuments`/`VoidedDocuments`, que usa XSD de SUNAT distintos a los de OASIS), `getStatusCdr` (solo producción), pago a crédito,
ISC/IVAP/gratuitas/descuentos globales/detracción/retención, un certificado por emisor, alta de POS/emisores y consola (JWT), verificación de
**autenticidad** de la firma de la CDR (hoy solo integridad: falta fijar el certificado de SUNAT), alertas sobre `sfe.comprobantes.plazo_vencido`.
