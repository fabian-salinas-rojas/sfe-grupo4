# Consola de pruebas SFE (frontend independiente)

No toca el prototipo. Es un nginx aparte en el puerto 3000 que sirve `index.html`
y reenvía `/api/` al prototipo (localhost:8080), poniendo la API key en el proxy.

## Uso
1. Con el prototipo arriba (`docker compose ps` en `deploy/`), desde esta carpeta:

       docker compose up -d

2. Abre http://localhost:3000
3. Para apagarlo: `docker compose down`

## Qué muestra
- Emitir factura, boleta, nota de crédito (07) y nota de débito (08), con formulario o JSON editable.
- Lista de la sesión con estado que cambia solo (PENDIENTE → ACEPTADO).
- Contingencia en vivo (RF-10).
- Consulta por id con el CDR.
- Enviar por correo (RF-07) un comprobante aceptado; la bandeja de pruebas es Mailpit (http://localhost:8025).
- Casos de prueba rápidos (USD, ítems mixtos, boleta consumidor final, y dos casos de error: 422 y 400).
- Tiempo hasta la respuesta de SUNAT por comprobante, resumen de la sesión y exportación a CSV.
- Estados ACEPTADO CON OBSERVACIONES y EN CONTINGENCIA con su propio color; errores explicados en español.

## Límites
- No hay listado de todos los comprobantes: el prototipo no tiene ese endpoint.
  La lista es la de esta sesión del navegador.
- La API key de desarrollo está en `nginx.conf`. Es solo para demo local.
