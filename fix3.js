const fs = require('fs');
const path = require('path');

const dict = {
  'customerId': 'clienteId',
  'deviceId': 'dispositivoId',
  'productId': 'productoId',
  'parts': 'partes',
  'getCustomerId': 'getClienteId',
  'getDeviceId': 'getDispositivoId',
  'getProductId': 'getProductoId',
  'getParts': 'getPartes',
  'setParts': 'setPartes',
  'getMonto(T)': 'getMonto()'
};

function doReplace(content) {
  let str = content;
  // Fix the "invalid method reference getMonto(T)" in AccountingService.java
  // In Java, stream().mapToDouble(EntradaContable::getMonto).sum() might be complaining if getMonto is not public? No, the issue is lombok @Getter? We'll see.
  
  for(let k of Object.keys(dict)) {
    const val = dict[k];
    str = str.split(k).join(val);
  }
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

      if (f === 'public.java') {
         fs.writeFileSync(path.join(dir, 'EntidadBase.java'), content);
         fs.unlinkSync(p);
      } else {
         fs.writeFileSync(p, content);
      }
    }
  }
}

processDir(path.join(__dirname, 'backend', 'src', 'main', 'java'));
console.log('Backend code fixed 3');
