import { useEffect, useMemo, useRef, useState } from 'react';
import {
  Cloud,
  Clock3,
  DatabaseBackup,
  X,
  FolderSync,
  HardDriveDownload,
  LoaderCircle,
  RefreshCcw,
  Save,
  ShieldCheck,
  Unplug,
  UploadCloud,
} from 'lucide-react';
import { api } from '../api/api';
import PageHeader from '../components/PageHeader';
import EmptyState from '../components/common/EmptyState';
import { formatDateTime } from '../utils/formatters';
import '../styles/pages/backups.css';

const initialSettings = {
  enabled: true,
  cron: '0 0 1 * * *',
  directory: './backups',
  zipEnabled: true,
  retentionDays: 30,
  googleDriveEnabled: false,
  googleDriveFolderId: '',
  googleDriveFolderName: '',
  googleOauthClientId: '',
  googleOauthClientSecret: '',
  googleOauthConnected: false,
  googleOauthConnectedAt: null,
  googleDriveReady: false,
  lastAutomaticBackupAt: null,
  nextAutomaticBackupAt: null,
};

function getStatusTone(status) {
  switch (status) {
    case 'REMOTE_OK':
      return 'is-success';
    case 'PENDING_UPLOAD':
      return 'is-warning';
    case 'FAILED_UPLOAD':
      return 'is-danger';
    default:
      return 'is-neutral';
  }
}

function getStatusLabel(status) {
  switch (status) {
    case 'LOCAL_OK':
      return 'Local OK';
    case 'REMOTE_OK':
      return 'Subido a Drive';
    case 'PENDING_UPLOAD':
      return 'Pendiente de subida';
    case 'FAILED_UPLOAD':
      return 'Error de subida';
    default:
      return status || 'Sin estado';
  }
}

function getOriginLabel(origin) {
  switch (origin) {
    case 'MANUAL':
      return 'Manual';
    case 'PROGRAMADO':
      return 'Automatico';
    case 'REINTENTO':
      return 'Reintento';
    default:
      return origin || 'N/D';
  }
}

function formatBytes(value) {
  const bytes = Number(value || 0);
  if (!bytes) return '0 B';
  const units = ['B', 'KB', 'MB', 'GB'];
  const index = Math.min(Math.floor(Math.log(bytes) / Math.log(1024)), units.length - 1);
  const size = bytes / 1024 ** index;
  return `${size.toFixed(index === 0 ? 0 : 2)} ${units[index]}`;
}

export default function BackupsPage() {
  const [summary, setSummary] = useState(null);
  const [settings, setSettings] = useState(initialSettings);
  const [recordsPage, setRecordsPage] = useState({ content: [], totalPages: 0, number: 0 });
  const [driveTest, setDriveTest] = useState(null);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [running, setRunning] = useState(false);
  const [retrying, setRetrying] = useState(false);
  const [testingDrive, setTestingDrive] = useState(false);
  const [connectingDrive, setConnectingDrive] = useState(false);
  const [disconnectingDrive, setDisconnectingDrive] = useState(false);
  const [remoteBackups, setRemoteBackups] = useState([]);
  const [loadingRemoteBackups, setLoadingRemoteBackups] = useState(false);
  const [selectedRemoteFileId, setSelectedRemoteFileId] = useState('');
  const [remoteRestoreValidation, setRemoteRestoreValidation] = useState(null);
  const [validatingRemoteRestore, setValidatingRemoteRestore] = useState(false);
  const [restoreFile, setRestoreFile] = useState(null);
  const [restoreValidation, setRestoreValidation] = useState(null);
  const [validatingRestore, setValidatingRestore] = useState(false);
  const [executingRestore, setExecutingRestore] = useState(false);
  const [lastRestoreResult, setLastRestoreResult] = useState(null);
  const [notifications, setNotifications] = useState([]);
  const restoreNotificationKeyRef = useRef('');

  const pushNotification = (type, message, options = {}) => {
    if (!message) return;

    const id = `${Date.now()}-${Math.random().toString(36).slice(2, 8)}`;
    const duration = options.duration ?? (type === 'error' ? 7000 : 5000);
    setNotifications((current) => [...current, { id, type, message }]);

    if (duration > 0) {
      window.setTimeout(() => {
        setNotifications((current) => current.filter((item) => item.id !== id));
      }, duration);
    }
  };

  const removeNotification = (id) => {
    setNotifications((current) => current.filter((item) => item.id !== id));
  };

  const loadRemoteRestoreFiles = async (settingsData) => {
    if (!settingsData?.googleDriveReady) {
      setRemoteBackups([]);
      setSelectedRemoteFileId('');
      setRemoteRestoreValidation(null);
      return;
    }

    setLoadingRemoteBackups(true);
    try {
      const files = await api.get('/admin/backups/restore/drive/files');
      const nextFiles = files || [];
      setRemoteBackups(nextFiles);
      setSelectedRemoteFileId((current) => {
        if (current && nextFiles.some((file) => file.fileId === current)) {
          return current;
        }
        return nextFiles[0]?.fileId || '';
      });
    } finally {
      setLoadingRemoteBackups(false);
    }
  };

  const loadData = async (pagina = 0) => {
    setLoading(true);

    try {
      const [summaryData, settingsData, backupsData] = await Promise.all([
        api.get('/admin/backups/summary'),
        api.get('/admin/backups/settings'),
        api.get('/admin/backups', { pagina, tamano: 8 }),
      ]);

      setSummary(summaryData);
      setSettings({
        ...initialSettings,
        ...settingsData,
        retentionDays: Number(settingsData?.retentionDays || 30),
      });
      setRecordsPage(backupsData || { content: [], totalPages: 0, number: 0 });

      try {
        setLastRestoreResult(await api.get('/admin/backups/restore/last-result'));
      } catch {
        setLastRestoreResult(null);
      }

      try {
        await loadRemoteRestoreFiles(settingsData);
      } catch (remoteError) {
        setRemoteBackups([]);
        setSelectedRemoteFileId('');
        setRemoteRestoreValidation(null);
        pushNotification('error', remoteError.message);
      }
    } catch (err) {
      pushNotification('error', err.message);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadData();
  }, []);

  useEffect(() => {
    const handleMessage = async (event) => {
      if (!event?.data || event.data.type !== 'google-drive-oauth') return;

      setConnectingDrive(false);
      if (event.data.status === 'success') {
        pushNotification('success', event.data.message || 'Google Drive conectado correctamente.');
        setDriveTest(null);
      } else {
        pushNotification('error', event.data.message || 'No se pudo completar la conexion con Google Drive.');
      }

      await loadData(recordsPage.number || 0);
    };

    window.addEventListener('message', handleMessage);
    return () => window.removeEventListener('message', handleMessage);
  }, [recordsPage.number]);

  useEffect(() => {
    if (!lastRestoreResult?.available || !lastRestoreResult?.message) {
      return;
    }

    const key = `${lastRestoreResult.restoredAt || ''}-${lastRestoreResult.message}`;
    if (restoreNotificationKeyRef.current === key) {
      return;
    }

    restoreNotificationKeyRef.current = key;
    pushNotification(lastRestoreResult.ok ? 'success' : 'error', lastRestoreResult.message, { duration: 9000 });
  }, [lastRestoreResult]);

  const driveChecklist = useMemo(() => {
    return [
      {
        label: 'Drive habilitado',
        ready: Boolean(settings.googleDriveEnabled),
      },
      {
        label: 'Client ID configurado',
        ready: Boolean(settings.googleOauthClientId?.trim()),
      },
      {
        label: 'Client Secret configurado',
        ready: Boolean(settings.googleOauthClientSecret?.trim()),
      },
      {
        label: 'OAuth conectado',
        ready: Boolean(settings.googleOauthConnected),
      },
      {
        label: 'Carpeta de backups lista',
        ready: Boolean(settings.googleDriveFolderId?.trim()),
      },
    ];
  }, [settings.googleDriveEnabled, settings.googleOauthClientId, settings.googleOauthClientSecret, settings.googleOauthConnected, settings.googleDriveFolderId]);

  const handleChange = (field, value) => {
    setSettings((current) => ({
      ...current,
      [field]: value,
    }));
  };

  const buildSettingsPayload = () => ({
    enabled: Boolean(settings.enabled),
    cron: settings.cron,
    directory: settings.directory,
    zipEnabled: Boolean(settings.zipEnabled),
    retentionDays: Number(settings.retentionDays || 1),
    googleDriveEnabled: Boolean(settings.googleDriveEnabled),
    googleOauthClientId: settings.googleOauthClientId,
    googleOauthClientSecret: settings.googleOauthClientSecret,
  });

  const handleSaveSettings = async (event) => {
    event.preventDefault();
    setSaving(true);

    try {
      const saved = await api.put('/admin/backups/settings', buildSettingsPayload());
      setSettings((current) => ({
        ...current,
        ...saved,
        retentionDays: Number(saved?.retentionDays || current.retentionDays || 30),
      }));
      setSummary(await api.get('/admin/backups/summary'));
      pushNotification('success', 'Configuracion de respaldos actualizada correctamente.');
    } catch (err) {
      pushNotification('error', err.message);
    } finally {
      setSaving(false);
    }
  };

  const handleConnectDrive = async () => {
    setConnectingDrive(true);

    try {
      const response = await api.post('/admin/backups/oauth/start', buildSettingsPayload());
      const popup = window.open(
        response.authUrl,
        'google-drive-oauth',
        'width=560,height=720,menubar=no,toolbar=no,location=yes,status=no'
      );

      if (!popup) {
        setConnectingDrive(false);
        pushNotification('error', 'El navegador bloqueo la ventana emergente. Permite popups para continuar.');
        return;
      }

      pushNotification('success', 'Se abrio la autorizacion de Google. Completa el acceso en la ventana emergente.');
    } catch (err) {
      setConnectingDrive(false);
      pushNotification('error', err.message);
    }
  };

  const handleDisconnectDrive = async () => {
    setDisconnectingDrive(true);

    try {
      await api.post('/admin/backups/oauth/disconnect');
      setDriveTest(null);
      await loadData(recordsPage.number || 0);
      pushNotification('success', 'Google Drive se desconecto correctamente.');
    } catch (err) {
      pushNotification('error', err.message);
    } finally {
      setDisconnectingDrive(false);
    }
  };

  const handleTestDrive = async () => {
    setTestingDrive(true);

    try {
      const response = await api.post('/admin/backups/test-drive');
      setDriveTest(response);
      if (response.ok) {
        pushNotification('success', `Drive verificado: ${response.folderName || response.folderId}`);
      } else {
        pushNotification('error', response.message || 'No se pudo validar la conexion con Google Drive.');
      }
    } catch (err) {
      setDriveTest(null);
      pushNotification('error', err.message);
    } finally {
      setTestingDrive(false);
    }
  };

  const handleRunBackup = async () => {
    setRunning(true);

    try {
      const response = await api.post('/admin/backups/run');
      await loadData(recordsPage.number || 0);
      pushNotification('success', `Backup ejecutado: ${response.archivo}`);
    } catch (err) {
      pushNotification('error', err.message);
    } finally {
      setRunning(false);
    }
  };

  const handleValidateRestore = async () => {
    if (!restoreFile) {
      pushNotification('error', 'Debes seleccionar un archivo .db o .zip antes de validar la restauracion.');
      return;
    }

    setValidatingRestore(true);

    try {
      const formData = new FormData();
      formData.append('file', restoreFile);
      const response = await api.post('/admin/backups/restore/validate-local', formData);
      setRestoreValidation(response);
      pushNotification('success', 'Backup local validado correctamente. Ya puedes confirmar la restauracion.');
    } catch (err) {
      setRestoreValidation(null);
      pushNotification('error', err.message);
    } finally {
      setValidatingRestore(false);
    }
  };

  const handleValidateRemoteRestore = async () => {
    if (!selectedRemoteFileId) {
      pushNotification('error', 'Debes seleccionar un backup remoto de Drive antes de validarlo.');
      return;
    }

    setValidatingRemoteRestore(true);

    try {
      const response = await api.post('/admin/backups/restore/validate-drive', {
        fileId: selectedRemoteFileId,
      });
      setRemoteRestoreValidation(response);
      pushNotification('success', 'Backup remoto validado correctamente. Ya puedes confirmar la restauracion.');
    } catch (err) {
      setRemoteRestoreValidation(null);
      pushNotification('error', err.message);
    } finally {
      setValidatingRemoteRestore(false);
    }
  };

  const executeRestore = async (validation, sourceLabel) => {
    if (!validation?.sessionId) {
      pushNotification('error', `Primero valida el backup ${sourceLabel} antes de restaurar.`);
      return;
    }

    const confirmed = window.confirm(
      `Esta accion reemplazara la base activa usando el backup ${sourceLabel}, creara un backup previo y reiniciara el backend. Deseas continuar?`
    );
    if (!confirmed) return;

    setExecutingRestore(true);

    try {
      const response = await api.post('/admin/backups/restore/execute', {
        sessionId: validation.sessionId,
      });
      pushNotification('success', response.message, { duration: 9000 });
      setRestoreValidation(null);
      setRemoteRestoreValidation(null);
      setRestoreFile(null);
      window.setTimeout(() => {
        window.location.reload();
      }, 8000);
    } catch (err) {
      pushNotification('error', err.message);
    } finally {
      setExecutingRestore(false);
    }
  };

  const handleExecuteLocalRestore = async () => {
    await executeRestore(restoreValidation, 'local');
  };

  const handleExecuteRemoteRestore = async () => {
    await executeRestore(remoteRestoreValidation, 'remoto de Drive');
  };

  const handleRetryPending = async () => {
    setRetrying(true);

    try {
      const retried = await api.post('/admin/backups/retry-pending');
      await loadData(recordsPage.number || 0);
      pushNotification('success', `Reintento completado. Backups procesados: ${retried}.`);
    } catch (err) {
      pushNotification('error', err.message);
    } finally {
      setRetrying(false);
    }
  };

  const goToPage = async (pagina) => {
    await loadData(pagina);
  };

  return (
    <div className="page-stack backups-page">
      <div className="backups-toast-stack" aria-live="polite" aria-atomic="true">
        {notifications.map((notification) => (
          <div key={notification.id} className={`backups-toast is-${notification.type}`}>
            <p>{notification.message}</p>
            <button type="button" onClick={() => removeNotification(notification.id)} aria-label="Cerrar notificacion">
              <X size={16} />
            </button>
          </div>
        ))}
      </div>

      <PageHeader
        title="Respaldos"
        subtitle="Administra backups automaticos, historial local y la conexion de Google Drive desde un solo lugar."
      >
        <div className="backups-header-actions">
          <button className="secondary backups-ghost-button" onClick={() => loadData(recordsPage.number || 0)} disabled={loading}>
            <RefreshCcw size={18} />
            Actualizar
          </button>
          <button className="backups-primary-button" onClick={handleRunBackup} disabled={running || loading}>
            {running ? <LoaderCircle size={18} className="backups-spin" /> : <HardDriveDownload size={18} />}
            Ejecutar backup ahora
          </button>
        </div>
      </PageHeader>

      <section className="backups-hero-card">
        <div className="backups-hero-copy">
          <span className="backups-kicker">Seguridad operativa</span>
          <h2>{loading ? 'Cargando estado de respaldos...' : 'Controla copias locales, automatizacion y subida remota'}</h2>
          <p>El sistema guarda historial persistente de respaldos, reintenta subidas pendientes y puede dejar lista la integracion con Google Drive personal.</p>
        </div>
        <div className={`backups-hero-badge ${summary?.googleDriveReady ? 'is-ready' : 'is-pending'}`}>
          <Cloud size={18} />
          {summary?.googleDriveReady ? 'Drive listo para usarse' : 'Drive aun incompleto'}
        </div>
      </section>

      <section className="backups-kpi-grid">
        <article className="backups-kpi-card">
          <div className="backups-kpi-icon icon-soft"><DatabaseBackup size={20} /></div>
          <span className="backups-kpi-label">Total de backups</span>
          <strong className="backups-kpi-value">{summary?.totalBackups ?? 0}</strong>
          <p>Registros persistidos en la base local.</p>
        </article>

        <article className="backups-kpi-card">
          <div className="backups-kpi-icon icon-warning"><UploadCloud size={20} /></div>
          <span className="backups-kpi-label">Pendientes</span>
          <strong className="backups-kpi-value">{summary?.pendingUploads ?? 0}</strong>
          <p>Respaldos esperando subir a Drive.</p>
        </article>

        <article className="backups-kpi-card backups-kpi-card-accent">
          <div className="backups-kpi-icon icon-soft"><Clock3 size={20} /></div>
          <span className="backups-kpi-label">Siguiente ejecucion</span>
          <strong className="backups-kpi-value backups-kpi-small-value">{summary?.nextAutomaticBackupAt ? formatDateTime(summary.nextAutomaticBackupAt) : 'No programada'}</strong>
          <p>Calculada desde la expresion cron actual.</p>
        </article>

        <article className="backups-kpi-card">
          <div className="backups-kpi-icon icon-success"><ShieldCheck size={20} /></div>
          <span className="backups-kpi-label">Ultimo estado</span>
          <strong className="backups-kpi-value backups-kpi-small-value">{summary?.lastBackupStatus ? getStatusLabel(summary.lastBackupStatus) : 'Sin backups'}</strong>
          <p>{summary?.lastBackupAt ? formatDateTime(summary.lastBackupAt) : 'Todavia no hay ejecucion registrada.'}</p>
        </article>
      </section>

      <section className="backups-grid">
        <article className="card backups-panel backups-summary-panel">
          <div className="backups-panel-header">
            <div>
              <h3>Resumen operativo</h3>
              <p>Lectura rapida del ultimo respaldo y la salud de la configuracion.</p>
            </div>
            <span className="chip">{summary?.automaticEnabled ? 'Automatico activo' : 'Automatico pausado'}</span>
          </div>

          <div className="backups-summary-list">
            <div className="backups-summary-item">
              <span>Directorio local</span>
              <strong>{summary?.backupDirectory || settings.directory}</strong>
            </div>
            <div className="backups-summary-item">
              <span>Ultimo respaldo</span>
              <strong>{summary?.lastBackupAt ? formatDateTime(summary.lastBackupAt) : 'Sin registros'}</strong>
            </div>
            <div className="backups-summary-item">
              <span>Mensaje reciente</span>
              <strong>{summary?.lastBackupMessage || 'Aun no hay mensajes de respaldo.'}</strong>
            </div>
            <div className="backups-summary-item">
              <span>Enlace remoto</span>
              <strong>{summary?.lastRemoteLocation || 'Todavia no hay ubicacion remota.'}</strong>
            </div>
          </div>

          <div className="backups-quick-actions">
            <button className="secondary" onClick={handleRetryPending} disabled={retrying || loading || !summary?.pendingUploads}>
              {retrying ? <LoaderCircle size={18} className="backups-spin" /> : <FolderSync size={18} />}
              Reintentar pendientes
            </button>
          </div>
        </article>

        <article className="card backups-panel backups-drive-panel">
          <div className="backups-panel-header">
            <div>
              <h3>Preparacion de Google Drive</h3>
              <p>La app usa OAuth personal y crea una carpeta propia en tu Drive para guardar los respaldos.</p>
            </div>
            <span className={`backups-status-chip ${
              driveTest?.ok ? 'is-success' : driveTest ? 'is-danger' : settings.googleDriveReady ? 'is-success' : 'is-warning'
            }`}>
              {driveTest?.ok ? 'Conexion validada' : driveTest ? 'Validacion fallida' : settings.googleDriveReady ? 'Conectado' : 'Pendiente'}
            </span>
          </div>

          <div className="backups-drive-checklist">
            {driveChecklist.map((item) => (
              <div className={`backups-drive-item ${item.ready ? 'is-ready' : 'is-missing'}`} key={item.label}>
                <span>{item.label}</span>
                <strong>{item.ready ? 'OK' : 'Pendiente'}</strong>
              </div>
            ))}
          </div>

          <div className="backups-drive-note">
            <strong>Como funciona ahora</strong>
            <p>Primero guardas el Client ID, luego conectas tu cuenta de Google y la app crea automaticamente la carpeta `TallerCelularBackups` en tu Drive.</p>
          </div>

          <div className="backups-quick-actions">
            <button
              className="secondary"
              onClick={handleConnectDrive}
              disabled={connectingDrive || !settings.googleDriveEnabled || !settings.googleOauthClientId?.trim() || !settings.googleOauthClientSecret?.trim()}
            >
              {connectingDrive ? <LoaderCircle size={18} className="backups-spin" /> : <Cloud size={18} />}
              Conectar Google Drive
            </button>

            <button
              className="secondary"
              onClick={handleTestDrive}
              disabled={testingDrive || !settings.googleOauthConnected}
            >
              {testingDrive ? <LoaderCircle size={18} className="backups-spin" /> : <Cloud size={18} />}
              Probar conexion
            </button>

            <button
              className="secondary"
              onClick={handleDisconnectDrive}
              disabled={disconnectingDrive || !settings.googleOauthConnected}
            >
              {disconnectingDrive ? <LoaderCircle size={18} className="backups-spin" /> : <Unplug size={18} />}
              Desconectar
            </button>
          </div>

          <div className="backups-drive-note">
            <strong>Estado actual</strong>
            <p><strong>Carpeta:</strong> {settings.googleDriveFolderName || 'Se creara automaticamente al conectar'}</p>
            <p><strong>Folder ID:</strong> {settings.googleDriveFolderId || 'Pendiente'}</p>
            <p><strong>Conectado:</strong> {settings.googleOauthConnected ? 'Si' : 'No'}</p>
            <p><strong>Ultima conexion:</strong> {settings.googleOauthConnectedAt ? formatDateTime(settings.googleOauthConnectedAt) : 'Sin conexion todavia'}</p>
          </div>

          {driveTest && (
            <div className="backups-drive-note">
              <strong>{driveTest.ok ? 'Resultado de la prueba' : 'Detalle del error'}</strong>
              <p>{driveTest.message}</p>
              {driveTest.folderName && <p><strong>Carpeta detectada:</strong> {driveTest.folderName}</p>}
              {!driveTest.ok && <p><strong>Tipo:</strong> {driveTest.retryable ? 'Fallo temporal, conviene reintentar.' : 'Fallo definitivo de configuracion o autorizacion.'}</p>}
            </div>
          )}
        </article>
      </section>

      <section className="backups-grid backups-grid-bottom">
        <article className="card backups-panel backups-settings-panel">
          <div className="backups-panel-header">
            <div>
              <h3>Configuracion editable</h3>
              <p>Controla cron, retencion, compresion y el Client ID usado para Google Drive.</p>
            </div>
          </div>

          <form className="backups-form" onSubmit={handleSaveSettings}>
            <div className="backups-toggle-grid">
              <label className="backups-toggle-card">
                <input
                  type="checkbox"
                  checked={Boolean(settings.enabled)}
                  onChange={(event) => handleChange('enabled', event.target.checked)}
                />
                <div>
                  <strong>Respaldos automaticos</strong>
                  <p>Permite que el scheduler ejecute backups segun el cron.</p>
                </div>
              </label>

              <label className="backups-toggle-card">
                <input
                  type="checkbox"
                  checked={Boolean(settings.zipEnabled)}
                  onChange={(event) => handleChange('zipEnabled', event.target.checked)}
                />
                <div>
                  <strong>Comprimir en ZIP</strong>
                  <p>Reduce espacio y facilita mover el archivo generado.</p>
                </div>
              </label>

              <label className="backups-toggle-card">
                <input
                  type="checkbox"
                  checked={Boolean(settings.googleDriveEnabled)}
                  onChange={(event) => handleChange('googleDriveEnabled', event.target.checked)}
                />
                <div>
                  <strong>Subida remota a Drive</strong>
                  <p>Activa la integracion remota y el reintento de pendientes.</p>
                </div>
              </label>
            </div>

            <div className="form-grid form-grid-2">
              <label>
                <span>Cron</span>
                <input
                  value={settings.cron}
                  onChange={(event) => handleChange('cron', event.target.value)}
                  placeholder="0 0 1 * * *"
                />
              </label>

              <label>
                <span>Dias de retencion</span>
                <input
                  type="number"
                  min="1"
                  value={settings.retentionDays}
                  onChange={(event) => handleChange('retentionDays', event.target.value)}
                />
              </label>

              <label className="field-span-2">
                <span>Carpeta local</span>
                <input
                  value={settings.directory}
                  onChange={(event) => handleChange('directory', event.target.value)}
                  placeholder="./backups"
                />
                <small>En desktop final se guarda en una ruta absoluta del sistema. Si ingresas una ruta relativa, el backend la convierte a la carpeta segura de la app en disco local.</small>
              </label>

              <label className="field-span-2">
                <span>Google OAuth Client ID</span>
                <input
                  value={settings.googleOauthClientId || ''}
                  onChange={(event) => handleChange('googleOauthClientId', event.target.value)}
                  placeholder="1234567890-abc123def456.apps.googleusercontent.com"
                  disabled={!settings.googleDriveEnabled}
                />
                <small>Usa el Client ID del tipo Escritorio que creaste en Google Cloud.</small>
              </label>

              <label className="field-span-2">
                <span>Google OAuth Client Secret</span>
                <input
                  value={settings.googleOauthClientSecret || ''}
                  onChange={(event) => handleChange('googleOauthClientSecret', event.target.value)}
                  placeholder="GOCSPX-xxxxxxxxxxxxxxxxxxxx"
                  disabled={!settings.googleDriveEnabled}
                />
                <small>Copialo desde tu cliente OAuth de tipo Escritorio en Google Cloud.</small>
              </label>
            </div>

            <div className="backups-form-meta">
              <div>
                <span>Ultimo automatico</span>
                <strong>{settings.lastAutomaticBackupAt ? formatDateTime(settings.lastAutomaticBackupAt) : 'Sin ejecucion automatica'}</strong>
              </div>
              <div>
                <span>Siguiente automatico</span>
                <strong>{settings.nextAutomaticBackupAt ? formatDateTime(settings.nextAutomaticBackupAt) : 'No programado'}</strong>
              </div>
            </div>

            <div className="backups-form-actions">
              <button type="submit" disabled={saving}>
                {saving ? <LoaderCircle size={18} className="backups-spin" /> : <Save size={18} />}
                Guardar configuracion
              </button>
            </div>
          </form>
        </article>

        <article className="card backups-panel backups-drive-panel">
          <div className="backups-panel-header">
            <div>
              <h3>Restauracion local</h3>
              <p>Valida un backup local .db o .zip, crea un resguardo de la base actual y reinicia el backend para aplicar la restauracion.</p>
            </div>
            <span className={`backups-status-chip ${restoreValidation ? 'is-warning' : 'is-warning'}`}>
              {restoreValidation ? 'Listo para confirmar' : 'Pendiente'}
            </span>
          </div>

          <div className="backups-drive-note">
            <strong>Importante</strong>
            <p>Solo restaura backups generados por esta app. Al confirmar, el sistema creara un backup previo de seguridad y reiniciara el backend.</p>
          </div>

          <div className="form-grid">
            <label className="field-span-2">
              <span>Archivo de backup local</span>
              <input
                type="file"
                accept=".db,.zip"
                onChange={(event) => {
                  const file = event.target.files?.[0] || null;
                  setRestoreFile(file);
                  setRestoreValidation(null);
                }}
              />
              <small>Formatos aceptados: .db y .zip con una sola base SQLite dentro.</small>
            </label>
          </div>

          <div className="backups-quick-actions">
            <button className="secondary" onClick={handleValidateRestore} disabled={validatingRestore || !restoreFile}>
              {validatingRestore ? <LoaderCircle size={18} className="backups-spin" /> : <Cloud size={18} />}
              Validar backup local
            </button>
            <button className="secondary" onClick={handleExecuteLocalRestore} disabled={executingRestore || !restoreValidation?.sessionId}>
              {executingRestore ? <LoaderCircle size={18} className="backups-spin" /> : <HardDriveDownload size={18} />}
              Restaurar ahora
            </button>
          </div>

          {restoreValidation && (
            <div className="backups-drive-note">
              <strong>Backup validado</strong>
              <p><strong>Archivo:</strong> {restoreValidation.originalFileName}</p>
              <p><strong>Formato:</strong> {restoreValidation.format}</p>
              <p><strong>Base detectada:</strong> {restoreValidation.detectedDatabaseFileName}</p>
              <p><strong>Tamano:</strong> {formatBytes(restoreValidation.sizeBytes)}</p>
              <p><strong>Validado en:</strong> {formatDateTime(restoreValidation.validatedAt)}</p>
            </div>
          )}

          {lastRestoreResult?.available && (
            <div className="backups-drive-note">
              <strong>Ultimo resultado de restauracion</strong>
              <p><strong>Estado:</strong> {lastRestoreResult.ok ? 'Exitoso' : 'Fallido'}</p>
              <p><strong>Momento:</strong> {lastRestoreResult.restoredAt ? formatDateTime(lastRestoreResult.restoredAt) : 'N/D'}</p>
              <p><strong>Origen:</strong> {lastRestoreResult.restoredFrom || 'N/D'}</p>
              <p><strong>Backup previo:</strong> {lastRestoreResult.backupBeforeRestorePath || 'N/D'}</p>
            </div>
          )}
        </article>

        <article className="card backups-panel backups-drive-panel">
          <div className="backups-panel-header">
            <div>
              <h3>Restauracion desde Drive</h3>
              <p>Descarga el backup remoto a staging local, lo valida y luego reutiliza el mismo flujo seguro de restauracion.</p>
            </div>
            <span className={`backups-status-chip ${remoteRestoreValidation ? 'is-warning' : settings.googleDriveReady ? 'is-success' : 'is-warning'}`}>
              {remoteRestoreValidation ? 'Listo para confirmar' : settings.googleDriveReady ? 'Drive disponible' : 'Drive no listo'}
            </span>
          </div>

          <div className="backups-drive-note">
            <strong>Importante</strong>
            <p>Nunca se restaura directo desde Drive. Primero se descarga el archivo a temporal local, se valida y recien despues se prepara el reinicio controlado.</p>
          </div>

          <div className="form-grid">
            <label className="field-span-2">
              <span>Backup remoto de Google Drive</span>
              <select
                value={selectedRemoteFileId}
                onChange={(event) => {
                  setSelectedRemoteFileId(event.target.value);
                  setRemoteRestoreValidation(null);
                }}
                disabled={!settings.googleDriveReady || loadingRemoteBackups || remoteBackups.length === 0}
              >
                {!remoteBackups.length && <option value="">No hay backups remotos disponibles</option>}
                {remoteBackups.map((file) => (
                  <option key={file.fileId} value={file.fileId}>
                    {file.fileName} - {file.createdAt ? formatDateTime(file.createdAt) : 'Sin fecha'}
                  </option>
                ))}
              </select>
              <small>Se listan archivos .db y .zip dentro de la carpeta de backups administrada por la app en tu Google Drive.</small>
            </label>
          </div>

          <div className="backups-quick-actions">
            <button className="secondary" onClick={() => loadRemoteRestoreFiles(settings)} disabled={loadingRemoteBackups || !settings.googleDriveReady}>
              {loadingRemoteBackups ? <LoaderCircle size={18} className="backups-spin" /> : <RefreshCcw size={18} />}
              Actualizar lista de Drive
            </button>
            <button className="secondary" onClick={handleValidateRemoteRestore} disabled={validatingRemoteRestore || !selectedRemoteFileId || !settings.googleDriveReady}>
              {validatingRemoteRestore ? <LoaderCircle size={18} className="backups-spin" /> : <Cloud size={18} />}
              Validar backup remoto
            </button>
            <button className="secondary" onClick={handleExecuteRemoteRestore} disabled={executingRestore || !remoteRestoreValidation?.sessionId}>
              {executingRestore ? <LoaderCircle size={18} className="backups-spin" /> : <HardDriveDownload size={18} />}
              Restaurar desde Drive
            </button>
          </div>

          {selectedRemoteFileId && remoteBackups.some((file) => file.fileId === selectedRemoteFileId) && (
            <div className="backups-drive-note">
              {(() => {
                const file = remoteBackups.find((item) => item.fileId === selectedRemoteFileId);
                return (
                  <>
                    <strong>Backup remoto seleccionado</strong>
                    <p><strong>Archivo:</strong> {file.fileName}</p>
                    <p><strong>Creado en:</strong> {file.createdAt ? formatDateTime(file.createdAt) : 'Sin fecha'}</p>
                    <p><strong>Tamano:</strong> {formatBytes(file.sizeBytes)}</p>
                  </>
                );
              })()}
            </div>
          )}

          {remoteRestoreValidation && (
            <div className="backups-drive-note">
              <strong>Backup remoto validado</strong>
              <p><strong>Archivo:</strong> {remoteRestoreValidation.originalFileName}</p>
              <p><strong>Formato:</strong> {remoteRestoreValidation.format}</p>
              <p><strong>Base detectada:</strong> {remoteRestoreValidation.detectedDatabaseFileName}</p>
              <p><strong>Tamano:</strong> {formatBytes(remoteRestoreValidation.sizeBytes)}</p>
              <p><strong>Validado en:</strong> {formatDateTime(remoteRestoreValidation.validatedAt)}</p>
            </div>
          )}
        </article>

        <article className="card backups-panel backups-history-panel">
          <div className="backups-panel-header">
            <div>
              <h3>Historial de respaldos</h3>
              <p>Listado persistente de ejecuciones manuales y automaticas.</p>
            </div>
            <span className="chip">{recordsPage.content.length} visibles</span>
          </div>

          {loading && recordsPage.content.length === 0 ? (
            <div className="backups-empty-loading">
              <LoaderCircle size={22} className="backups-spin" />
              <span>Cargando respaldos...</span>
            </div>
          ) : recordsPage.content.length === 0 ? (
            <EmptyState
              title="Todavia no hay respaldos"
              description="Ejecuta un backup manual para empezar a poblar el historial."
            />
          ) : (
            <>
              <div className="backups-history-list">
                {recordsPage.content.map((record) => (
                  <article key={record.id} className="backups-record-card">
                    <div className="backups-record-top">
                      <div>
                        <strong>{record.archivo}</strong>
                        <p>{formatDateTime(record.generadoEn)}</p>
                      </div>
                      <span className={`backups-status-chip ${getStatusTone(record.estado)}`}>{getStatusLabel(record.estado)}</span>
                    </div>

                    <div className="backups-record-grid">
                      <div>
                        <span>Origen</span>
                        <strong>{getOriginLabel(record.origen)}</strong>
                      </div>
                      <div>
                        <span>Tamano</span>
                        <strong>{formatBytes(record.tamanoBytes)}</strong>
                      </div>
                      <div>
                        <span>Intentos</span>
                        <strong>{record.intentosSubida ?? 0}</strong>
                      </div>
                    </div>

                    <div className="backups-record-meta">
                      <p><strong>Ruta local:</strong> {record.rutaLocal}</p>
                      <p><strong>Mensaje:</strong> {record.mensaje || 'Sin mensaje adicional.'}</p>
                      {record.ubicacionRemota && <p><strong>Remoto:</strong> {record.ubicacionRemota}</p>}
                    </div>
                  </article>
                ))}
              </div>

              <div className="pagination-row backups-pagination-row">
                <button
                  className="secondary"
                  type="button"
                  disabled={(recordsPage.number || 0) <= 0}
                  onClick={() => goToPage((recordsPage.number || 0) - 1)}
                >
                  Anterior
                </button>

                <span>
                  Pagina {(recordsPage.number || 0) + 1} de {Math.max(recordsPage.totalPages || 1, 1)}
                </span>

                <button
                  className="secondary"
                  type="button"
                  disabled={(recordsPage.number || 0) + 1 >= (recordsPage.totalPages || 1)}
                  onClick={() => goToPage((recordsPage.number || 0) + 1)}
                >
                  Siguiente
                </button>
              </div>
            </>
          )}
        </article>
      </section>
    </div>
  );
}
