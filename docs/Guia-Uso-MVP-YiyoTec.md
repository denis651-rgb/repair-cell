# Guia de Uso del Sistema MVP

## Yiyo Tec - Taller de Celulares

Documento orientado a presentacion comercial y operativa del sistema MVP para taller de reparacion de celulares.

## 1. Objetivo del sistema

El sistema fue disenado para centralizar la operacion diaria del taller en una sola aplicacion de escritorio. Su objetivo es reducir errores manuales, mejorar el control de ingresos y mantener trazabilidad sobre clientes, dispositivos, ordenes, tickets, inventario, caja y respaldos.

## 2. Alcance del MVP

El MVP actualmente cubre:

- Inicio de sesion administrativo
- Gestion de clientes
- Gestion de dispositivos
- Registro y seguimiento de ordenes de reparacion
- Tickets imprimibles y envio por WhatsApp
- Control de inventario y repuestos
- Caja diaria y contabilidad
- Reportes operativos y comerciales
- Backups locales y preparacion para Google Drive
- Funcionamiento como aplicacion de escritorio con base de datos local

## 3. Acceso al sistema

### Inicio de sesion

Al abrir la aplicacion se muestra la pantalla de login. El acceso esta restringido a usuarios con rol administrador.

En entorno inicial de demostracion se puede usar:

- Usuario: admin
- Contrasena: admin123

Nota: estas credenciales pueden cambiarse para produccion.

## 4. Estructura general del sistema

El menu lateral permite navegar entre los modulos principales:

- Dashboard
- Clientes
- Dispositivos
- Ordenes
- Inventario
- Contabilidad
- Reportes
- Respaldos

En la parte superior existe un buscador general y accesos rapidos segun la pantalla activa.

## 5. Flujo operativo recomendado

### Paso 1. Abrir caja

Antes de iniciar la jornada se recomienda entrar a Contabilidad y abrir la caja diaria con el monto inicial del turno.

Esto permite:

- habilitar control de efectivo
- calcular cierre esperado
- registrar ingresos y salidas del dia
- asociar movimientos contables a la caja activa

### Paso 2. Registrar clientes

Si el cliente no existe, se crea en el modulo Clientes.

Datos recomendados:

- nombre completo
- telefono
- correo opcional
- direccion opcional

### Paso 3. Registrar dispositivos

En Dispositivos se asocia cada equipo a un cliente. Esto permite mantener historial tecnico por equipo.

Datos principales:

- cliente relacionado
- marca
- modelo
- IMEI o serie
- color u observaciones

### Paso 4. Crear orden de reparacion

En Ordenes se registra el ingreso del equipo al taller.

La orden permite guardar:

- cliente
- dispositivo
- falla reportada
- diagnostico tecnico
- tecnico responsable
- costo estimado
- costo final
- fecha estimada de entrega
- garantia
- firma del cliente
- texto de confirmacion
- repuestos o partes utilizadas

### Paso 5. Gestionar estados de la orden

La orden puede avanzar por estados como:

- RECIBIDO
- EN_DIAGNOSTICO
- EN_REPARACION
- LISTO
- ENTREGADO
- CANCELADO

Cuando la orden cambia a ENTREGADO, el sistema registra automaticamente el ingreso en Contabilidad usando el monto real visible de la orden.

## 6. Modulo Dashboard

El Dashboard resume el estado general del taller.

Permite visualizar:

- ordenes acumuladas
- ordenes pendientes
- caja activa
- comportamiento de ordenes
- estado de reparaciones
- ordenes recientes

Su funcion es dar una vista ejecutiva y operativa al iniciar la jornada.

## 7. Modulo Clientes

Sirve para administrar la base de clientes del taller.

Acciones principales:

- crear cliente
- editar cliente
- buscar por nombre o telefono
- consultar informacion historica basica

Beneficio:

Evita duplicados y mejora el seguimiento comercial del negocio.

## 8. Modulo Dispositivos

Sirve para registrar equipos vinculados a clientes.

Acciones principales:

- crear nuevo dispositivo
- buscar cliente desde el mismo modal
- registrar datos tecnicos del equipo
- consultar el listado general de equipos

Beneficio:

Cada orden queda asociada a un equipo real y no solo a un cliente.

## 9. Modulo Ordenes

Es el modulo central del sistema.

### Funciones principales

- crear orden
- guardar borradores
- agregar repuestos o partes
- actualizar estado
- consultar historial
- abrir ticket

### Borradores

El sistema permite guardar ordenes como borrador para terminarlas despues. Esto es util cuando:

- el cliente esta apurado
- falta confirmar diagnostico
- aun no se definio costo final

### KPI de ordenes

La pantalla muestra indicadores como:

- ordenes visibles
- pendientes
- entregadas
- valor visible

El valor visible ya no depende solo del estimado. El sistema utiliza el monto real con esta prioridad:

1. costo final
2. suma de repuestos y partes cargadas
3. costo estimado

### Monto real de la orden

La tarjeta de cada orden y el ticket reflejan datos reales y no montos fijos. Si existe costo final, ese valor tiene prioridad. Si no existe, el sistema usa el valor de partes cargadas y como respaldo el costo estimado.

## 10. Ticket profesional

Cada orden cuenta con una vista de ticket mejorada para entrega al cliente.

### El ticket muestra

- numero de orden
- cliente
- telefono
- fecha de recepcion
- tecnico responsable
- estado actual
- equipo
- IMEI o serie
- fecha estimada
- firma cliente
- falla reportada
- diagnostico tecnico
- detalle de partes
- costo estimado
- costo final o total visible
- garantia

### Acciones disponibles

- volver a ordenes
- imprimir o exportar a PDF
- descargar HTML
- enviar por WhatsApp

### WhatsApp

El mensaje de WhatsApp se genera con formato ordenado desde backend para compartir con el cliente un resumen claro de la orden.

## 11. Modulo Inventario

Permite controlar categorias, productos y stock.

### Funciones principales

- crear categorias
- crear productos
- buscar categoria dentro del modal de producto
- ajustar stock
- ver movimientos

### Integracion con ordenes

Cuando en una orden se agrega un repuesto desde inventario y la fuente es TIENDA, el sistema descuenta stock automaticamente.

### Filtro por categoria y buscadores

Los modales ya incluyen buscadores para reducir tiempo de carga y evitar listas largas.

## 12. Modulo Contabilidad

Este modulo controla movimientos manuales y automaticos.

### Caja diaria

Permite:

- abrir caja
- ver monto de apertura
- ver entradas
- ver salidas
- calcular esperado al cierre
- cerrar caja con observaciones

### Registro manual

Se pueden registrar movimientos manuales como:

- gastos operativos
- compras
- pagos varios
- ingresos extraordinarios

### Registro automatico desde ordenes

Cuando una orden pasa a ENTREGADO, el sistema crea o actualiza un movimiento contable automatico asociado a esa orden.

Esto asegura:

- trazabilidad entre orden y contabilidad
- menos errores manuales
- mejor control de caja diaria

## 13. Modulo Reportes

El modulo Reportes ofrece una vista rapida del rendimiento del taller.

Se utiliza para revisar:

- comportamiento comercial
- volumen de ordenes
- clientes destacados
- tecnicos destacados
- facturacion resumida

Es especialmente util para seguimiento gerencial.

## 14. Modulo Respaldos

El sistema incluye una pantalla dedicada para administracion de backups.

### Funciones actuales

- ejecutar respaldo manual
- ver historial de respaldos
- configurar carpeta local
- definir retencion
- activar compresion ZIP
- reintentar pendientes
- preparar integracion con Google Drive

### Google Drive

La pantalla ya esta lista para configurarse con:

- ID de carpeta de Drive
- ruta al JSON de la service account

## 15. Aplicacion de escritorio

El sistema puede ejecutarse como aplicacion de escritorio usando Electron.

### Beneficios

- uso local sin depender del navegador
- base SQLite propia
- empaquetado para instalacion
- arranque del backend dentro de la app
- logs tecnicos para diagnostico

## 16. Beneficios del MVP para el cliente

- centraliza la operacion del taller
- mejora el control del proceso de reparacion
- reduce errores en cobros y tickets
- integra inventario con ordenes
- integra ordenes con contabilidad
- permite crecimiento por modulos
- deja lista la base para una siguiente fase comercial

## 17. Limitaciones normales de un MVP

Como MVP, el sistema prioriza operatividad y valor de negocio antes que automatizaciones avanzadas de gran escala.

Puede evolucionar en siguientes fases con:

- multiples roles de usuario
- caja por sucursal
- cierres mas avanzados
- sincronizacion completa con nube
- dashboard gerencial extendido
- reportes financieros mas profundos

## 18. Recomendacion para demostracion con cliente

Para presentar el MVP se recomienda mostrar este flujo:

1. iniciar sesion
2. abrir caja
3. crear cliente
4. crear dispositivo
5. registrar orden
6. agregar repuesto
7. generar ticket
8. enviar por WhatsApp
9. marcar como entregado
10. revisar ingreso automatico en contabilidad
11. mostrar respaldo manual
12. mostrar reportes

## 19. Conclusion

El sistema MVP ya permite operar un taller de reparacion con una base funcional, clara y escalable. La plataforma integra recepcion, proceso tecnico, control de repuestos, entrega, ticket, caja y reportes en una sola experiencia.

Este MVP ya es presentable para validacion con cliente y tambien sirve como base concreta para una version comercial mas completa.
