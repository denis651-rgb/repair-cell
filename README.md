# Sistema de escritorio para taller de reparación de celulares

Proyecto full-stack para una tienda de reparación de celulares que también vende repuestos.

## Stack

- Backend: Spring Boot + Java 21
- Base de datos: SQLite
- Frontend: React + Vite + CSS
- Desktop wrapper: Electron

## Funcionalidades incluidas

- Gestión de clientes y dispositivos
- Gestión de órdenes de reparación
- Seguimiento por estados: `RECIBIDO`, `EN_DIAGNOSTICO`, `EN_REPARACION`, `LISTO`, `ENTREGADO`
- Asociación de repuestos a la orden
- Descuento automático de stock cuando el repuesto sale de la tienda
- Registro de movimientos de inventario
- Ticket imprimible o exportable a PDF desde la vista del navegador
- Contabilidad simplificada con entradas, salidas y balance por período
- Funcionamiento offline con SQLite local
- Wrapper de escritorio con Electron para lanzar backend y frontend como app local

## Modelo de datos

### Tablas principales

- `customers`
- `devices`
- `inventory_categories`
- `inventory_products`
- `repair_orders`
- `repair_order_parts`
- `stock_movements`
- `accounting_entries`

El esquema SQL completo está en:

```text
backend/src/main/resources/schema.sql
```

## Arquitectura general

Consulta el archivo:

```text
ARCHITECTURE.md
```

## Ejecución en desarrollo

### 1. Backend

Requisitos:
- Java 21
- Maven 3.9+

```bash
cd backend
mvn spring-boot:run
```

API local:

```text
http://localhost:8080
```

### 2. Frontend

Requisitos:
- Node.js 20+

```bash
cd frontend
npm install
npm run dev
```

Frontend local:

```text
http://localhost:5173
```

### 3. Electron en desarrollo

Desde la raíz del proyecto:

```bash
npm install
npm --prefix frontend install
```

Primero empaqueta el backend en JAR:

```bash
cd backend
mvn -DskipTests package
cd ..
```

Luego ejecuta el frontend y Electron:

```bash
npm run desktop:dev
```

## Generar instalador de escritorio

### 1. Construir frontend

```bash
npm run frontend:build
```

### 2. Empaquetar backend

```bash
cd backend
mvn -DskipTests package
cd ..
```

### 3. Generar instalador

```bash
npm run dist
```

El instalador generado quedará en:

```text
dist/
```

## Cómo funciona Electron

- Al abrir la app, Electron ejecuta el JAR de Spring Boot como proceso local.
- El frontend React se muestra dentro de la ventana de Electron.
- Al cerrar la app, Electron detiene el proceso Java.

Archivo principal:

```text
electron/main.js
```

## Endpoints principales

### Clientes
- `GET /api/customers`
- `POST /api/customers`
- `PUT /api/customers/{id}`
- `DELETE /api/customers/{id}`

### Dispositivos
- `GET /api/devices`
- `POST /api/devices?customerId={id}`

### Órdenes de reparación
- `GET /api/repair-orders`
- `GET /api/repair-orders/{id}`
- `POST /api/repair-orders`
- `PATCH /api/repair-orders/{id}/status`

### Ticket
- `GET /api/tickets/repair-order/{id}/html`

### Inventario
- `GET /api/inventory/categories`
- `POST /api/inventory/categories`
- `GET /api/inventory/products`
- `GET /api/inventory/products/low-stock`
- `POST /api/inventory/products?categoryId={id}`
- `POST /api/inventory/products/{id}/stock?quantity=5&movementType=ENTRADA`
- `GET /api/inventory/movements`

### Contabilidad
- `GET /api/accounting/entries`
- `POST /api/accounting/entries`
- `GET /api/accounting/balance?startDate=2026-04-01&endDate=2026-04-30`

## Notas importantes

- El ticket se puede imprimir o exportar a PDF usando la opción de impresión del navegador o de Electron.
- SQLite se eligió por simplicidad de instalación, portabilidad y funcionamiento offline.
- El archivo de base de datos se crea automáticamente al iniciar el backend.
- El proyecto es una base funcional y clara para continuar con autenticación local, reportes, respaldo de base de datos y mejoras de UI.
