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

CREATE TABLE IF NOT EXISTS productos_inventario (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    categoria_id INTEGER NOT NULL,
    sku TEXT NOT NULL UNIQUE,
    nombre TEXT NOT NULL,
    descripcion TEXT,
    costo_unitario REAL NOT NULL DEFAULT 0,
    precio_venta REAL NOT NULL DEFAULT 0,
    cantidad_stock INTEGER NOT NULL DEFAULT 0,
    stock_minimo INTEGER NOT NULL DEFAULT 0,
    costo_promedio REAL DEFAULT 0,
    activo INTEGER NOT NULL DEFAULT 1,
    creado_en TEXT NOT NULL,
    actualizado_en TEXT NOT NULL,
    FOREIGN KEY (categoria_id) REFERENCES categorias_inventario(id)
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
    nombre_parte TEXT NOT NULL,
    cantidad INTEGER NOT NULL DEFAULT 1,
    costo_unitario REAL NOT NULL DEFAULT 0,
    precio_unitario REAL NOT NULL DEFAULT 0,
    tipo_fuente TEXT NOT NULL,
    notas TEXT,
    creado_en TEXT NOT NULL,
    actualizado_en TEXT NOT NULL,
    FOREIGN KEY (orden_reparacion_id) REFERENCES ordenes_reparacion(id),
    FOREIGN KEY (producto_id) REFERENCES productos_inventario(id)
);

CREATE TABLE IF NOT EXISTS movimientos_stock (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    producto_id INTEGER NOT NULL,
    tipo_movimiento TEXT NOT NULL,
    cantidad INTEGER NOT NULL,
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

CREATE INDEX IF NOT EXISTS idx_clientes_nombre ON clientes(nombre_completo);
CREATE INDEX IF NOT EXISTS idx_dispositivos_cliente ON dispositivos(cliente_id);
CREATE INDEX IF NOT EXISTS idx_productos_categoria ON productos_inventario(categoria_id);
CREATE INDEX IF NOT EXISTS idx_productos_stock_bajo ON productos_inventario(cantidad_stock, stock_minimo);
CREATE INDEX IF NOT EXISTS idx_ordenes_reparacion_cliente ON ordenes_reparacion(cliente_id);
CREATE INDEX IF NOT EXISTS idx_ordenes_reparacion_dispositivo ON ordenes_reparacion(dispositivo_id);
CREATE INDEX IF NOT EXISTS idx_ordenes_reparacion_estado ON ordenes_reparacion(estado);
CREATE INDEX IF NOT EXISTS idx_ordenes_reparacion_recibido ON ordenes_reparacion(recibido_en);
CREATE INDEX IF NOT EXISTS idx_partes_orden_reparacion_orden ON partes_orden_reparacion(orden_reparacion_id);
CREATE INDEX IF NOT EXISTS idx_movimientos_stock_producto ON movimientos_stock(producto_id);
CREATE INDEX IF NOT EXISTS idx_entradas_contables_fecha ON entradas_contables(fecha_entrada);
CREATE INDEX IF NOT EXISTS idx_entradas_contables_caja ON entradas_contables(caja_id);
CREATE INDEX IF NOT EXISTS idx_orden_historial_orden ON orden_historial(orden_id);
CREATE INDEX IF NOT EXISTS idx_usuarios_username ON usuarios(username);
CREATE INDEX IF NOT EXISTS idx_backup_records_generado_en ON backup_records(generado_en);
CREATE INDEX IF NOT EXISTS idx_backup_records_estado ON backup_records(estado);
