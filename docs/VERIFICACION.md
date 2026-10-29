# Verificación realizada (salida real)

Entorno de generación: JDK 21.0.10, JUnit 5.10.1 (jars de apt), JAXB 2.3, PostgreSQL 16.15, Logback 1.2.11, **1 vCPU**.
Sin acceso a Maven Central, a Docker ni a SUNAT. Fecha del sandbox: octubre 2026.

## 1. `sfe-core` — JUnit

Cómo se ejecutó: se compiló `sfe-core` con `javac` sobre una **copia** donde `jakarta.xml.bind` se cambió mecánicamente a `javax.xml.bind`
(JAXB 2.3 de apt; las anotaciones y la API usadas son idénticas) y se corrió el `ConsoleLauncher` de JUnit 5.

```
RNF-01 -> 50 emisiones concurrentes: p50=65 ms, p95=164 ms, max=192 ms (cpus=1)
+-- JUnit Platform Suite
+-- JUnit Jupiter
| +-- LogMaskerTest
| | +-- enmascaraSecretosEnFormasComunes()
| | +-- enmascaraWsSecurityYLlavesPem()
| | '-- noTocaMensajesNormales()
| +-- EmisionPipelineTest
| | +-- facturaBoletaNotasGeneranXmlFirmadoValidoYZip()
| | +-- xmlDelZipEsIdenticoByteAByteAlFirmado()
| | +-- sha1ConfigurableFirmaYVerifica()
| | +-- alterarUnByteDespuesDeFirmarInvalidaLaFirma()
| | +-- tildesEneYCaracteresFueraDeLatin1SeSaneanYNoRompenLaFirma()
| | '-- firmaEnvelopedUnicaConIdReferenciadoPorCacSignature()
| +-- DominioTest
| | +-- rn03NotaExigeReferenciaMotivoYCatalogo()
| | +-- rn02BoletaSobreSetecientosExigeIdentificarAlCliente()
| | +-- sanitizadorLatin1()
| | +-- cp_auth_08_credencialesBetaSegunManual()
| | +-- rn01FacturaExigeRucDelAdquirente()
| | +-- totalesCuadranConElDetalleYRedondeanPorLinea()
| | +-- secretosNuncaSeImprimen()
| | +-- montoEnLetras()
| | +-- aesGcmCifraDescifraYDetectaAlteracion()
| | '-- validacionesBasicasDeItemsYMoneda()
| +-- EmpaquetadoYSoapTest
| | +-- elRucDebeConstarEnElCampoOuDelCertificado()
| | +-- contrasenaIncorrectaNoSeFiltraEnElMensaje()
| | +-- certificadoVencidoOaunNoVigenteSeRechaza()
| | +-- respuestasSoapSeInterpretan()
| | +-- zipNoSeTruncaConXmlGrande()
| | +-- firmaSinLineBreaksCRLF()
| | +-- nombresDeArchivoSegunManual()
| | +-- aDomRechazaDoctypeEnRespuestas()
| | +-- getStatusCdrConLosCuatroCamposDelManual()
| | '-- sendBillConFormaDelManual()
| +-- CasosDeUsoTest
| | +-- sinSaltosDeCorrelativoSiLaEmisionFalla()
| | +-- unaFallaInesperadaNoAbortaElLote()
| | +-- excepcionDelContribuyenteQuedaParaRevisionManual()
| | +-- emisionNumeraGeneraZipYDejaPendiente()
| | +-- workerNuncaReprogramaUnRechazoNormativo()
| | +-- reglasDeNegocioFallanAntesDeTocarNada()
| | +-- carreraEntreDosPeticionesIgualesDevuelveLaGanadora()
| | +-- workerReprogramaConBackoffExponencialEnRedYFaultServer()
| | +-- reintentoDelPosConElMismoIdDevuelveElMismoComprobanteSinGastarCorrelativo()
| | '-- workerAceptaYRegistraElCdr()
| +-- CdrYPoliticaTest
| | +-- cdrRechazadoNormativoNuncaSeReintenta()
| | +-- faultConCodigoDeRechazoOExcepcionDelContribuyenteNoSeReintenta()
| | +-- redTimeoutYFaultServerSeReintentan()
| | +-- cdrIlegibleSeReintenta()
| | +-- rn05CualquierCodigoDistintoDeCeroEnUnaCdrEsRechazoSalvoExcepcionesDeSunat()
| | +-- cdrAceptadoConObservacionesDelManual()
| | +-- parserRechazaDoctypeYEntidadesExternas()
| | +-- backoffExponencialConTopeYJitter()
| | +-- zipReaderPrefiereLaConstanciaYLimitaTamano()
| | +-- cdrConDoctypeNoSeProcesa()
| | '-- cdrAceptadoSinNotas()
| '-- RendimientoTest
|   '-- cincuentaEmisionesConcurrentes()
'-- JUnit Vintage
[        51 tests found           ]
[        51 tests successful      ]
[         0 tests failed          ]
```

`RNF-01` (50 emisiones concurrentes) varió entre corridas: p95 de ~80 ms a ~440 ms según carga del sandbox (1 vCPU); siempre muy por debajo de 1,2 s.

## 2. SQL contra PostgreSQL 16.15 real (`sfe-persistence/src/test/sql/probar-sql.sh`)

Los `.sql` probados son **los mismos archivos** que carga `JdbcOutbox`/`JdbcCorrelativos` (`db/queries/`). Migraciones V1, V2 y V900 aplicadas desde cero.

```
== 1) SKIP LOCKED: dos workers reclaman a la vez (A retiene su transaccion 3 s) ==
worker A reclamo 5, worker B reclamo 5, en comun: 0   (esperado: 5, 5, 0)
EN_PROCESO x10 intentos=1..1
== 2) Lease: un EN_PROCESO abandonado (lease vencido) se retoma; uno con lease vigente NO ==
reclamado: intento=2
(esperado: exactamente 1 fila, con intento=2)
== 3) Fencing: el worker lento NO pisa al que retomo el envio ==
W1 (obsoleto, intento=1): UPDATE 0   |   W2 (vigente, intento=2): UPDATE 1   (esperado: UPDATE 0 / UPDATE 1)
== 4) Correlativos sin saltos: 40 transacciones concurrentes, 10 con ROLLBACK ==
ultimo_correlativo=30
(esperado: 30 = 40 - 10 revertidas; el rollback devuelve el numero, no quedan huecos)
confirmadas: 30, distintos: 30, min=1, max=30
OK: los numeros confirmados son exactamente 1..30 (sin huecos ni duplicados)
== 5) Inmutabilidad y bitacora de solo agregar ==
UPDATE comprobante SET xml_firmado='\\x01'                     -> ERROR:  El comprobante firmado es inmutable
UPDATE comprobante SET estado='ACEPTADO', cdr_codigo='0'       -> UPDATE 1
DELETE FROM comprobante                                        -> ERROR:  Los comprobantes no se eliminan (conservacion legal)
INSERT INTO bitacora_auditoria(evento,actor) VALUES ('X','t')  -> INSERT 0 1
UPDATE bitacora_auditoria SET actor='y'                        -> ERROR:  La bitacora de auditoria es de solo agregar
DELETE FROM bitacora_auditoria                                 -> ERROR:  La bitacora de auditoria es de solo agregar

```

## 3. Logback real

Entrada → salida del `MaskedMessageConverter` (Logback 1.2.11):

```
login password=SuperSecreta123 usuario=juan                    →  login password=*** usuario=juan
api-key=abc123XYZ fin                                           →  api-key=*** fin
<wsse:Password Type="x">moddatos</wsse:Password>                →  <wsse:Password Type="x">***</wsse:Password>
creds SunatCredentials[...Secret.of("NuncaEnLog77")]            →  creds SunatCredentials[username=20608945123USR, password=***]
```

## 4. Chequeo de los módulos Spring (sin las librerías de Spring)

`javac -proc:none` sobre `sfe-persistence`, `sfe-api` y `sfe-worker`: **0 errores de sintaxis**; los errores restantes son únicamente
"paquete/símbolo de Spring no existe". Los símbolos que apuntan a clases propias de `sfe-core` resolvieron correctamente
(salvo `JdbcClient`, que es de Spring). Esto detecta errores gruesos, **no** reemplaza compilar con Spring.

## 5. Lo que NO se pudo verificar

`mvn verify`, `FlujoEmisionIT`, el arranque de `sfe-api`/`sfe-worker`, el cliente Spring‑WS/WSS4J, el runtime JAXB `jakarta` 4.x y cualquier llamada a SUNAT BETA.
