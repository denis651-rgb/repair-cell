const fs = require('fs');
const path = require('path');

const dict = {
  // Class Names
  'Customer': 'Cliente',
  'Device': 'Dispositivo',
  'InventoryCategory': 'CategoriaInventario',
  'InventoryProduct': 'ProductoInventario',
  'RepairOrder': 'OrdenReparacion',
  'RepairOrderPart': 'ParteOrdenReparacion',
  'StockMovement': 'MovimientoStock',
  'AccountingEntry': 'EntradaContable',
  'EntryType': 'TipoEntrada',
  'PartSourceType': 'TipoFuenteParte',
  'RepairStatus': 'EstadoReparacion',
  'StockMovementType': 'TipoMovimientoStock',
  'BaseEntity': 'EntidadBase',
  
  // DTOs & Custom types
  'RepairOrderRequest': 'OrdenReparacionRequest',
  'RepairOrderPartRequest': 'ParteOrdenReparacionRequest',
  'StatusUpdateRequest': 'StatusUpdateRequest', // Leave as is or translate? Let's leave
  'BalanceResponse': 'BalanceResponse',
  'TicketController': 'TicketController', // maybe translate?

  // Properties / Variables / Params
  'customer': 'cliente',
  'device': 'dispositivo',
  'inventoryCategory': 'categoriaInventario',
  'inventoryProduct': 'productoInventario',
  'product': 'producto',
  'repairOrder': 'ordenReparacion',
  'repairOrderPart': 'parteOrdenReparacion',
  'stockMovement': 'movimientoStock',
  'accountingEntry': 'entradaContable',
  'entryType': 'tipoEntrada',
  'partSourceType': 'tipoFuenteParte',
  'repairStatus': 'estadoReparacion',
  'stockMovementType': 'tipoMovimientoStock',
  'baseEntity': 'entidadBase',
  'customers' : 'clientes',
  'devices' : 'dispositivos',
  
  // specific property names
  'fullName': 'nombreCompleto',
  'phone': 'telefono',
  'address': 'direccion',
  'notes': 'notas',
  'createdAt': 'creadoEn',
  'updatedAt': 'actualizadoEn',
  'brand': 'marca',
  'model': 'modelo',
  'imeiSerial': 'imeiSerie',
  'color': 'color',
  'lockCode': 'codigoBloqueo',
  'accessories': 'accesorios',
  'observations': 'observaciones',
  
  // Note: we must be careful with "name", perhaps substitute later
  'description': 'descripcion',
  'unitCost': 'costoUnitario',
  'salePrice': 'precioVenta',
  'stockQuantity': 'cantidadStock',
  'minStock': 'stockMinimo',
  'active': 'activo',
  'orderNumber': 'numeroOrden',
  'reportedIssue': 'problemaReportado',
  'technicalDiagnosis': 'diagnosticoTecnico',
  'status': 'estado',
  'estimatedCost': 'costoEstimado',
  'finalCost': 'costoFinal',
  'estimatedDeliveryDate': 'fechaEntregaEstimada',
  'receivedAt': 'recibidoEn',
  'deliveredAt': 'entregadoEn',
  'warrantyDays': 'diasGarantia',
  'customerSignatureName': 'nombreFirmaCliente',
  'confirmationText': 'textoConfirmacion',
  'partName': 'nombreParte',
  'quantity': 'cantidad',
  'unitPrice': 'precioUnitario',
  'sourceType': 'tipoFuente',
  'movementType': 'tipoMovimiento',
  'referenceType': 'tipoReferencia',
  'referenceId': 'referenciaId',
  'movementDate': 'fechaMovimiento',
  'category': 'categoria',
  'amount': 'monto',
  'relatedModule': 'moduloRelacionado',
  'relatedId': 'relacionadoId',
  'entryDate': 'fechaEntrada',

  // Getters/Setters patterns
  'getCustomer': 'getCliente',
  'setCustomer': 'setCliente',
  'getDevice': 'getDispositivo',
  'setDevice': 'setDispositivo',
  'getFullName': 'getNombreCompleto',
  'setFullName': 'setNombreCompleto',
  'getPhone': 'getTelefono',
  'setPhone': 'setTelefono',
  'getAddress': 'getDireccion',
  'setAddress': 'setDireccion',
  'getNotes': 'getNotas',
  'setNotes': 'setNotas',
  'getBrand': 'getMarca',
  'setBrand': 'setMarca',
  'getModel': 'getModelo',
  'setModel': 'setModelo',
  'getImeiSerial': 'getImeiSerie',
  'setImeiSerial': 'setImeiSerie',
  'getColor': 'getColor',
  'setColor': 'setColor',
  'getLockCode': 'getCodigoBloqueo',
  'setLockCode': 'setCodigoBloqueo',
  'getAccessories': 'getAccesorios',
  'setAccessories': 'setAccesorios',
  'getObservations': 'getObservaciones',
  'setObservations': 'setObservaciones',
  'getDescription': 'getDescripcion',
  'setDescription': 'setDescripcion',
  'getUnitCost': 'getCostoUnitario',
  'setUnitCost': 'setCostoUnitario',
  'getSalePrice': 'getPrecioVenta',
  'setSalePrice': 'setPrecioVenta',
  'getStockQuantity': 'getCantidadStock',
  'setStockQuantity': 'setCantidadStock',
  'getMinStock': 'getStockMinimo',
  'setMinStock': 'setStockMinimo',
  'getActive': 'getActivo',
  'setActive': 'setActivo',
  'getOrderNumber': 'getNumeroOrden',
  'setOrderNumber': 'setNumeroOrden',
  'getReportedIssue': 'getProblemaReportado',
  'setReportedIssue': 'setProblemaReportado',
  'getTechnicalDiagnosis': 'getDiagnosticoTecnico',
  'setTechnicalDiagnosis': 'setDiagnosticoTecnico',
  'getStatus': 'getEstado',
  'setStatus': 'setEstado',
  'getEstimatedCost': 'getCostoEstimado',
  'setEstimatedCost': 'setCostoEstimado',
  'getFinalCost': 'getCostoFinal',
  'setFinalCost': 'setCostoFinal',
  'getEstimatedDeliveryDate': 'getFechaEntregaEstimada',
  'setEstimatedDeliveryDate': 'setFechaEntregaEstimada',
  'getReceivedAt': 'getRecibidoEn',
  'setReceivedAt': 'setRecibidoEn',
  'getDeliveredAt': 'getEntregadoEn',
  'setDeliveredAt': 'setEntregadoEn',
  'getWarrantyDays': 'getDiasGarantia',
  'setWarrantyDays': 'setDiasGarantia',
  'getCustomerSignatureName': 'getNombreFirmaCliente',
  'setCustomerSignatureName': 'setNombreFirmaCliente',
  'getConfirmationText': 'getTextoConfirmacion',
  'setConfirmationText': 'setTextoConfirmacion',
  'getPartName': 'getNombreParte',
  'setPartName': 'setNombreParte',
  'getQuantity': 'getCantidad',
  'setQuantity': 'setCantidad',
  'getUnitPrice': 'getPrecioUnitario',
  'setUnitPrice': 'setPrecioUnitario',
  'getSourceType': 'getTipoFuente',
  'setSourceType': 'setTipoFuente',
  'getMovementType': 'getTipoMovimiento',
  'setMovementType': 'setTipoMovimiento',
  'getReferenceType': 'getTipoReferencia',
  'setReferenceType': 'setTipoReferencia',
  'getReferenceId': 'getReferenciaId',
  'setReferenceId': 'setReferenciaId',
  'getMovementDate': 'getFechaMovimiento',
  'setMovementDate': 'setFechaMovimiento',
  'getCategory': 'getCategoria',
  'setCategory': 'setCategoria',
  'getAmount': 'getMonto',
  'setAmount': 'setMonto',
  'getRelatedModule': 'getModuloRelacionado',
  'setRelatedModule': 'setModuloRelacionado',
  'getRelatedId': 'getRelacionadoId',
  'setRelatedId': 'setRelacionadoId',
  'getEntryDate': 'getFechaEntrada',
  'setEntryDate': 'setFechaEntrada',
  'getCreatedAt': 'getCreadoEn',
  'setCreatedAt': 'setCreadoEn',
  'getUpdatedAt': 'getActualizadoEn',
  'setUpdatedAt': 'setActualizadoEn',

  // Some more endpoints mappings
  '"/api/customers"': '"/api/clientes"',
  '"/api/devices"': '"/api/dispositivos"',
  '"/api/inventory-categories"': '"/api/categorias-inventario"',
  '"/api/inventory-products"': '"/api/productos-inventario"',
  '"/api/repair-orders"': '"/api/ordenes-reparacion"',
  '"/api/stock-movements"': '"/api/movimientos-stock"',
  '"/api/accounting"': '"/api/contabilidad"',
  '"/api/tickets"': '"/api/tickets"'
};

function doReplace(content) {
  let str = content;
  // first sort keys by length descending to prevent partial replacements
  const keys = Object.keys(dict).sort((a,b) => b.length - a.length);
  for(let k of keys) {
    const val = dict[k];
    // To match words strictly if it's alphanumeric
    if (/^[A-Za-z0-9]+$/.test(k)) {
      const regex = new RegExp(`\\b${k}\\b`, 'g');
      str = str.replace(regex, val);
    } else {
      // endpoints or strings with quotes
      const regex = new RegExp(k.replace(/([\/\-\"\[\]])/g, '\\$1'), 'g');
      str = str.replace(regex, val);
    }
  }
  // also handle "name" -> "nombre" but carefully, e.g. private String name;
  str = str.replace(/\bString name\b/g, 'String nombre');
  str = str.replace(/\bthis\.name\b/g, 'this.nombre');
  str = str.replace(/\getName\b/g, 'getNombre');
  str = str.replace(/\setName\b/g, 'setNombre');
  
  return str;
}

function processDir(dir) {
  const files = fs.readdirSync(dir);
  for(let f of files) {
    const p = path.join(dir, f);
    if(fs.statSync(p).isDirectory()) {
      processDir(p);
    } else if (p.endsWith('.java')) {
      let content = fs.readFileSync(p, 'utf-8');
      content = doReplace(content);
      // rename the file if its class name changed
      let newFilename = f;
      for (const [k, v] of Object.entries(dict)) {
        if (/^[A-Z][a-zA-Z]+$/.test(k) && f.includes(k) && !f.includes(v)) {
           newFilename = newFilename.replace(k, v);
        }
      }
      if (newFilename !== f) {
        fs.writeFileSync(path.join(dir, newFilename), content);
        fs.unlinkSync(p);
      } else {
         fs.writeFileSync(p, content);
      }
    }
  }
}

processDir(path.join(__dirname, 'backend', 'src', 'main', 'java'));
console.log('Backend code translated');
