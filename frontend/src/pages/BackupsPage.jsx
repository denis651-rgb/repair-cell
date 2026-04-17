import { useEffect, useMemo, useState } from 'react';
import {
  Cloud,
  Clock3,
  DatabaseBackup,
  FolderSync,
  HardDriveDownload,
  LoaderCircle,
  RefreshCcw,
  Save,
  ShieldCheck,
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
  googleServiceAccountKeyPath: '',
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
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [running, setRunning] = useState(false);
  const [retrying, setRetrying] = useState(false);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');

  const loadData = async (pagina = 0) => {
    setLoading(true);
    setError('');

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
    } catch (err) {
      setError(err.message);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadData();
  }, []);

  const driveChecklist = useMemo(() => {
    return [
      {
        label: 'Drive habilitado',
        ready: Boolean(settings.googleDriveEnabled),
      },
      {
        label: 'Folder ID configurado',
        ready: Boolean(settings.googleDriveFolderId?.trim()),
      },
      {
        label: 'Ruta al JSON configurada',
        ready: Boolean(settings.googleServiceAccountKeyPath?.trim()),
      },
    ];
  }, [settings.googleDriveEnabled, settings.googleDriveFolderId, settings.googleServiceAccountKeyPath]);

  const handleChange = (field, value) => {
    setSettings((current) => ({
      ...current,
      [field]: value,
    }));
  };

  const handleSaveSettings = async (event) => {
    event.preventDefault();
    setSaving(true);
    setError('');
    setSuccess('');

    try {
      const payload = {
        enabled: Boolean(settings.enabled),
        cron: settings.cron,
        directory: settings.directory,
        zipEnabled: Boolean(settings.zipEnabled),
        retentionDays: Number(settings.retentionDays || 1),
        googleDriveEnabled: Boolean(settings.googleDriveEnabled),
        googleDriveFolderId: settings.googleDriveFolderId,
        googleServiceAccountKeyPath: settings.googleServiceAccountKeyPath,
      };

      const saved = await api.put('/admin/backups/settings', payload);
      setSettings((current) => ({
        ...current,
        ...saved,
        retentionDays: Number(saved?.retentionDays || current.retentionDays || 30),
      }));
      setSummary(await api.get('/admin/backups/summary'));
      setSuccess('Configuracion de respaldos actualizada correctamente.');
    } catch (err) {
      setError(err.message);
    } finally {
      setSaving(false);
    }
  };

  const handleRunBackup = async () => {
    setRunning(true);
    setError('');
    setSuccess('');

    try {
      const response = await api.post('/admin/backups/run');
      await loadData(recordsPage.number || 0);
      setSuccess(`Backup ejecutado: ${response.archivo}`);
    } catch (err) {
      setError(err.message);
    } finally {
      setRunning(false);
    }
  };

  const handleRetryPending = async () => {
    setRetrying(true);
    setError('');
    setSuccess('');

    try {
      const retried = await api.post('/admin/backups/retry-pending');
      await loadData(recordsPage.number || 0);
      setSuccess(`Reintento completado. Backups procesados: ${retried}.`);
    } catch (err) {
      setError(err.message);
    } finally {
      setRetrying(false);
    }
  };

  const goToPage = async (pagina) => {
    await loadData(pagina);
  };

  return (
    <div className="page-stack backups-page">
      <PageHeader
        title="Respaldos"
        subtitle="Administra backups automáticos, historial local y preparación de Google Drive desde un solo lugar."
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

      {error && <div className="alert">{error}</div>}
      {success && <div className="backups-success-banner">{success}</div>}

      <section className="backups-hero-card">
        <div className="backups-hero-copy">
          <span className="backups-kicker">Seguridad operativa</span>
          <h2>{loading ? 'Cargando estado de respaldos...' : 'Controla copias locales, automatización y subida remota'}</h2>
          <p>El sistema ya guarda historial persistente de respaldos, reintenta subidas pendientes y puede dejar lista la integración con Google Drive.</p>
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
          <span className="backups-kpi-label">Siguiente ejecución</span>
          <strong className="backups-kpi-value backups-kpi-small-value">{summary?.nextAutomaticBackupAt ? formatDateTime(summary.nextAutomaticBackupAt) : 'No programada'}</strong>
          <p>Calculada desde la expresión cron actual.</p>
        </article>

        <article className="backups-kpi-card">
          <div className="backups-kpi-icon icon-success"><ShieldCheck size={20} /></div>
          <span className="backups-kpi-label">Último estado</span>
          <strong className="backups-kpi-value backups-kpi-small-value">{summary?.lastBackupStatus ? getStatusLabel(summary.lastBackupStatus) : 'Sin backups'}</strong>
          <p>{summary?.lastBackupAt ? formatDateTime(summary.lastBackupAt) : 'Todavía no hay ejecución registrada.'}</p>
        </article>
      </section>

      <section className="backups-grid">
        <article className="card backups-panel backups-summary-panel">
          <div className="backups-panel-header">
            <div>
              <h3>Resumen operativo</h3>
              <p>Lectura rápida del último respaldo y la salud de la configuración.</p>
            </div>
            <span className="chip">{summary?.automaticEnabled ? 'Automatico activo' : 'Automatico pausado'}</span>
          </div>

          <div className="backups-summary-list">
            <div className="backups-summary-item">
              <span>Directorio local</span>
              <strong>{summary?.backupDirectory || settings.directory}</strong>
            </div>
            <div className="backups-summary-item">
              <span>Último respaldo</span>
              <strong>{summary?.lastBackupAt ? formatDateTime(summary.lastBackupAt) : 'Sin registros'}</strong>
            </div>
            <div className="backups-summary-item">
              <span>Mensaje reciente</span>
              <strong>{summary?.lastBackupMessage || 'Aun no hay mensajes de respaldo.'}</strong>
            </div>
            <div className="backups-summary-item">
              <span>Enlace remoto</span>
              <strong>{summary?.lastRemoteLocation || 'Todavía no hay ubicación remota.'}</strong>
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
              <h3>Preparación de Google Drive</h3>
              <p>La integración remota ya está implementada; aquí validamos lo mínimo para activarla.</p>
            </div>
            <span className={`backups-status-chip ${settings.googleDriveReady ? 'is-success' : 'is-warning'}`}>
              {settings.googleDriveReady ? 'Configurado' : 'Faltan datos'}
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
            <strong>Qué falta para usar Drive</strong>
            <p>Necesitas el ID de la carpeta compartida en Google Drive y la ruta local al JSON de la service account con permisos sobre esa carpeta.</p>
          </div>
        </article>
      </section>

      <section className="backups-grid backups-grid-bottom">
        <article className="card backups-panel backups-settings-panel">
          <div className="backups-panel-header">
            <div>
              <h3>Configuración editable</h3>
              <p>Controla cron, retención, compresión y los datos necesarios para Drive.</p>
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
                  <strong>Respaldos automáticos</strong>
                  <p>Permite que el scheduler ejecute backups según el cron.</p>
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
                  <p>Activa la integración remota y el reintento de pendientes.</p>
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
                <span>Días de retención</span>
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
              </label>

              <label className="field-span-2">
                <span>ID de carpeta de Google Drive</span>
                <input
                  value={settings.googleDriveFolderId || ''}
                  onChange={(event) => handleChange('googleDriveFolderId', event.target.value)}
                  placeholder="1AbCdEfGhIj..."
                  disabled={!settings.googleDriveEnabled}
                />
              </label>

              <label className="field-span-2">
                <span>Ruta al JSON de service account</span>
                <input
                  value={settings.googleServiceAccountKeyPath || ''}
                  onChange={(event) => handleChange('googleServiceAccountKeyPath', event.target.value)}
                  placeholder="C:\\credenciales\\google-drive-backups.json"
                  disabled={!settings.googleDriveEnabled}
                />
              </label>
            </div>

            <div className="backups-form-meta">
              <div>
                <span>Último automático</span>
                <strong>{settings.lastAutomaticBackupAt ? formatDateTime(settings.lastAutomaticBackupAt) : 'Sin ejecución automática'}</strong>
              </div>
              <div>
                <span>Siguiente automático</span>
                <strong>{settings.nextAutomaticBackupAt ? formatDateTime(settings.nextAutomaticBackupAt) : 'No programado'}</strong>
              </div>
            </div>

            <div className="backups-form-actions">
              <button type="submit" disabled={saving}>
                {saving ? <LoaderCircle size={18} className="backups-spin" /> : <Save size={18} />}
                Guardar configuración
              </button>
            </div>
          </form>
        </article>

        <article className="card backups-panel backups-history-panel">
          <div className="backups-panel-header">
            <div>
              <h3>Historial de respaldos</h3>
              <p>Listado persistente de ejecuciones manuales y automáticas.</p>
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
              title="Todavía no hay respaldos"
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
                        <span>Tamaño</span>
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
                  Página {(recordsPage.number || 0) + 1} de {Math.max(recordsPage.totalPages || 1, 1)}
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
