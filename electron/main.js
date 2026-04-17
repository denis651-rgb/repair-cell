const { app, BrowserWindow, dialog } = require('electron');
const { spawn } = require('child_process');
const fs = require('fs');
const http = require('http');
const path = require('path');

let mainWindow;
let backendProcess;
let isQuitting = false;
let ownsBackendProcess = false;
let backendLogPath = '';
let electronLogPath = '';

const BACKEND_PORT = 8080;
const BACKEND_HEALTH_URL = `http://127.0.0.1:${BACKEND_PORT}/actuator/health`;
const BACKEND_START_TIMEOUT_MS = 30000;

function getBackendJarPath() {
  if (app.isPackaged) {
    return path.join(process.resourcesPath, 'backend', 'repair-backend.jar');
  }
  return path.join(__dirname, '..', 'backend', 'target', 'repair-backend-1.0.0.jar');
}

function getFrontendEntry() {
  if (app.isPackaged) {
    return path.join(__dirname, '..', 'frontend', 'dist', 'index.html');
  }
  return 'http://localhost:5173';
}

function getBundledJavaPath() {
  if (!app.isPackaged) {
    return null;
  }

  const bundledJava = path.join(process.resourcesPath, 'runtime', 'bin', 'java.exe');
  return fs.existsSync(bundledJava) ? bundledJava : null;
}

function getJavaCommand() {
  return getBundledJavaPath() || 'java';
}

function getAppStoragePath() {
  return app.getPath('userData');
}

function toPortablePath(value) {
  return value.replace(/\\/g, '/');
}

function ensureAppDirectories() {
  const appStoragePath = getAppStoragePath();
  fs.mkdirSync(path.join(appStoragePath, 'data'), { recursive: true });
  fs.mkdirSync(path.join(appStoragePath, 'backups'), { recursive: true });
  fs.mkdirSync(path.join(appStoragePath, 'logs'), { recursive: true });
}

function initializeLogPaths() {
  const logsPath = path.join(getAppStoragePath(), 'logs');
  backendLogPath = path.join(logsPath, 'backend.log');
  electronLogPath = path.join(logsPath, 'electron.log');
  fs.writeFileSync(backendLogPath, '', { flag: 'w' });
  fs.writeFileSync(electronLogPath, '', { flag: 'w' });
}

function appendLog(filePath, message) {
  const line = `[${new Date().toISOString()}] ${message}\n`;
  fs.appendFileSync(filePath, line, 'utf8');
}

function logElectron(message) {
  if (!electronLogPath) {
    return;
  }
  appendLog(electronLogPath, message);
}

function logBackendChunk(prefix, chunk) {
  if (!backendLogPath) {
    return;
  }
  const text = chunk.toString().replace(/\r?\n$/, '');
  if (text) {
    appendLog(backendLogPath, `${prefix} ${text}`);
  }
}

function buildFailureMessage(title, detail) {
  const logHint = electronLogPath
    ? `\n\nRevisa los logs en:\n${backendLogPath}\n${electronLogPath}`
    : '';

  return `${detail}${logHint}`;
}

function waitForBackendReady(timeoutMs = BACKEND_START_TIMEOUT_MS) {
  const startedAt = Date.now();

  return new Promise((resolve, reject) => {
    const probe = () => {
      const request = http.get(BACKEND_HEALTH_URL, (response) => {
        response.resume();

        if (response.statusCode === 200) {
          logElectron(`Backend listo en ${BACKEND_HEALTH_URL}`);
          resolve();
          return;
        }

        retry();
      });

      request.on('error', retry);
      request.setTimeout(1500, () => {
        request.destroy();
        retry();
      });
    };

    const retry = () => {
      if (Date.now() - startedAt >= timeoutMs) {
        reject(new Error('El backend no respondio a tiempo.'));
        return;
      }
      setTimeout(probe, 700);
    };

    probe();
  });
}

function probeBackendHealth(timeoutMs = 1500) {
  return new Promise((resolve) => {
    const request = http.get(BACKEND_HEALTH_URL, (response) => {
      response.resume();
      resolve(response.statusCode === 200);
    });

    request.on('error', () => resolve(false));
    request.setTimeout(timeoutMs, () => {
      request.destroy();
      resolve(false);
    });
  });
}

function startBackend() {
  const jarPath = getBackendJarPath();

  if (!fs.existsSync(jarPath)) {
    throw new Error(`No se encontro el backend empaquetado en ${jarPath}`);
  }

  ensureAppDirectories();
  initializeLogPaths();

  const appStoragePath = getAppStoragePath();
  const dbPath = toPortablePath(path.join(appStoragePath, 'data', 'repair-shop.db'));
  const backupPath = toPortablePath(path.join(appStoragePath, 'backups'));
  const javaCommand = getJavaCommand();
  const javaArgs = ['-jar', jarPath];

  logElectron(`Iniciando backend con ${javaCommand} ${javaArgs.join(' ')}`);
  logElectron(`APP_STORAGE_DIR=${toPortablePath(appStoragePath)}`);
  logElectron(`DB_URL=jdbc:sqlite:${dbPath}`);
  logElectron(`APP_BACKUP_DIRECTORY=${backupPath}`);

  backendProcess = spawn(javaCommand, javaArgs, {
    cwd: path.dirname(jarPath),
    windowsHide: true,
    stdio: ['ignore', 'pipe', 'pipe'],
    env: {
      ...process.env,
      SPRING_PROFILES_ACTIVE: app.isPackaged ? 'prod' : 'dev',
      SERVER_PORT: String(BACKEND_PORT),
      APP_STORAGE_DIR: toPortablePath(appStoragePath),
      DB_URL: `jdbc:sqlite:${dbPath}`,
      APP_BACKUP_DIRECTORY: backupPath
    }
  });

  ownsBackendProcess = true;

  backendProcess.stdout.on('data', (chunk) => logBackendChunk('[stdout]', chunk));
  backendProcess.stderr.on('data', (chunk) => logBackendChunk('[stderr]', chunk));

  backendProcess.on('error', (error) => {
    logElectron(`Error al iniciar backend: ${error.stack || error.message}`);
    dialog.showErrorBox(
      'No se pudo iniciar el backend',
      buildFailureMessage(
        'No se pudo iniciar el backend',
        `Electron no pudo ejecutar Java.\n\nDetalle: ${error.message}\n\nInstala Java 17 o agrega un runtime embebido en resources/runtime.`
      )
    );
  });

  backendProcess.on('close', (code) => {
    logElectron(`Backend finalizado con codigo ${code}`);
    if (!isQuitting && ownsBackendProcess && code !== 0) {
      dialog.showErrorBox(
        'El backend se cerro inesperadamente',
        buildFailureMessage(
          'El backend se cerro inesperadamente',
          `El proceso local finalizo con codigo ${code}.`
        )
      );
    }
  });
}

function createWindow() {
  mainWindow = new BrowserWindow({
    width: 1440,
    height: 920,
    minWidth: 1180,
    minHeight: 760,
    autoHideMenuBar: true,
    backgroundColor: '#f4f7fb',
    webPreferences: {
      contextIsolation: true,
      nodeIntegration: false
    }
  });

  const frontendEntry = getFrontendEntry();
  if (app.isPackaged) {
    mainWindow.loadFile(frontendEntry);
    return;
  }

  mainWindow.loadURL(frontendEntry);
}

app.whenReady().then(async () => {
  try {
    const existingBackend = await probeBackendHealth();
    if (existingBackend) {
      logElectron(`Se reutilizara un backend ya activo en ${BACKEND_HEALTH_URL}`);
    } else {
      startBackend();
    }

    await waitForBackendReady();
    createWindow();
  } catch (error) {
    logElectron(`Fallo al abrir la aplicacion: ${error.stack || error.message}`);
    dialog.showErrorBox(
      'No se pudo abrir la aplicacion',
      buildFailureMessage(
        'No se pudo abrir la aplicacion',
        `${error.message}\n\nRevisa que el backend este empaquetado y que Java 17 este disponible.`
      )
    );
    app.quit();
  }
});

app.on('window-all-closed', () => {
  isQuitting = true;
  if (backendProcess && ownsBackendProcess) {
    backendProcess.kill();
  }
  if (process.platform !== 'darwin') {
    app.quit();
  }
});

app.on('before-quit', () => {
  isQuitting = true;
  if (backendProcess && ownsBackendProcess) {
    backendProcess.kill();
  }
});
