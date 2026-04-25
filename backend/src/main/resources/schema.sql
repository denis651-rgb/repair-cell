PRAGMA foreign_keys = ON;

CREATE TABLE IF NOT EXISTS clientes (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    nombre_completo TEXT NOT NULL,
    telefono TEXT NOT NULL,
    email TEXT,
    direccion TEXT,
    notas TEXT,
    creado_en TEXT NOT NULL,
    actualizado_en TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS dispositivos (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    cliente_id INTEGER NOT NULL,
    marca TEXT NOT NULL,
    modelo TEXT NOT NULL,
    imei_serie TEXT,
    color TEXT,
    codigo_bloqueo TEXT,
    accesorios TEXT,
    observaciones TEXT,
    creado_en TEXT NOT NULL,
    actualizado_en TEXT NOT NULL,
    FOREIGN KEY (cliente_id) REFERENCES clientes(id)
);

CREATE TABLE IF NOT EXISTS categorias_inventario (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    nombre TEXT NOT NULL UNIQUE,
    descripcion TEXT,
    creado_en TEXT NOT NULL,
    actualizado_en TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS marcas_inventario (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    nombre TEXT NOT NULL UNIQUE,
    descripcion TEXT,
    activa INTEGER NOT NULL DEFAULT 1,
    creado_en TEXT NOT NULL,
    actualizado_en TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS productos_inventario (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    categoria_id INTEGER NOT NULL,
    marca_id INTEGER,
    sku TEXT NOT NULL UNIQUE,
    nombre TEXT NOT NULL,
    descripcion TEXT,
    calidad TEXT,
    costo_unitario REAL NOT NULL DEFAULT 0,
    precio_venta REAL NOT NULL DEFAULT 0,
    cantidad_stock INTEGER NOT NULL DEFAULT 0,
    stock_minimo INTEGER NOT NULL DEFAULT 0,
    costo_promedio REAL DEFAULT 0,
    activo INTEGER NOT NULL DEFAULT 1,
    creado_en TEXT NOT NULL,
    actualizado_en TEXT NOT NULL,
    FOREIGN KEY (categoria_id) REFERENCES categorias_inventario(id),
    FOREIGN KEY (marca_id) REFERENCES marcas_inventario(id)
);

CREATE TABLE IF NOT EXISTS productos_base (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    codigo_base TEXT NOT NULL UNIQUE,
    nombre_base TEXT NOT NULL,
    categoria_id INTEGER NOT NULL,
    marca_id INTEGER NOT NULL,
    modelo TEXT,
    descripcion TEXT,
    activo INTEGER NOT NULL DEFAULT 1,
    creado_en TEXT NOT NULL,
    actualizado_en TEXT NOT NULL,
    FOREIGN KEY (categoria_id) REFERENCES categorias_inventario(id),
    FOREIGN KEY (marca_id) REFERENCES marcas_inventario(id)
);

CREATE TABLE IF NOT EXISTS productos_variantes (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    producto_base_id INTEGER NOT NULL,
    codigo_variante TEXT NOT NULL UNIQUE,
    calidad TEXT NOT NULL,
    tipo_presentacion TEXT,
    color TEXT,
    precio_venta_sugerido REAL NOT NULL DEFAULT 0,
    activo INTEGER NOT NULL DEFAULT 1,
    creado_en TEXT NOT NULL,
    actualizado_en TEXT NOT NULL,
    FOREIGN KEY (producto_base_id) REFERENCES productos_base(id)
);

CREATE TABLE IF NOT EXISTS productos_base_compatibilidades (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    producto_base_id INTEGER NOT NULL,
    marca_compatible TEXT NOT NULL,
    modelo_compatible TEXT NOT NULL,
    codigo_referencia TEXT,
    nota TEXT,
    activa INTEGER NOT NULL DEFAULT 1,
    creado_en TEXT NOT NULL,
    actualizado_en TEXT NOT NULL,
    FOREIGN KEY (producto_base_id) REFERENCES productos_base(id)
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_productos_variantes_base_calidad_presentacion
ON productos_variantes(producto_base_id, lower(calidad), lower(coalesce(tipo_presentacion, '')));

CREATE TABLE IF NOT EXISTS lotes_inventario (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    variante_id INTEGER NOT NULL,
    proveedor_id INTEGER,
    codigo_lote TEXT NOT NULL UNIQUE,
    codigo_proveedor TEXT,
    fecha_ingreso TEXT NOT NULL,
    cantidad_inicial INTEGER NOT NULL DEFAULT 0,
    cantidad_disponible INTEGER NOT NULL DEFAULT 0,
    costo_unitario REAL NOT NULL DEFAULT 0,
    subtotal_compra REAL NOT NULL DEFAULT 0,
    estado TEXT NOT NULL,
    compra_id INTEGER,
    activo INTEGER NOT NULL DEFAULT 1,
    visible_en_ventas INTEGER NOT NULL DEFAULT 1,
    fecha_cierre TEXT,
    motivo_cierre TEXT,
    creado_en TEXT NOT NULL,
    actualizado_en TEXT NOT NULL,
    FOREIGN KEY (variante_id) REFERENCES productos_variantes(id),
    FOREIGN KEY (proveedor_id) REFERENCES proveedores(id)
);

CREATE TABLE IF NOT EXISTS ordenes_reparacion (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    numero_orden TEXT NOT NULL UNIQUE,
    cliente_id INTEGER NOT NULL,
    dispositivo_id INTEGER NOT NULL,
    problema_reportado TEXT NOT NULL,
    diagnostico_tecnico TEXT,
    tecnico_responsable TEXT,
    estado TEXT NOT NULL,
    costo_estimado REAL NOT NULL DEFAULT 0,
    costo_final REAL NOT NULL DEFAULT 0,
    fecha_entrega_estimada TEXT,
    recibido_en TEXT NOT NULL,
    entregado_en TEXT,
    dias_garantia INTEGER NOT NULL DEFAULT 0,
    nombre_firma_cliente TEXT,
    texto_confirmacion TEXT,
    creado_en TEXT NOT NULL,
    actualizado_en TEXT NOT NULL,
    FOREIGN KEY (cliente_id) REFERENCES clientes(id),
    FOREIGN KEY (dispositivo_id) REFERENCES dispositivos(id)
);

CREATE TABLE IF NOT EXISTS partes_orden_reparacion (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    orden_reparacion_id INTEGER NOT NULL,
    producto_id INTEGER,
    variante_id INTEGER,
    nombre_parte TEXT NOT NULL,
    cantidad INTEGER NOT NULL DEFAULT 1,
    costo_unitario REAL NOT NULL DEFAULT 0,
    precio_unitario REAL NOT NULL DEFAULT 0,
    tipo_fuente TEXT NOT NULL,
    notas TEXT,
    creado_en TEXT NOT NULL,
    actualizado_en TEXT NOT NULL,
    FOREIGN KEY (orden_reparacion_id) REFERENCES ordenes_reparacion(id),
    FOREIGN KEY (producto_id) REFERENCES productos_inventario(id),
    FOREIGN KEY (variante_id) REFERENCES productos_variantes(id)
);

CREATE TABLE IF NOT EXISTS movimientos_stock (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    producto_id INTEGER NOT NULL,
    tipo_movimiento TEXT NOT NULL,
    cantidad INTEGER NOT NULL,
    stock_anterior INTEGER,
    stock_posterior INTEGER,
    costo_unitario REAL,
    precio_venta_unitario REAL,
    tipo_referencia TEXT,
    referencia_id INTEGER,
    descripcion TEXT,
    fecha_movimiento TEXT NOT NULL,
    creado_en TEXT NOT NULL,
    actualizado_en TEXT NOT NULL,
    FOREIGN KEY (producto_id) REFERENCES productos_inventario(id)
);

CREATE TABLE IF NOT EXISTS entradas_contables (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    tipo_entrada TEXT NOT NULL,
    categoria TEXT NOT NULL,
    descripcion TEXT NOT NULL,
    monto REAL NOT NULL,
    modulo_relacionado TEXT,
    relacionado_id INTEGER,
    fecha_entrada TEXT NOT NULL,
    caja_id INTEGER,
    creado_en TEXT NOT NULL,
    actualizado_en TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS cajas_diarias (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    fecha_apertura TEXT NOT NULL,
    fecha_cierre TEXT,
    monto_apertura REAL NOT NULL,
    monto_cierre REAL,
    monto_esperado REAL,
    estado TEXT NOT NULL,
    usuario_apertura TEXT NOT NULL,
    usuario_cierre TEXT,
    observaciones TEXT,
    creado_en TEXT NOT NULL,
    actualizado_en TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS usuarios (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    username TEXT NOT NULL UNIQUE,
    password TEXT NOT NULL,
    nombre TEXT NOT NULL,
    rol TEXT NOT NULL,
    activo INTEGER NOT NULL DEFAULT 1,
    creado_en TEXT NOT NULL,
    actualizado_en TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS orden_historial (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    orden_id INTEGER NOT NULL,
    estado_anterior TEXT,
    estado_nuevo TEXT NOT NULL,
    usuario TEXT NOT NULL,
    fecha TEXT NOT NULL,
    notas TEXT
);

CREATE TABLE IF NOT EXISTS auditoria (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    accion TEXT NOT NULL,
    modulo TEXT NOT NULL,
    entidad_id INTEGER,
    usuario TEXT NOT NULL,
    fecha TEXT NOT NULL,
    detalles TEXT
);

CREATE TABLE IF NOT EXISTS backup_settings (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    enabled INTEGER NOT NULL DEFAULT 1,
    cron TEXT NOT NULL,
    directory TEXT NOT NULL,
    zip_enabled INTEGER NOT NULL DEFAULT 1,
    retention_days INTEGER NOT NULL DEFAULT 30,
    google_drive_enabled INTEGER NOT NULL DEFAULT 0,
    google_drive_folder_id TEXT,
    google_service_account_key_path TEXT,
    google_drive_folder_name TEXT,
    google_oauth_client_id TEXT,
    google_oauth_client_secret TEXT,
    google_oauth_refresh_token TEXT,
    google_oauth_connected_at TEXT,
    last_automatic_backup_at TEXT,
    creado_en TEXT,
    actualizado_en TEXT
);

CREATE TABLE IF NOT EXISTS backup_records (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    origen TEXT NOT NULL,
    estado TEXT NOT NULL,
    archivo TEXT NOT NULL,
    ruta_local TEXT NOT NULL,
    ubicacion_remota TEXT,
    mensaje TEXT,
    tamano_bytes INTEGER,
    generado_en TEXT NOT NULL,
    ultimo_intento_subida_en TEXT,
    intentos_subida INTEGER NOT NULL DEFAULT 0,
    creado_en TEXT,
    actualizado_en TEXT
);

CREATE TABLE IF NOT EXISTS compras (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    proveedor_id INTEGER NOT NULL,
    fecha_compra TEXT NOT NULL,
    numero_comprobante TEXT,
    observaciones TEXT,
    tipo_pago TEXT NOT NULL,
    total REAL NOT NULL DEFAULT 0,
    activa INTEGER NOT NULL DEFAULT 1,
    creado_en TEXT NOT NULL,
    actualizado_en TEXT NOT NULL,
    FOREIGN KEY (proveedor_id) REFERENCES proveedores(id)
);

CREATE TABLE IF NOT EXISTS compras_detalle (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    compra_id INTEGER NOT NULL,
    producto_id INTEGER,
    variante_id INTEGER,
    categoria_nombre TEXT NOT NULL,
    sku TEXT NOT NULL,
    nombre_producto TEXT NOT NULL,
    producto_base_codigo TEXT,
    marca TEXT NOT NULL,
    calidad TEXT,
    tipo_presentacion TEXT,
    color TEXT,
    codigo_proveedor TEXT,
    codigo_lote TEXT,
    cantidad INTEGER NOT NULL,
    precio_compra_unitario REAL NOT NULL,
    precio_venta_unitario REAL NOT NULL,
    subtotal REAL NOT NULL,
    creado_en TEXT NOT NULL,
    actualizado_en TEXT NOT NULL,
    FOREIGN KEY (compra_id) REFERENCES compras(id),
    FOREIGN KEY (producto_id) REFERENCES productos_inventario(id),
    FOREIGN KEY (variante_id) REFERENCES productos_variantes(id)
);

CREATE TABLE IF NOT EXISTS ventas (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    cliente_id INTEGER NOT NULL,
    fecha_venta TEXT NOT NULL,
    numero_comprobante TEXT,
    tipo_pago TEXT NOT NULL,
    estado TEXT NOT NULL,
    total REAL NOT NULL DEFAULT 0,
    observaciones TEXT,
    fecha_devolucion TEXT,
    motivo_devolucion TEXT,
    creado_en TEXT NOT NULL,
    actualizado_en TEXT NOT NULL,
    FOREIGN KEY (cliente_id) REFERENCES clientes(id)
);

CREATE TABLE IF NOT EXISTS ventas_detalle (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    venta_id INTEGER NOT NULL,
    producto_id INTEGER,
    variante_id INTEGER,
    categoria_nombre TEXT NOT NULL,
    sku TEXT NOT NULL,
    nombre_producto TEXT NOT NULL,
    producto_base_codigo TEXT,
    marca TEXT NOT NULL,
    calidad TEXT,
    tipo_presentacion TEXT,
    color TEXT,
    cantidad INTEGER NOT NULL,
    cantidad_devuelta INTEGER NOT NULL DEFAULT 0,
    precio_lista_unitario REAL NOT NULL DEFAULT 0,
    precio_venta_unitario REAL NOT NULL,
    subtotal REAL NOT NULL,
    creado_en TEXT NOT NULL,
    actualizado_en TEXT NOT NULL,
    FOREIGN KEY (venta_id) REFERENCES ventas(id),
    FOREIGN KEY (producto_id) REFERENCES productos_inventario(id),
    FOREIGN KEY (variante_id) REFERENCES productos_variantes(id)
);

CREATE TABLE IF NOT EXISTS venta_detalle_lote (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    venta_detalle_id INTEGER NOT NULL,
    lote_id INTEGER NOT NULL,
    cantidad INTEGER NOT NULL,
    cantidad_devuelta INTEGER NOT NULL DEFAULT 0,
    costo_unitario_aplicado REAL NOT NULL,
    costo_total REAL NOT NULL,
    ganancia_bruta REAL NOT NULL,
    creado_en TEXT NOT NULL,
    actualizado_en TEXT NOT NULL,
    FOREIGN KEY (venta_detalle_id) REFERENCES ventas_detalle(id),
    FOREIGN KEY (lote_id) REFERENCES lotes_inventario(id)
);

CREATE TABLE IF NOT EXISTS cuentas_por_cobrar (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    cliente_id INTEGER NOT NULL,
    venta_id INTEGER NOT NULL UNIQUE,
    fecha_emision TEXT NOT NULL,
    monto_original REAL NOT NULL,
    saldo_pendiente REAL NOT NULL,
    estado TEXT NOT NULL,
    creado_en TEXT NOT NULL,
    actualizado_en TEXT NOT NULL,
    FOREIGN KEY (cliente_id) REFERENCES clientes(id),
    FOREIGN KEY (venta_id) REFERENCES ventas(id)
);

CREATE TABLE IF NOT EXISTS abonos_cuentas_por_cobrar (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    cuenta_por_cobrar_id INTEGER NOT NULL,
    fecha_abono TEXT NOT NULL,
    monto REAL NOT NULL,
    observaciones TEXT,
    creado_en TEXT NOT NULL,
    actualizado_en TEXT NOT NULL,
    FOREIGN KEY (cuenta_por_cobrar_id) REFERENCES cuentas_por_cobrar(id)
);

CREATE INDEX IF NOT EXISTS idx_clientes_nombre ON clientes(nombre_completo);
CREATE INDEX IF NOT EXISTS idx_dispositivos_cliente ON dispositivos(cliente_id);
CREATE INDEX IF NOT EXISTS idx_compras_proveedor ON compras(proveedor_id);
CREATE INDEX IF NOT EXISTS idx_compras_fecha ON compras(fecha_compra);
CREATE INDEX IF NOT EXISTS idx_compras_detalle_compra ON compras_detalle(compra_id);
CREATE INDEX IF NOT EXISTS idx_compras_detalle_variante ON compras_detalle(variante_id);
CREATE INDEX IF NOT EXISTS idx_productos_categoria ON productos_inventario(categoria_id);
CREATE INDEX IF NOT EXISTS idx_productos_marca ON productos_inventario(marca_id);
CREATE INDEX IF NOT EXISTS idx_productos_stock_bajo ON productos_inventario(cantidad_stock, stock_minimo);
CREATE INDEX IF NOT EXISTS idx_productos_base_categoria ON productos_base(categoria_id);
CREATE INDEX IF NOT EXISTS idx_productos_base_marca ON productos_base(marca_id);
CREATE INDEX IF NOT EXISTS idx_productos_base_modelo ON productos_base(modelo);
CREATE INDEX IF NOT EXISTS idx_productos_base_compat_producto ON productos_base_compatibilidades(producto_base_id);
CREATE INDEX IF NOT EXISTS idx_productos_base_compat_modelo ON productos_base_compatibilidades(modelo_compatible);
CREATE INDEX IF NOT EXISTS idx_productos_base_compat_marca ON productos_base_compatibilidades(marca_compatible);
CREATE INDEX IF NOT EXISTS idx_productos_variantes_producto_base ON productos_variantes(producto_base_id);
CREATE INDEX IF NOT EXISTS idx_productos_variantes_calidad ON productos_variantes(calidad);
CREATE INDEX IF NOT EXISTS idx_lotes_variante ON lotes_inventario(variante_id);
CREATE INDEX IF NOT EXISTS idx_lotes_proveedor ON lotes_inventario(proveedor_id);
CREATE INDEX IF NOT EXISTS idx_lotes_estado ON lotes_inventario(estado);
CREATE INDEX IF NOT EXISTS idx_lotes_fecha_ingreso ON lotes_inventario(fecha_ingreso);
CREATE INDEX IF NOT EXISTS idx_ordenes_reparacion_cliente ON ordenes_reparacion(cliente_id);
CREATE INDEX IF NOT EXISTS idx_ordenes_reparacion_dispositivo ON ordenes_reparacion(dispositivo_id);
CREATE INDEX IF NOT EXISTS idx_ordenes_reparacion_estado ON ordenes_reparacion(estado);
CREATE INDEX IF NOT EXISTS idx_ordenes_reparacion_recibido ON ordenes_reparacion(recibido_en);
CREATE INDEX IF NOT EXISTS idx_partes_orden_reparacion_orden ON partes_orden_reparacion(orden_reparacion_id);
CREATE INDEX IF NOT EXISTS idx_partes_orden_reparacion_variante ON partes_orden_reparacion(variante_id);
CREATE INDEX IF NOT EXISTS idx_movimientos_stock_producto ON movimientos_stock(producto_id);
CREATE INDEX IF NOT EXISTS idx_ventas_cliente ON ventas(cliente_id);
CREATE INDEX IF NOT EXISTS idx_ventas_fecha ON ventas(fecha_venta);
CREATE INDEX IF NOT EXISTS idx_ventas_estado ON ventas(estado);
CREATE INDEX IF NOT EXISTS idx_ventas_detalle_venta ON ventas_detalle(venta_id);
CREATE INDEX IF NOT EXISTS idx_ventas_detalle_variante ON ventas_detalle(variante_id);
CREATE INDEX IF NOT EXISTS idx_venta_detalle_lote_detalle ON venta_detalle_lote(venta_detalle_id);
CREATE INDEX IF NOT EXISTS idx_venta_detalle_lote_lote ON venta_detalle_lote(lote_id);
CREATE INDEX IF NOT EXISTS idx_cuentas_por_cobrar_cliente ON cuentas_por_cobrar(cliente_id);
CREATE INDEX IF NOT EXISTS idx_entradas_contables_fecha ON entradas_contables(fecha_entrada);
CREATE INDEX IF NOT EXISTS idx_entradas_contables_caja ON entradas_contables(caja_id);
CREATE INDEX IF NOT EXISTS idx_orden_historial_orden ON orden_historial(orden_id);
CREATE INDEX IF NOT EXISTS idx_usuarios_username ON usuarios(username);
CREATE INDEX IF NOT EXISTS idx_backup_records_generado_en ON backup_records(generado_en);
CREATE INDEX IF NOT EXISTS idx_backup_records_estado ON backup_records(estado);
