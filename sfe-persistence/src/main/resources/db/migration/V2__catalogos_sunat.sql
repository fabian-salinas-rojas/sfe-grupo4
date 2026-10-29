INSERT INTO cat_tipo_comprobante VALUES
    ('01', 'Factura'), ('03', 'Boleta de venta'), ('07', 'Nota de credito'), ('08', 'Nota de debito');

INSERT INTO cat_tipo_doc_identidad VALUES
    ('0', 'Documento tributario no domiciliado sin RUC'), ('1', 'DNI'), ('4', 'Carnet de extranjeria'),
    ('6', 'RUC'), ('7', 'Pasaporte');

-- Catalogo 07 (operaciones onerosas) -> codigo de tributo del Catalogo 05
INSERT INTO cat_afectacion_igv VALUES
    ('10', 'Gravado - operacion onerosa', '1000'), ('20', 'Exonerado - operacion onerosa', '9997'),
    ('30', 'Inafecto - operacion onerosa', '9998'), ('40', 'Exportacion de bienes o servicios', '9995');

INSERT INTO cat_unidad_medida VALUES
    ('NIU', 'Unidad (bienes)'), ('ZZ', 'Unidad (servicios)'), ('KGM', 'Kilogramo'), ('LTR', 'Litro'),
    ('MTR', 'Metro'), ('BX', 'Caja'), ('BG', 'Bolsa'), ('PK', 'Paquete');

INSERT INTO cat_tipo_nota_credito VALUES
    ('01', 'Anulacion de la operacion'), ('02', 'Anulacion por error en el RUC'),
    ('03', 'Correccion por error en la descripcion'), ('04', 'Descuento global'), ('05', 'Descuento por item'),
    ('06', 'Devolucion total'), ('07', 'Devolucion por item'), ('08', 'Bonificacion'),
    ('09', 'Disminucion en el valor'), ('10', 'Otros conceptos');

INSERT INTO cat_tipo_nota_debito VALUES
    ('01', 'Intereses por mora'), ('02', 'Aumento en el valor'), ('03', 'Penalidades / otros conceptos');
