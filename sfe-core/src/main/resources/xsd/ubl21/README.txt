XSD oficiales de OASIS UBL 2.1 (Invoice, CreditNote, DebitNote y componentes comunes).
Copiados sin cambios de contenido, salvo UNA modificacion en common/UBL-UnqualifiedDataTypes-2.1.xsd:
el <xsd:import> de CoreComponentTypeSchemaModule:2 lleva schemaLocation="CCTS_CCT_SchemaModule-2.1.xsd"
(igual que en la distribucion oficial de OASIS) para que se resuelva localmente, sin acceso a red.
CCTS_CCT_SchemaModule-2.1.xsd: modulo de tipos de componentes centrales de UN/CEFACT (CCTS v2.01).
Fuente de la copia: repositorios phax/ph-ubl y phax/ph-xsds (Apache-2.0); contenido (c) OASIS / UN/CEFACT.
Verificar contra la distribucion oficial vigente de SUNAT antes de produccion.
common/UBL-xmldsig-core-schema-2.1.xsd: esquema W3C XMLDSig (xmldsig-core-schema.xsd); el <xsd:import> de
common/UBL-SignatureAggregateComponents-2.1.xsd lleva schemaLocation hacia este archivo (igual que la distribucion OASIS).
