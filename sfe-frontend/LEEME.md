# Consola de pruebas SFE (frontend independiente)

No toca el prototipo. Es un nginx aparte en el puerto 3000 que sirve `index.html`
y reenvía `/api/` al prototipo (localhost:8080), poniendo la API key en el proxy.

## Uso
1. Con el prototipo arriba (`docker compose ps` en `deploy/`), desde esta carpeta:

       docker compose up -d

2. Abre http://localhost:3000
3. Para apagarlo: `docker compose down`

## Qué muestra
- Emitir factura o boleta (formulario o JSON editable; el JSON sirve para notas 07/08).
- Lista de la sesión con estado que cambia solo (PENDIENTE → ACEPTADO).
- Contingencia en vivo (RF-10).
- Consulta por id con el CDR.

## Límites
- No hay listado de todos los comprobantes: el prototipo no tiene ese endpoint.
  La lista es la de esta sesión del navegador.
- La API key de desarrollo está en `nginx.conf`. Es solo para demo local.
