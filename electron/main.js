const { app, BrowserWindow } = require('electron');
const { spawn } = require('child_process');
const path = require('path');

let mainWindow;
let backendProcess;

function getBackendJarPath() {
  if (app.isPackaged) {
    return path.join(process.resourcesPath, 'backend', 'repair-backend.jar');
  }
  return path.join(__dirname, '..', 'backend', 'target', 'repair-backend-1.0.0.jar');
}

function getFrontendEntry() {
  if (app.isPackaged) {
    return `file://${path.join(__dirname, '..', 'frontend', 'dist', 'index.html')}`;
  }
  return 'http://localhost:5173';
}

function startBackend() {
  const jarPath = getBackendJarPath();
  backendProcess = spawn('java', ['-jar', jarPath], {
    cwd: path.dirname(jarPath),
    windowsHide: true,
    stdio: 'inherit'
  });

  backendProcess.on('close', (code) => {
    console.log(`Backend finalizado con código ${code}`);
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

  mainWindow.loadURL(getFrontendEntry());
}

app.whenReady().then(() => {
  startBackend();
  setTimeout(createWindow, app.isPackaged ? 3500 : 1500);
});

app.on('window-all-closed', () => {
  if (backendProcess) backendProcess.kill();
  if (process.platform !== 'darwin') app.quit();
});

app.on('before-quit', () => {
  if (backendProcess) backendProcess.kill();
});
