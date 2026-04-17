const fs = require('fs');
const path = require('path');

const dict = {
  // Add missing combinations
  'InventoryCategoryController': 'CategoriaInventarioController',
  'CustomerController': 'ClienteController',
  'DeviceController': 'DispositivoController',
  'StockMovementController': 'MovimientoStockController',
  'RepairOrderController': 'OrdenReparacionController',
  'InventoryProductController': 'ProductoInventarioController',
  'TipoMovimientoStockType': 'TipoMovimientoStock',
  'MovimientoStockType': 'TipoMovimientoStock', // enum
  'ParteOrdenReparacionPart': 'ParteOrdenReparacion', // class inside domain/OrdenReparacionPart.java
  'OrdenReparacionPartRequest': 'ParteOrdenReparacionRequest',
  'ParteOrdenReparacionRequest': 'ParteOrdenReparacionRequest',
  'InventoryCategoryRepository': 'CategoriaInventarioRepository',
  'CustomerRepository': 'ClienteRepository',
  'DeviceRepository': 'DispositivoRepository',
  'AccountingEntryRepository': 'EntradaContableRepository',
  'StockMovementRepository': 'MovimientoStockRepository',
  'RepairOrderPartRepository': 'ParteOrdenReparacionRepository',
  'RepairOrderRepository': 'OrdenReparacionRepository',
  'InventoryProductRepository': 'ProductoInventarioRepository',
  'InventoryCategoryService': 'CategoriaInventarioService',
  'CustomerService': 'ClienteService',
  'DeviceService': 'DispositivoService',
  'RepairOrderService': 'OrdenReparacionService',
  'InventoryProductService': 'ProductoInventarioService',

  // Fix getter/setters that failed
  'getEntryType': 'getTipoEntrada',
  'getMonto': 'getMonto', // check AccountingService
  'builder': 'builder',
  'getCustomerId': 'getClienteId',
  'getDeviceId': 'getDispositivoId',
  'getParts': 'getPartes',
  'getProductId': 'getProductoId',
  'setId': 'setId',
  
  // also missed translations for Parts
  'OrdenReparacionPart': 'ParteOrdenReparacion',
  'RepairOrderPart': 'ParteOrdenReparacion',
  
  // fixing class rename
  'class CustomerController': 'class ClienteController',
  'class DeviceController': 'class DispositivoController',
  'class InventoryCategoryController': 'class CategoriaInventarioController',
  'class InventoryProductController': 'class ProductoInventarioController',
  'class RepairOrderController': 'class OrdenReparacionController',
  'class StockMovementController': 'class MovimientoStockController',
  
  'interface CustomerRepository': 'interface ClienteRepository',
  'interface DeviceRepository': 'interface DispositivoRepository',
  'interface InventoryCategoryRepository': 'interface CategoriaInventarioRepository',
  'interface InventoryProductRepository': 'interface ProductoInventarioRepository',
  'interface RepairOrderRepository': 'interface OrdenReparacionRepository',
  'interface RepairOrderPartRepository': 'interface ParteOrdenReparacionRepository',
  'interface StockMovementRepository': 'interface MovimientoStockRepository',
  'interface AccountingEntryRepository': 'interface EntradaContableRepository',
  
  'class CustomerService': 'class ClienteService',
  'class DeviceService': 'class DispositivoService',
  'class InventoryCategoryService': 'class CategoriaInventarioService',
  'class InventoryProductService': 'class ProductoInventarioService',
  'class RepairOrderService': 'class OrdenReparacionService'
};

function doReplace(content) {
  let str = content;
  const keys = Object.keys(dict).sort((a,b) => b.length - a.length);
  for(let k of keys) {
    const val = dict[k];
    // Simple replaceAll without word boundaries if it is complex, or use replaceAll
    str = str.split(k).join(val);
  }
  
  str = str.replace(/getParts\(\)/g, "getPartes()");
  str = str.replace(/getCustomerId\(\)/g, "getClienteId()");
  str = str.replace(/getDeviceId\(\)/g, "getDispositivoId()");
  str = str.replace(/getProductId\(\)/g, "getProductoId()");
  str = str.replace(/getEntryType\(\)/g, "getTipoEntrada()");

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
      
      // We also check for wrong class names inside files
      // Because we renamed files before, let's fix file names to match class names if needed
      const classMatch = content.match(/(?:public\s+)?(?:class|interface|enum)\s+([A-Za-z0-9_]+)/);
      let newFilename = f;
      if (classMatch) {
         newFilename = classMatch[1] + '.java';
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
console.log('Backend code fixed');
