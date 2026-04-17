# Proyecto auditado y refactorizado

## Qué se ajustó

### Fase 1 — Estabilización
- mejoras responsive en dashboard, reportes, clientes, tickets y órdenes
- formulario de órdenes migrado a modal adaptable
- búsqueda y paginación en órdenes y clientes
- tablas con contenedor responsive
- ticket más profesional para impresión

### Fase 2 — Backend enterprise
- validaciones con `jakarta.validation`
- manejo global de errores más claro
- sanitización básica de texto
- filtros de logs de requests
- headers de seguridad
- caché simple con Spring Cache
- índices extra en SQLite y endpoint paginado para órdenes

### Fase 3 — Dashboard profesional
- gráficos reales con `Recharts`
- ingresos por día
- órdenes por día
- estado de reparaciones
- stock bajo

### Fase 4 — Reportería avanzada
- reporte por fecha
- reporte por cliente
- reporte por técnico
- exportación Excel con `xlsx`
- ticket exportable a PDF usando impresión del navegador

## Versiones recomendadas
- Java 17
- Maven 3.9+
- Node.js 20 LTS
- npm 10+

## Backend
```bash
cd backend
mvn clean package
mvn spring-boot:run
```

El backend corre por defecto en:
```bash
http://localhost:8080
```

## Frontend
```bash
cd frontend
npm install
npm run dev
```

El frontend corre por defecto en:
```bash
http://localhost:5173
```

## Dependencias nuevas del frontend
Se agregaron estas librerías:
- `recharts`
- `xlsx`

Si por alguna razón faltan, ejecuta:
```bash
npm install recharts xlsx
```

## Base de datos
El proyecto usa SQLite:
```bash
backend/data/repair-shop.db
```

### Recomendación importante
Como se agregó el campo `tecnico_responsable`, si deseas empezar totalmente limpio y evitar inconsistencias con una base antigua, puedes borrar el archivo:
```bash
backend/data/repair-shop.db
```
Luego reinicia el backend para que vuelva a crear la estructura.

## Rutas nuevas del backend
- `GET /api/ordenes-reparacion/paginado`
- `GET /api/reportes/panel`
- `GET /api/reportes/por-fecha?inicio=YYYY-MM-DD&fin=YYYY-MM-DD`
- `GET /api/reportes/por-cliente`
- `GET /api/reportes/por-tecnico`

## Flujo recomendado para probar
1. Crear cliente
2. Crear dispositivo
3. Crear producto de inventario
4. Crear orden desde el modal
5. Asignar técnico responsable
6. Ver dashboard
7. Ver reportes
8. Exportar Excel
9. Abrir ticket y usar `Imprimir / PDF`
