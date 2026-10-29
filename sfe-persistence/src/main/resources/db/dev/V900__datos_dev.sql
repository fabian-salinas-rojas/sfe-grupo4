-- SOLO PERFIL dev (spring.flyway.locations incluye classpath:db/dev). Nunca cargar en produccion.
-- Emisor ficticio del APF1 en BETA y una caja con API Key de desarrollo: dev-key-caja-01-NO-USAR-EN-PROD
INSERT INTO emisor (ruc, razon_social, nombre_comercial, ubigeo, direccion, departamento, provincia, distrito, entorno)
VALUES ('20608945123', 'COMERCIAL ANDINA SAC', 'ANDINA MARKET', '150101', 'AV. AREQUIPA 1234 - OF. 5', 'LIMA', 'LIMA', 'LIMA', 'BETA');

INSERT INTO pos (emisor_id, nombre, api_key_hash)
SELECT id, 'CAJA-01', '6b1c92361c99c6c671bd2ba55caf8a7ccb7b7d3d9f8f98620ebd7e235bb8b1df' FROM emisor WHERE ruc = '20608945123';

INSERT INTO serie (emisor_id, pos_id, tipo_comprobante, serie)
SELECT e.id, p.id, t.tipo, t.serie
FROM emisor e JOIN pos p ON p.emisor_id = e.id AND p.nombre = 'CAJA-01',
     (VALUES ('01','F001'), ('03','B001'), ('07','FC01'), ('07','BC01'), ('08','FD01'), ('08','BD01')) AS t(tipo, serie)
WHERE e.ruc = '20608945123';
