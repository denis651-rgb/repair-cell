const { app, BrowserWindow, dialog } = require('electron');
const { spawn, execFile } = require('child_process');
const fs = require('fs');
const http = require('http');
const path = require('path');

let mainWindow;
let backendProcess;
let isQuitting = false;
let ownsBackendProcess = false;
let backendLogPath = '';
let electronLogPath = '';
let isApplyingPendingRestore = false;
let isClearingSessionForClose = false;

const BACKEND_PORT = 8080;
const BACKEND_HEALTH_URL = `http://127.0.0.1:${BACKEND_PORT}/actuator/health`;
const BACKEND_START_TIMEOUT_MS = 120000;
const BACKEND_ALLOWED_ORIGINS = 'http://localhost:5173,http://localhost:5174,http://localhost:3000,null';
const DATABASE_FILE_NAME = 'repair-shop.db';

const gotTheLock = app.requestSingleInstanceLock();

if (!gotTheLock) {
  app.quit();
} else {
  app.on('second-instance', () => {
    if (mainWindow) {
      if (mainWindow.isMinimized()) {
        mainWindow.restore();
      }
      mainWindow.focus();
    }
  });
}

function delay(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

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

function getAppIconPath() {
  return path.join(__dirname, 'assets', 'icon.png');
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
  fs.mkdirSync(path.join(appStoragePath, 'restore'), { recursive: true });
  fs.mkdirSync(path.join(appStoragePath, 'restore', 'staging'), { recursive: true });
}

function initializeLogPaths() {
  const logsPath = path.join(getAppStoragePath(), 'logs');
  fs.mkdirSync(logsPath, { recursive: true });

  backendLogPath = path.join(logsPath, 'backend.log');
  electronLogPath = path.join(logsPath, 'electron.log');

  fs.writeFileSync(backendLogPath, '', { flag: 'w' });
  fs.writeFileSync(electronLogPath, '', { flag: 'w' });
}

function appendLog(filePath, message) {
  if (!filePath) {
    return;
  }

  const line = `[${new Date().toISOString()}] ${message}\n`;
  fs.appendFileSync(filePath, line, 'utf8');
}

function logElectron(message) {
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

function getPendingRestorePlanPath() {
  return path.join(getAppStoragePath(), 'restore', 'pending-restore.json');
}

function getLastRestoreResultPath() {
  return path.join(getAppStoragePath(), 'restore', 'last-restore-result.json');
}

function getBackendPidPath() {
  return path.join(getAppStoragePath(), 'backend.pid');
}

function readJsonFile(filePath) {
  if (!fs.existsSync(filePath)) {
    return null;
  }

  return JSON.parse(fs.readFileSync(filePath, 'utf8'));
}

function writeJsonFile(filePath, payload) {
  fs.writeFileSync(filePath, JSON.stringify(payload, null, 2), 'utf8');
}

function removePathIfExists(filePath) {
  if (fs.existsSync(filePath)) {
    fs.rmSync(filePath, { force: true, recursive: true });
  }
}
function cleanupSqliteSidecarFiles(dbPath) {
  if (!dbPath) {
    return;
  }

  [
    `${dbPath}-wal`,
    `${dbPath}-shm`,
    `${dbPath}-journal`
  ].forEach((sidecarPath) => {
    try {
      removePathIfExists(sidecarPath);
    } catch (error) {
      logElectron(`No se pudo eliminar archivo auxiliar SQLite ${sidecarPath}: ${error.message}`);
    }
  });
}

function isProcessAlive(pid) {
  if (!pid) {
    return false;
  }

  try {
    process.kill(pid, 0);
    return true;
  } catch {
    return false;
  }
}

function killProcessTree(pid) {
  return new Promise((resolve) => {
    if (!pid) {
      resolve();
      return;
    }

    if (process.platform === 'win32') {
      execFile('taskkill', ['/PID', String(pid), '/T', '/F'], () => resolve());
      return;
    }

    try {
      process.kill(pid, 'SIGTERM');
    } catch {
      // Ignorar si el proceso ya no existe.
    }

    resolve();
  });
}

async function cleanupStaleBackendProcessIfNeeded() {
  const pidPath = getBackendPidPath();

  if (!fs.existsSync(pidPath)) {
    return;
  }

  const rawPid = fs.readFileSync(pidPath, 'utf8').trim();
  const pid = Number(rawPid);

  if (!pid || Number.isNaN(pid)) {
    removePathIfExists(pidPath);
    return;
  }

  if (isProcessAlive(pid)) {
    logElectron(`Cerrando backend anterior con PID ${pid}.`);
    await killProcessTree(pid);
    await delay(1500);
  }

  removePathIfExists(pidPath);
}

function applyPendingRestoreIfNeeded() {
  const planPath = getPendingRestorePlanPath();
  if (!fs.existsSync(planPath)) {
    return false;
  }

  isApplyingPendingRestore = true;

  try {
    const plan = readJsonFile(planPath);
    if (!plan || !plan.sourceDatabasePath || !plan.targetDatabasePath) {
      throw new Error('El plan de restauracion pendiente esta incompleto.');
    }

    const sourcePath = plan.sourceDatabasePath;
    const targetPath = plan.targetDatabasePath;
    const tempTargetPath = `${targetPath}.restore-tmp`;
    const rollbackPath = `${targetPath}.rollback`;
    const sourceType = plan.sourceType || 'LOCAL';
    const displaySource = plan.displaySource || sourcePath;

    if (!fs.existsSync(sourcePath)) {
      throw new Error(`No existe el archivo de backup preparado en ${sourcePath}.`);
    }

    removePathIfExists(tempTargetPath);
    removePathIfExists(rollbackPath);

    // Importante para SQLite:
    // antes de reemplazar repair-shop.db eliminamos archivos auxiliares viejos.
    cleanupSqliteSidecarFiles(targetPath);

    fs.copyFileSync(sourcePath, tempTargetPath);

    if (fs.existsSync(targetPath)) {
      fs.renameSync(targetPath, rollbackPath);
    }

    fs.renameSync(tempTargetPath, targetPath);

    // Limpieza defensiva después de restaurar, antes de reiniciar Spring Boot.
    cleanupSqliteSidecarFiles(targetPath);

    removePathIfExists(rollbackPath);
    removePathIfExists(planPath);

    writeJsonFile(getLastRestoreResultPath(), {
      ok: true,
      message: sourceType === 'DRIVE'
        ? 'La restauracion desde Drive se aplico correctamente y el backend fue reiniciado.'
        : 'La restauracion local se aplico correctamente y el backend fue reiniciado.',
      restoredAt: new Date().toISOString(),
      restoredFrom: displaySource,
      backupBeforeRestorePath: plan.backupBeforeRestorePath || ''
    });

    logElectron(`Restauracion ${sourceType === 'DRIVE' ? 'desde Drive' : 'local'} aplicada correctamente desde ${displaySource}`);
    return true;
  } catch (error) {
    const plan = readJsonFile(planPath);

    if (plan && plan.targetDatabasePath) {
      const tempTargetPath = `${plan.targetDatabasePath}.restore-tmp`;
      const rollbackPath = `${plan.targetDatabasePath}.rollback`;

      try {
        cleanupSqliteSidecarFiles(plan.targetDatabasePath);
        removePathIfExists(tempTargetPath);

        if (fs.existsSync(rollbackPath)) {
          removePathIfExists(plan.targetDatabasePath);
          fs.renameSync(rollbackPath, plan.targetDatabasePath);
          cleanupSqliteSidecarFiles(plan.targetDatabasePath);
        }
      } catch (rollbackError) {
        logElectron(`No se pudo revertir la restauracion fallida: ${rollbackError.message}`);
      }
    }

    writeJsonFile(getLastRestoreResultPath(), {
      ok: false,
      message: `La restauracion fallo: ${error.message}`,
      restoredAt: new Date().toISOString(),
      restoredFrom: plan?.sourceDatabasePath || '',
      backupBeforeRestorePath: plan?.backupBeforeRestorePath || ''
    });

    removePathIfExists(planPath);
    logElectron(`Fallo al aplicar restauracion pendiente: ${error.stack || error.message}`);
    return false;
  } finally {
    isApplyingPendingRestore = false;
  }
}

function probeBackendHealth(timeoutMs = 1500) {
  return new Promise((resolve) => {
    let resolved = false;

    const finish = (value) => {
      if (!resolved) {
        resolved = true;
        resolve(value);
      }
    };

    const request = http.get(BACKEND_HEALTH_URL, (response) => {
      response.resume();
      finish(response.statusCode === 200);
    });

    request.on('error', () => finish(false));
    request.setTimeout(timeoutMs, () => {
      request.destroy();
      finish(false);
    });
  });
}

function waitForBackendReady(timeoutMs = BACKEND_START_TIMEOUT_MS) {
  const startedAt = Date.now();

  return new Promise((resolve, reject) => {
    let finished = false;

    const finishOk = () => {
      if (!finished) {
        finished = true;
        logElectron(`Backend listo en ${BACKEND_HEALTH_URL}`);
        resolve();
      }
    };

    const finishError = (error) => {
      if (!finished) {
        finished = true;
        reject(error);
      }
    };

    const probe = () => {
      if (finished) {
        return;
      }

      const request = http.get(BACKEND_HEALTH_URL, (response) => {
        response.resume();

        if (response.statusCode === 200) {
          finishOk();
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
      if (finished) {
        return;
      }

      if (Date.now() - startedAt >= timeoutMs) {
        finishError(new Error('El backend no respondio a tiempo.'));
        return;
      }

      setTimeout(probe, 700);
    };

    probe();
  });
}

async function clearRendererSession({ navigateToLogin = false, reload = false } = {}) {
  if (!mainWindow || mainWindow.isDestroyed()) {
    return;
  }

  try {
    await mainWindow.webContents.executeJavaScript(`
      try {
        localStorage.removeItem('token');
        localStorage.removeItem('user');
        localStorage.removeItem('auth');
        localStorage.removeItem('usuarioActual');
        sessionStorage.clear();

        if (${navigateToLogin ? 'true' : 'false'}) {
          if (window.location.protocol === 'file:') {
            window.location.hash = '#/login';
          } else {
            window.location.href = '/login';
          }
        }
      } catch (error) {
        console.error('No se pudo limpiar la sesion local', error);
      }
    `);

    if (reload && !mainWindow.isDestroyed()) {
      mainWindow.webContents.reloadIgnoringCache();
    }
  } catch (error) {
    logElectron(`No se pudo limpiar la sesion local: ${error.message}`);
  }
}

async function clearSessionAndGoToLogin() {
  await clearRendererSession({ navigateToLogin: true, reload: true });
}

function startBackend() {
  if (backendProcess && backendProcess.exitCode === null && !backendProcess.killed) {
    logElectron(`El backend ya se esta ejecutando con PID ${backendProcess.pid}. No se iniciara otro proceso.`);
    return backendProcess;
  }

  const jarPath = getBackendJarPath();

  if (!fs.existsSync(jarPath)) {
    throw new Error(`No se encontro el backend empaquetado en ${jarPath}`);
  }

  ensureAppDirectories();

  const appStoragePath = getAppStoragePath();
  const dbPath = toPortablePath(path.join(appStoragePath, 'data', DATABASE_FILE_NAME));
  const backupPath = toPortablePath(path.join(appStoragePath, 'backups'));
  const javaCommand = getJavaCommand();
  const javaArgs = ['-jar', jarPath];

  logElectron(`Iniciando backend con ${javaCommand} ${javaArgs.join(' ')}`);
  logElectron(`APP_STORAGE_DIR=${toPortablePath(appStoragePath)}`);
  logElectron(`DB_URL=jdbc:sqlite:${dbPath}`);
  logElectron(`APP_BACKUP_DIRECTORY=${backupPath}`);
  logElectron(`APP_CORS_ALLOWED_ORIGINS=${BACKEND_ALLOWED_ORIGINS}`);

  const child = spawn(javaCommand, javaArgs, {
    cwd: path.dirname(jarPath),
    windowsHide: true,
    stdio: ['ignore', 'pipe', 'pipe'],
    env: {
      ...process.env,
      SPRING_PROFILES_ACTIVE: app.isPackaged ? 'prod' : 'dev',
      SERVER_PORT: String(BACKEND_PORT),
      APP_STORAGE_DIR: toPortablePath(appStoragePath),
      DB_URL: `jdbc:sqlite:${dbPath}`,
      APP_DB_PATH: dbPath,
      APP_BACKUP_DIRECTORY: backupPath,
      APP_CORS_ALLOWED_ORIGINS: BACKEND_ALLOWED_ORIGINS
    }
  });

  backendProcess = child;
  ownsBackendProcess = true;

  fs.writeFileSync(getBackendPidPath(), String(child.pid), 'utf8');
  logElectron(`Backend iniciado con PID ${child.pid}`);

  child.stdout.on('data', (chunk) => logBackendChunk('[stdout]', chunk));
  child.stderr.on('data', (chunk) => logBackendChunk('[stderr]', chunk));

  child.on('error', (error) => {
    logElectron(`Error al iniciar backend: ${error.stack || error.message}`);

    dialog.showErrorBox(
      'No se pudo iniciar el backend',
      buildFailureMessage(
        'No se pudo iniciar el backend',
        `Electron no pudo ejecutar Java.\n\nDetalle: ${error.message}\n\nInstala Java 17 o agrega un runtime embebido en resources/runtime.`
      )
    );
  });

  child.on('close', async (code) => {
    logElectron(`Backend finalizado con codigo ${code}`);

    try {
      const pidPath = getBackendPidPath();
      if (fs.existsSync(pidPath)) {
        const storedPid = fs.readFileSync(pidPath, 'utf8').trim();
        if (storedPid === String(child.pid)) {
          removePathIfExists(pidPath);
        }
      }
    } catch {
      // Ignorar error de limpieza de PID.
    }

    if (backendProcess === child) {
      backendProcess = null;
    }

    if (!isQuitting && fs.existsSync(getPendingRestorePlanPath())) {
      const restoreApplied = applyPendingRestoreIfNeeded();

      try {
        startBackend();
        await waitForBackendReady(BACKEND_START_TIMEOUT_MS);
        await clearSessionAndGoToLogin();

        if (mainWindow && !mainWindow.isDestroyed()) {
          dialog.showMessageBox(mainWindow, {
            type: 'info',
            title: 'Restauracion completada',
            message: restoreApplied
              ? 'La restauracion se aplico correctamente. Por seguridad, vuelve a iniciar sesion.'
              : 'La restauracion no se pudo aplicar correctamente. Revisa el resultado en la seccion de respaldos.'
          });
        }
      } catch (error) {
        logElectron(`No se pudo reiniciar despues de restaurar: ${error.stack || error.message}`);

        if (backendProcess && backendProcess.pid) {
          await killProcessTree(backendProcess.pid);
          backendProcess = null;
        }

        dialog.showErrorBox(
          'No se pudo reiniciar despues de restaurar',
          buildFailureMessage(
            'No se pudo reiniciar despues de restaurar',
            restoreApplied
              ? `La restauracion se aplico, pero el backend no pudo reiniciarse.\n\nDetalle: ${error.message}`
              : `La restauracion fallo y el backend no pudo reiniciarse.\n\nDetalle: ${error.message}`
          )
        );
      }

      return;
    }

    if (!isQuitting && !isApplyingPendingRestore && code !== 0) {
      dialog.showErrorBox(
        'El backend se cerro inesperadamente',
        buildFailureMessage(
          'El backend se cerro inesperadamente',
          `El proceso local finalizo con codigo ${code}.`
        )
      );
    }
  });

  return child;
}

function createWindow() {
  mainWindow = new BrowserWindow({
    width: 1440,
    height: 920,
    minWidth: 1180,
    minHeight: 760,
    icon: getAppIconPath(),
    autoHideMenuBar: true,
    backgroundColor: '#f4f7fb',
    webPreferences: {
      contextIsolation: true,
      nodeIntegration: false
    }
  });

  mainWindow.on('close', async (event) => {
    if (isClearingSessionForClose) {
      return;
    }

    event.preventDefault();
    isClearingSessionForClose = true;

    try {
      await clearRendererSession();
      logElectron('Sesion local limpiada al cerrar la ventana.');
    } finally {
      if (mainWindow && !mainWindow.isDestroyed()) {
        mainWindow.destroy();
      }
    }
  });

  mainWindow.on('closed', () => {
    mainWindow = null;
    isClearingSessionForClose = false;
  });

  const frontendEntry = getFrontendEntry();

  if (app.isPackaged) {
    mainWindow.loadFile(frontendEntry);
  } else {
    mainWindow.loadURL(frontendEntry);
  }

  return mainWindow;
}

async function stopOwnedBackend() {
  if (!backendProcess || !ownsBackendProcess || !backendProcess.pid) {
    return;
  }

  const pid = backendProcess.pid;

  try {
    logElectron(`Cerrando backend con PID ${pid}.`);
    await killProcessTree(pid);
  } catch (error) {
    logElectron(`No se pudo cerrar el backend con PID ${pid}: ${error.message}`);
  } finally {
    backendProcess = null;
    removePathIfExists(getBackendPidPath());
  }
}

async function bootstrapApplication() {
  try {
    ensureAppDirectories();
    initializeLogPaths();

    logElectron('Iniciando aplicacion de escritorio.');

    await cleanupStaleBackendProcessIfNeeded();

    const restoredAtStartup = applyPendingRestoreIfNeeded();
    const existingBackend = await probeBackendHealth(3000);

    if (existingBackend) {
      logElectron(`Se reutilizara un backend ya activo en ${BACKEND_HEALTH_URL}`);
      ownsBackendProcess = false;
    } else {
      startBackend();
    }

    await waitForBackendReady(BACKEND_START_TIMEOUT_MS);

    const window = createWindow();

    window.webContents.once('did-finish-load', () => {
      clearSessionAndGoToLogin();
    });
  } catch (error) {
    logElectron(`Fallo al abrir la aplicacion: ${error.stack || error.message}`);

    if (backendProcess && backendProcess.pid && ownsBackendProcess) {
      await killProcessTree(backendProcess.pid);
      backendProcess = null;
      removePathIfExists(getBackendPidPath());
    }

    dialog.showErrorBox(
      'No se pudo abrir la aplicacion',
      buildFailureMessage(
        'No se pudo abrir la aplicacion',
        `${error.message}\n\nRevisa que el backend este empaquetado, que Java este disponible y que el puerto ${BACKEND_PORT} no este ocupado.`
      )
    );

    app.quit();
  }
}

if (gotTheLock) {
  app.whenReady().then(bootstrapApplication);

  app.on('window-all-closed', () => {
    isQuitting = true;
    stopOwnedBackend().finally(() => {
      if (process.platform !== 'darwin') {
        app.quit();
      }
    });
  });

  app.on('before-quit', () => {
    isQuitting = true;
    stopOwnedBackend();
  });
}
