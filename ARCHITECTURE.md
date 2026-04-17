# Arquitectura del sistema

## Estructura del proyecto

```text
cell-repair-desktop-app/
├── backend/                 # API REST con Spring Boot + SQLite
│   ├── src/main/java/com/store/repair
│   │   ├── config
│   │   ├── controller
│   │   ├── domain
│   │   ├── dto
│   │   ├── repository
│   │   └── service
│   └── src/main/resources
├── frontend/                # React + Vite + CSS
│   └── src
│       ├── api
│       ├── components
│       ├── layout
│       ├── pages
│       └── styles
├── electron/                # Shell de escritorio
└── package.json             # Scripts de build/empaquetado desktop
```

## Módulos funcionales

1. Clientes
2. Dispositivos
3. Órdenes de reparación
4. Ticket imprimible / exportable a PDF
5. Inventario de repuestos
6. Contabilidad simplificada
7. Integración desktop con Electron

## Flujo principal de reparación

1. Registrar cliente
2. Registrar dispositivo
3. Crear orden de reparación
4. Asociar repuestos
5. Si el repuesto es de tienda, descontar stock y registrar salida
6. Cambiar estado de la orden
7. Imprimir ticket o guardar como PDF
8. Registrar ingreso contable por reparación

## Base de datos SQLite

La aplicación usa un archivo local:

```text
backend/data/repair-shop.db
```

Esto permite uso 100% offline y sin servidor externo.
