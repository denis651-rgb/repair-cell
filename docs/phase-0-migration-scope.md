# Fase 0 - Alcance congelado para migracion comercial

## Base de datos dev

- Base anterior respaldada en `backend/backups/repair-shop_phase0_backup_20260422_134537.db`
- Base activa de desarrollo movida a `backend/data/repair-shop-phase0.db`
- Base historica anterior conservada sin uso como referencia, porque estaba bloqueada por un proceso local

## Pantallas identificadas

- `frontend/src/pages/InventoryPage.jsx`
- `frontend/src/pages/ComprasPage.jsx`
- `frontend/src/pages/VentasPage.jsx`
- `frontend/src/pages/ReportesPage.jsx`

## Pantallas relacionadas por impacto directo

- `frontend/src/pages/ProveedoresPage.jsx`
- `frontend/src/pages/CuentasPorCobrarPage.jsx`

## Endpoints a tocar o revisar

### Inventario y productos

- `GET /api/inventario/productos`
- `GET /api/inventario/productos/paginado`
- `GET /api/inventario/productos/stock-bajo`
- `GET /api/inventario/productos/{id}`
- `GET /api/inventario/productos/sku/sugerencia`
- `POST /api/inventario/productos`
- `PUT /api/inventario/productos/{id}`
- `POST /api/inventario/productos/{id}/stock`
- `DELETE /api/inventario/productos/{id}`
- `GET /api/inventario/movimientos`
- `GET /api/inventario/movimientos/paginado`
- `GET /api/inventario/movimientos/producto/{productoId}`
- `GET /api/inventario/categorias`
- `POST /api/inventario/categorias`
- `PUT /api/inventario/categorias/{id}`
- `DELETE /api/inventario/categorias/{id}`
- `GET /api/inventario/marcas`
- `POST /api/inventario/marcas`
- `PUT /api/inventario/marcas/{id}`
- `DELETE /api/inventario/marcas/{id}`

### Compras

- `GET /api/compras/paginado`
- `GET /api/compras/{id}`
- `POST /api/compras`

### Ventas

- `GET /api/ventas/paginado`
- `GET /api/ventas/{id}`
- `POST /api/ventas`
- `POST /api/ventas/{id}/devolucion`

### Reportes

- Revisar `ReporteControlador.java`
- Revisar impacto sobre rentabilidad, costo promedio, ventas y stock

## Riesgos congelados en Fase 0

- No tocar aun la logica actual de compras, ventas, descuentos de stock ni reportes.
- No eliminar la base anterior mientras siga bloqueada por procesos locales externos al flujo de la app.
- Cualquier migracion de modelo nuevo debe preservar compatibilidad hacia atras con lo que hoy ya funciona.
