import { useEffect, useMemo, useState } from 'react';
import { Archive, FolderTree, Layers3, PackagePlus, Plus, Rows3, Tags } from 'lucide-react';
import { api } from '../../api/api';
import EmptyState from '../common/EmptyState';
import Modal from '../common/Modal';

const OPERACION_PAGE_SIZE = 10;
const HISTORICO_PAGE_SIZE = 10;
const CATALOGO_PAGE_SIZE = 8;
const DETALLE_LOTES_PAGE_SIZE = 5;

const productoBaseInicial = {
  codigoBase: '',
  nombreBase: '',
  categoriaId: '',
  marcaId: '',
  modelo: '',
  descripcion: '',
  activo: true,
};

const varianteInicial = {
  productoBaseId: '',
  codigoVariante: '',
  calidad: '',
  tipoPresentacion: '',
  color: '',
  precioVentaSugerido: 0,
  activo: true,
};

const loteInicial = {
  varianteId: '',
  codigoLote: '',
  codigoProveedor: '',
  fechaIngreso: new Date().toISOString().slice(0, 10),
  cantidadInicial: 0,
  cantidadDisponible: 0,
  costoUnitario: 0,
  subtotalCompra: '',
  compraId: '',
  activo: true,
  visibleEnVentas: true,
  motivoCierre: '',
};

const detalleOperativoInicial = null;

const paginaVacia = (size) => ({
  content: [],
  number: 0,
  size,
  totalElements: 0,
  totalPages: 0,
  first: true,
  last: true,
});

const crearErrorVisual = (titulo, error) => ({
  titulo,
  detalle: error?.message || 'No se pudo completar la operacion del catalogo.',
});

const normalizarCodigoBase = (valor) =>
  String(valor || '')
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
    .replace(/[^A-Za-z0-9 ]+/g, ' ')
    .trim()
    .toUpperCase();

const abreviarSegmentoCodigo = (valor) => {
  const limpio = normalizarCodigoBase(valor);
  if (!limpio) return '';

  const compacto = limpio.replace(/\s+/g, '');
  if (compacto.length <= 3) {
    return compacto;
  }

  return compacto.slice(0, 3);
};

function normalizarPagina(respuesta, size) {
  if (Array.isArray(respuesta)) {
    return {
      ...paginaVacia(size),
      content: respuesta,
      totalElements: respuesta.length,
      totalPages: Math.ceil(respuesta.length / size),
    };
  }

  if (!respuesta || typeof respuesta !== 'object') {
    return paginaVacia(size);
  }

  return {
    content: respuesta.content || [],
    number: respuesta.number || 0,
    size: respuesta.size || size,
    totalElements: respuesta.totalElements || 0,
    totalPages: respuesta.totalPages || 0,
    first: respuesta.first ?? true,
    last: respuesta.last ?? true,
  };
}

function paginarLocal(lista, pagina, tamano) {
  const totalElements = lista.length;
  const totalPages = Math.ceil(totalElements / tamano);
  const paginaSegura = totalPages === 0 ? 0 : Math.min(pagina, totalPages - 1);
  const inicio = paginaSegura * tamano;
  const fin = inicio + tamano;

  return {
    content: lista.slice(inicio, fin),
    number: paginaSegura,
    size: tamano,
    totalElements,
    totalPages,
    first: paginaSegura === 0,
    last: totalPages === 0 || paginaSegura === totalPages - 1,
  };
}

function rangoPaginacion(pagina) {
  if (!pagina.totalElements) {
    return 'Sin registros';
  }
  const desde = pagina.number * pagina.size + 1;
  const hasta = Math.min((pagina.number + 1) * pagina.size, pagina.totalElements);
  return `Mostrando ${desde}-${hasta} de ${pagina.totalElements}`;
}

function formatFecha(valor) {
  if (!valor) return 'Abierto';
  if (typeof valor === 'string' && valor.includes('T')) {
    return valor.replace('T', ' ').slice(0, 16);
  }
  return valor;
}

function PaginationRow({ pagina, onChange }) {
  if (!pagina || pagina.totalElements <= pagina.size) return null;

  return (
    <div className="catalogo-pagination">
      <span>{rangoPaginacion(pagina)}</span>
      <div className="catalogo-pagination-actions">
        <button
          type="button"
          className="secondary compact"
          onClick={() => onChange(Math.max(pagina.number - 1, 0))}
          disabled={pagina.first}
        >
          Anterior
        </button>
        <strong>
          Pagina {pagina.number + 1} de {Math.max(pagina.totalPages, 1)}
        </strong>
        <button
          type="button"
          className="secondary compact"
          onClick={() => onChange(Math.min(pagina.number + 1, pagina.totalPages - 1))}
          disabled={pagina.last}
        >
          Siguiente
        </button>
      </div>
    </div>
  );
}

export default function CatalogoBaseManager({ categorias, marcas, onOpenCategorias, onOpenMarcas }) {
  const [vistaActiva, setVistaActiva] = useState('OPERACION');
  const [productosBase, setProductosBase] = useState([]);
  const [variantes, setVariantes] = useState([]);
  const [lotes, setLotes] = useState([]);
  const [inventarioOperativo, setInventarioOperativo] = useState(paginaVacia(OPERACION_PAGE_SIZE));
  const [detalleOperativo, setDetalleOperativo] = useState(detalleOperativoInicial);
  const [historialLotes, setHistorialLotes] = useState(paginaVacia(HISTORICO_PAGE_SIZE));
  const [categoriaFiltro, setCategoriaFiltro] = useState('');
  const [marcaFiltro, setMarcaFiltro] = useState('');
  const [modeloFiltro, setModeloFiltro] = useState('');
  const [calidadFiltro, setCalidadFiltro] = useState('');
  const [soloConStock, setSoloConStock] = useState(true);
  const [soloActivos, setSoloActivos] = useState(true);
  const [estadoLoteFiltro, setEstadoLoteFiltro] = useState('OPERATIVOS');
  const [estadoHistorialFiltro, setEstadoHistorialFiltro] = useState('TODOS');
  const [historialLigadoAVariante, setHistorialLigadoAVariante] = useState(true);
  const [productoBaseSeleccionado, setProductoBaseSeleccionado] = useState(null);
  const [varianteSeleccionada, setVarianteSeleccionada] = useState(null);
  const [paginaOperativo, setPaginaOperativo] = useState(0);
  const [paginaHistorico, setPaginaHistorico] = useState(0);
  const [paginaProductosBase, setPaginaProductosBase] = useState(0);
  const [paginaVariantes, setPaginaVariantes] = useState(0);
  const [paginaLotesCatalogo, setPaginaLotesCatalogo] = useState(0);
  const [paginaDetalleLotes, setPaginaDetalleLotes] = useState(0);
  const [modalBaseOpen, setModalBaseOpen] = useState(false);
  const [modalVarianteOpen, setModalVarianteOpen] = useState(false);
  const [modalLoteOpen, setModalLoteOpen] = useState(false);
  const [productoBaseEditando, setProductoBaseEditando] = useState(null);
  const [varianteEditando, setVarianteEditando] = useState(null);
  const [loteEditando, setLoteEditando] = useState(null);
  const [productoBaseForm, setProductoBaseForm] = useState(productoBaseInicial);
  const [codigoBaseManual, setCodigoBaseManual] = useState(false);
  const [varianteForm, setVarianteForm] = useState(varianteInicial);
  const [codigoVarianteManual, setCodigoVarianteManual] = useState(false);
  const [loteForm, setLoteForm] = useState(loteInicial);
  const [error, setError] = useState(null);

  const currency = useMemo(
    () => new Intl.NumberFormat('es-BO', { minimumFractionDigits: 2, maximumFractionDigits: 2 }),
    [],
  );

  const filtrosGlobales = useMemo(
    () => ({
      categoriaId: categoriaFiltro || undefined,
      marcaId: marcaFiltro || undefined,
      modelo: modeloFiltro || undefined,
      calidad: calidadFiltro || undefined,
    }),
    [categoriaFiltro, marcaFiltro, modeloFiltro, calidadFiltro],
  );

  const categoriaSeleccionadaFormulario = useMemo(
    () => categorias.find((categoria) => String(categoria.id) === String(productoBaseForm.categoriaId)),
    [categorias, productoBaseForm.categoriaId],
  );

  const marcaSeleccionadaFormulario = useMemo(
    () => marcas.find((marca) => String(marca.id) === String(productoBaseForm.marcaId)),
    [marcas, productoBaseForm.marcaId],
  );

  const productoBaseSeleccionadoFormularioVariante = useMemo(
    () => productosBase.find((productoBase) => String(productoBase.id) === String(varianteForm.productoBaseId)),
    [productosBase, varianteForm.productoBaseId],
  );

  const codigoBaseSugerido = useMemo(() => {
    const categoriaCodigo = abreviarSegmentoCodigo(categoriaSeleccionadaFormulario?.nombre);
    const marcaCodigo = abreviarSegmentoCodigo(marcaSeleccionadaFormulario?.nombre);

    if (!categoriaCodigo || !marcaCodigo) {
      return '';
    }

    return `${categoriaCodigo}-${marcaCodigo}`;
  }, [categoriaSeleccionadaFormulario, marcaSeleccionadaFormulario]);

  const codigoVarianteSugerido = useMemo(() => {
    const codigoBase = normalizarCodigoBase(productoBaseSeleccionadoFormularioVariante?.codigoBase)
      .replace(/\s+/g, '-');
    const calidadCodigo = abreviarSegmentoCodigo(varianteForm.calidad);

    if (!codigoBase || !calidadCodigo) {
      return codigoBase || '';
    }

    return `${codigoBase}-${calidadCodigo}`;
  }, [productoBaseSeleccionadoFormularioVariante, varianteForm.calidad]);

  const cargarProductosBase = async () => {
    try {
      const respuesta = await api.get('/catalogo/productos-base', {
        categoriaId: filtrosGlobales.categoriaId,
        marcaId: filtrosGlobales.marcaId,
        modelo: filtrosGlobales.modelo,
        soloActivos,
      });
      const lista = respuesta || [];
      setProductosBase(lista);

      if (productoBaseSeleccionado) {
        const actualizado = lista.find((item) => item.id === productoBaseSeleccionado.id) || null;
        setProductoBaseSeleccionado(actualizado);
        if (!actualizado) {
          setVarianteSeleccionada(null);
        }
      }
    } catch (err) {
      setError(crearErrorVisual('No se pudo cargar el catalogo base.', err));
    }
  };

  const cargarVariantes = async () => {
    try {
      const respuesta = await api.get('/catalogo/productos-variantes', {
        productoBaseId: productoBaseSeleccionado?.id || undefined,
        categoriaId: filtrosGlobales.categoriaId,
        marcaId: filtrosGlobales.marcaId,
        modelo: filtrosGlobales.modelo,
        calidad: filtrosGlobales.calidad,
        soloActivas: soloActivos,
      });
      const lista = respuesta || [];
      setVariantes(lista);

      if (varianteSeleccionada) {
        const actualizada = lista.find((item) => item.id === varianteSeleccionada.id) || null;
        setVarianteSeleccionada(actualizada);
      }
    } catch (err) {
      setError(crearErrorVisual('No se pudieron cargar las variantes.', err));
    }
  };

  const cargarLotes = async () => {
    if (!varianteSeleccionada?.id) {
      setLotes([]);
      return;
    }

    try {
      const params = {
        varianteId: varianteSeleccionada.id,
        categoriaId: filtrosGlobales.categoriaId,
        marcaId: filtrosGlobales.marcaId,
        modelo: filtrosGlobales.modelo,
      };

      if (estadoLoteFiltro === 'OPERATIVOS') {
        params.soloOperativos = true;
      } else if (estadoLoteFiltro !== 'TODOS') {
        params.estado = estadoLoteFiltro;
      }

      const respuesta = await api.get('/catalogo/lotes', params);
      setLotes(respuesta || []);
    } catch (err) {
      setError(crearErrorVisual('No se pudieron cargar los lotes.', err));
    }
  };

  const cargarInventarioOperativo = async () => {
    try {
      const respuesta = await api.get('/catalogo/inventario-operativo', {
        categoriaId: filtrosGlobales.categoriaId,
        marcaId: filtrosGlobales.marcaId,
        modelo: filtrosGlobales.modelo,
        calidad: filtrosGlobales.calidad,
        soloConStock,
        pagina: paginaOperativo,
        tamano: OPERACION_PAGE_SIZE,
      });
      setInventarioOperativo(normalizarPagina(respuesta, OPERACION_PAGE_SIZE));
    } catch (err) {
      setError(crearErrorVisual('No se pudo cargar el inventario operativo.', err));
    }
  };

  const cargarDetalleOperativo = async () => {
    if (!varianteSeleccionada?.id) {
      setDetalleOperativo(detalleOperativoInicial);
      return;
    }

    try {
      const respuesta = await api.get(`/catalogo/inventario-operativo/${varianteSeleccionada.id}`);
      setDetalleOperativo(respuesta || detalleOperativoInicial);
    } catch (err) {
      setError(crearErrorVisual('No se pudo cargar el detalle operativo de la variante.', err));
    }
  };

  const cargarHistorialLotes = async () => {
    try {
      const params = {
        categoriaId: filtrosGlobales.categoriaId,
        marcaId: filtrosGlobales.marcaId,
        modelo: filtrosGlobales.modelo,
        calidad: filtrosGlobales.calidad,
        pagina: paginaHistorico,
        tamano: HISTORICO_PAGE_SIZE,
      };

      if (estadoHistorialFiltro !== 'TODOS') {
        params.estado = estadoHistorialFiltro;
      }

      if (historialLigadoAVariante && varianteSeleccionada?.id) {
        params.varianteId = varianteSeleccionada.id;
      }

      const respuesta = await api.get('/catalogo/lotes/historico', params);
      setHistorialLotes(normalizarPagina(respuesta, HISTORICO_PAGE_SIZE));
    } catch (err) {
      setError(crearErrorVisual('No se pudo cargar el historico de lotes.', err));
    }
  };

  useEffect(() => {
    cargarProductosBase();
  }, [filtrosGlobales.categoriaId, filtrosGlobales.marcaId, filtrosGlobales.modelo, soloActivos]);

  useEffect(() => {
    cargarVariantes();
  }, [
    filtrosGlobales.categoriaId,
    filtrosGlobales.marcaId,
    filtrosGlobales.modelo,
    filtrosGlobales.calidad,
    soloActivos,
    productoBaseSeleccionado?.id,
  ]);

  useEffect(() => {
    cargarLotes();
  }, [
    varianteSeleccionada?.id,
    filtrosGlobales.categoriaId,
    filtrosGlobales.marcaId,
    filtrosGlobales.modelo,
    estadoLoteFiltro,
  ]);

  useEffect(() => {
    cargarInventarioOperativo();
  }, [
    filtrosGlobales.categoriaId,
    filtrosGlobales.marcaId,
    filtrosGlobales.modelo,
    filtrosGlobales.calidad,
    soloConStock,
    paginaOperativo,
  ]);

  useEffect(() => {
    cargarDetalleOperativo();
  }, [varianteSeleccionada?.id]);

  useEffect(() => {
    cargarHistorialLotes();
  }, [
    filtrosGlobales.categoriaId,
    filtrosGlobales.marcaId,
    filtrosGlobales.modelo,
    filtrosGlobales.calidad,
    varianteSeleccionada?.id,
    estadoHistorialFiltro,
    historialLigadoAVariante,
    paginaHistorico,
  ]);

  useEffect(() => {
    setPaginaOperativo(0);
  }, [filtrosGlobales.categoriaId, filtrosGlobales.marcaId, filtrosGlobales.modelo, filtrosGlobales.calidad, soloConStock]);

  useEffect(() => {
    setPaginaHistorico(0);
  }, [
    filtrosGlobales.categoriaId,
    filtrosGlobales.marcaId,
    filtrosGlobales.modelo,
    filtrosGlobales.calidad,
    estadoHistorialFiltro,
    historialLigadoAVariante,
    varianteSeleccionada?.id,
  ]);

  useEffect(() => {
    setPaginaProductosBase(0);
  }, [productosBase.length]);

  useEffect(() => {
    setPaginaVariantes(0);
  }, [variantes.length, productoBaseSeleccionado?.id]);

  useEffect(() => {
    setPaginaLotesCatalogo(0);
  }, [lotes.length, varianteSeleccionada?.id]);

  useEffect(() => {
    setPaginaDetalleLotes(0);
  }, [detalleOperativo?.varianteId]);

  useEffect(() => {
    if (!modalBaseOpen || productoBaseEditando || codigoBaseManual) {
      return;
    }

    setProductoBaseForm((actual) => ({
      ...actual,
      codigoBase: codigoBaseSugerido,
    }));
  }, [codigoBaseSugerido, codigoBaseManual, modalBaseOpen, productoBaseEditando]);

  useEffect(() => {
    if (!modalVarianteOpen || varianteEditando || codigoVarianteManual) {
      return;
    }

    setVarianteForm((actual) => ({
      ...actual,
      codigoVariante: codigoVarianteSugerido,
    }));
  }, [codigoVarianteSugerido, codigoVarianteManual, modalVarianteOpen, varianteEditando]);

  const abrirProductoBaseNuevo = () => {
    setProductoBaseEditando(null);
    setProductoBaseForm(productoBaseInicial);
    setCodigoBaseManual(false);
    setModalBaseOpen(true);
  };

  const abrirVarianteNueva = () => {
    setVarianteEditando(null);
    setCodigoVarianteManual(false);
    setVarianteForm({
      ...varianteInicial,
      productoBaseId: productoBaseSeleccionado?.id ? String(productoBaseSeleccionado.id) : '',
    });
    setModalVarianteOpen(true);
  };

  const abrirLoteNuevo = () => {
    setLoteEditando(null);
    setLoteForm({
      ...loteInicial,
      varianteId: varianteSeleccionada?.id ? String(varianteSeleccionada.id) : '',
    });
    setModalLoteOpen(true);
  };

  const editarProductoBase = (productoBase) => {
    setProductoBaseEditando(productoBase);
    setCodigoBaseManual(true);
    setProductoBaseForm({
      codigoBase: productoBase.codigoBase || '',
      nombreBase: productoBase.nombreBase || '',
      categoriaId: productoBase.categoria?.id ? String(productoBase.categoria.id) : '',
      marcaId: productoBase.marca?.id ? String(productoBase.marca.id) : '',
      modelo: productoBase.modelo || '',
      descripcion: productoBase.descripcion || '',
      activo: productoBase.activo ?? true,
    });
    setModalBaseOpen(true);
  };

  const editarVariante = (variante) => {
    setVarianteEditando(variante);
    setCodigoVarianteManual(true);
    setVarianteForm({
      productoBaseId: variante.productoBase?.id ? String(variante.productoBase.id) : '',
      codigoVariante: variante.codigoVariante || '',
      calidad: variante.calidad || '',
      tipoPresentacion: variante.tipoPresentacion || '',
      color: variante.color || '',
      precioVentaSugerido: variante.precioVentaSugerido ?? 0,
      activo: variante.activo ?? true,
    });
    setModalVarianteOpen(true);
  };

  const editarLote = (lote) => {
    setLoteEditando(lote);
    setLoteForm({
      varianteId: lote.variante?.id ? String(lote.variante.id) : '',
      codigoLote: lote.codigoLote || '',
      codigoProveedor: lote.codigoProveedor || '',
      fechaIngreso: lote.fechaIngreso || new Date().toISOString().slice(0, 10),
      cantidadInicial: lote.cantidadInicial ?? 0,
      cantidadDisponible: lote.cantidadDisponible ?? 0,
      costoUnitario: lote.costoUnitario ?? 0,
      subtotalCompra: lote.subtotalCompra ?? '',
      compraId: lote.compraId ?? '',
      activo: lote.activo ?? true,
      visibleEnVentas: lote.visibleEnVentas ?? true,
      motivoCierre: lote.motivoCierre || '',
    });
    setModalLoteOpen(true);
  };

  const recargarOperacion = async () => {
    await Promise.all([cargarInventarioOperativo(), cargarDetalleOperativo(), cargarHistorialLotes()]);
  };

  const recargarCatalogo = async () => {
    await Promise.all([cargarProductosBase(), cargarVariantes(), cargarLotes()]);
  };

  const guardarProductoBase = async (event) => {
    event.preventDefault();
    try {
      const payload = {
        ...productoBaseForm,
        categoriaId: Number(productoBaseForm.categoriaId),
        marcaId: Number(productoBaseForm.marcaId),
      };

      if (productoBaseEditando) {
        await api.put(`/catalogo/productos-base/${productoBaseEditando.id}`, payload);
      } else {
        await api.post('/catalogo/productos-base', payload);
      }

      setModalBaseOpen(false);
      setProductoBaseEditando(null);
      setProductoBaseForm(productoBaseInicial);
      setCodigoBaseManual(false);
      await recargarCatalogo();
    } catch (err) {
      setError(crearErrorVisual(
        productoBaseEditando ? 'No se pudo actualizar el producto base.' : 'No se pudo crear el producto base.',
        err,
      ));
    }
  };

  const guardarVariante = async (event) => {
    event.preventDefault();
    try {
      const payload = {
        ...varianteForm,
        productoBaseId: Number(varianteForm.productoBaseId),
        precioVentaSugerido: Number(varianteForm.precioVentaSugerido || 0),
      };

      if (varianteEditando) {
        await api.put(`/catalogo/productos-variantes/${varianteEditando.id}`, payload);
      } else {
        await api.post('/catalogo/productos-variantes', payload);
      }

      setModalVarianteOpen(false);
      setVarianteEditando(null);
      setVarianteForm(varianteInicial);
      setCodigoVarianteManual(false);
      await Promise.all([recargarCatalogo(), recargarOperacion()]);
    } catch (err) {
      setError(crearErrorVisual(
        varianteEditando ? 'No se pudo actualizar la variante.' : 'No se pudo crear la variante.',
        err,
      ));
    }
  };

  const guardarLote = async (event) => {
    event.preventDefault();
    try {
      const payload = {
        ...loteForm,
        varianteId: Number(loteForm.varianteId),
        cantidadInicial: Number(loteForm.cantidadInicial || 0),
        cantidadDisponible: Number(loteForm.cantidadDisponible || 0),
        costoUnitario: Number(loteForm.costoUnitario || 0),
        subtotalCompra: loteForm.subtotalCompra === '' ? null : Number(loteForm.subtotalCompra),
        compraId: loteForm.compraId === '' ? null : Number(loteForm.compraId),
      };

      if (loteEditando) {
        await api.put(`/catalogo/lotes/${loteEditando.id}`, payload);
      } else {
        await api.post('/catalogo/lotes', payload);
      }

      setModalLoteOpen(false);
      setLoteEditando(null);
      setLoteForm(loteInicial);
      await Promise.all([recargarCatalogo(), recargarOperacion()]);
    } catch (err) {
      setError(crearErrorVisual(
        loteEditando ? 'No se pudo actualizar el lote.' : 'No se pudo crear el lote.',
        err,
      ));
    }
  };

  const cerrarLoteManual = async (lote) => {
    try {
      const stockDisponible = Number(lote.cantidadDisponible || 0);
      const motivo = window.prompt(
        stockDisponible > 0
          ? `Indica el motivo para cerrar manualmente el lote ${lote.codigoLote} con ${stockDisponible} unidades disponibles:`
          : `Indica el motivo de cierre para el lote ${lote.codigoLote}:`,
        stockDisponible > 0 ? 'Retirado del flujo operativo' : 'Cierre manual',
      );

      if (motivo === null) return;

      await api.post(`/catalogo/lotes/${lote.id}/cerrar-manual`, { motivo });
      await Promise.all([recargarCatalogo(), recargarOperacion()]);
    } catch (err) {
      setError(crearErrorVisual(`No se pudo cerrar manualmente el lote ${lote.codigoLote}.`, err));
    }
  };

  const productosBasePaginados = useMemo(
    () => paginarLocal(productosBase, paginaProductosBase, CATALOGO_PAGE_SIZE),
    [productosBase, paginaProductosBase],
  );
  const variantesPaginadas = useMemo(
    () => paginarLocal(variantes, paginaVariantes, CATALOGO_PAGE_SIZE),
    [variantes, paginaVariantes],
  );
  const lotesCatalogoPaginados = useMemo(
    () => paginarLocal(lotes, paginaLotesCatalogo, CATALOGO_PAGE_SIZE),
    [lotes, paginaLotesCatalogo],
  );
  const lotesDetallePaginados = useMemo(
    () => paginarLocal(detalleOperativo?.lotesOperativos || [], paginaDetalleLotes, DETALLE_LOTES_PAGE_SIZE),
    [detalleOperativo, paginaDetalleLotes],
  );

  const seleccionVarianteTexto = varianteSeleccionada
    ? `${varianteSeleccionada.codigoVariante} - ${varianteSeleccionada.productoBase?.nombreBase || varianteSeleccionada.nombreBase || 'Sin base'}`
    : 'Ninguna variante seleccionada';

  return (
    <section className="inventory-layout inventory-layout-full">
      <div className="inventory-main-panel card">
        <div className="inventory-panel-header">
          <div>
            <h3>Inventario nuevo por operacion y catalogo</h3>
            <p>Una sola tabla maestra para operar, un panel de detalle para entender y un historico separado para auditar.</p>
          </div>
          {vistaActiva === 'CATALOGO' && (
            <div className="inventory-header-actions">
              <button type="button" className="secondary inventory-icon-button" onClick={abrirProductoBaseNuevo} title="Nuevo producto base">
                <PackagePlus size={16} />
              </button>
              <button
                type="button"
                className="inventory-primary-button compact"
                onClick={abrirVarianteNueva}
                disabled={!productoBaseSeleccionado}
                title={productoBaseSeleccionado ? 'Nueva variante' : 'Selecciona un producto base'}
              >
                <Plus size={16} />
                Variante
              </button>
              <button
                type="button"
                className="secondary compact"
                onClick={abrirLoteNuevo}
                disabled={!varianteSeleccionada}
                title={varianteSeleccionada ? 'Nuevo lote manual' : 'Selecciona una variante'}
              >
                <Archive size={16} />
                Lote
              </button>
            </div>
          )}
        </div>

        {error && (
          <div className="alert inventory-alert-detailed">
            <div className="inventory-alert-copy">
              <strong>{error.titulo}</strong>
              <p>{error.detalle}</p>
            </div>
            <button type="button" className="secondary compact" onClick={() => setError(null)}>
              Cerrar
            </button>
          </div>
        )}

        <div className="catalogo-grid">
          <div className="catalogo-tabs">
            <button
              type="button"
              className={vistaActiva === 'OPERACION' ? 'catalogo-tab is-active' : 'catalogo-tab'}
              onClick={() => setVistaActiva('OPERACION')}
            >
              <Rows3 size={16} />
              Operacion
            </button>
            <button
              type="button"
              className={vistaActiva === 'CATALOGO' ? 'catalogo-tab is-active' : 'catalogo-tab'}
              onClick={() => setVistaActiva('CATALOGO')}
            >
              <Layers3 size={16} />
              Catalogo
            </button>
          </div>

          <section className="catalogo-panel catalogo-filter-shell">
            <div className="inventory-panel-header inventory-panel-header-tight">
              <div>
                <h4>Filtros globales</h4>
                <p>Marca, categoria, modelo y calidad afectan los listados de esta pantalla sin volverla un caos.</p>
              </div>
            </div>

            <div className="catalogo-filter-row catalogo-filter-row-global">
              <select value={categoriaFiltro} onChange={(event) => setCategoriaFiltro(event.target.value)}>
                <option value="">Todas las categorias</option>
                {categorias.map((categoria) => (
                  <option key={categoria.id} value={categoria.id}>
                    {categoria.nombre}
                  </option>
                ))}
              </select>

              <select value={marcaFiltro} onChange={(event) => setMarcaFiltro(event.target.value)}>
                <option value="">Todas las marcas</option>
                {marcas.map((marca) => (
                  <option key={marca.id} value={marca.id}>
                    {marca.nombre}
                  </option>
                ))}
              </select>

              <input
                value={modeloFiltro}
                onChange={(event) => setModeloFiltro(event.target.value)}
                placeholder="Filtrar por modelo"
              />

              <input
                value={calidadFiltro}
                onChange={(event) => setCalidadFiltro(event.target.value)}
                placeholder="Filtrar por calidad"
              />
            </div>
          </section>

          {vistaActiva === 'OPERACION' ? (
            <>
              <section className="catalogo-panel catalogo-panel-prominent">
                <div className="inventory-panel-header inventory-panel-header-tight">
                  <div>
                    <h4>Inventario operativo</h4>
                    <p>Solo una tabla maestra de variantes vendibles, ordenada para decidir rapido que puede venderse hoy.</p>
                  </div>
                  <div className="inventory-header-actions">
                    <label className="catalogo-checkbox">
                      <input
                        type="checkbox"
                        checked={soloConStock}
                        onChange={(event) => setSoloConStock(event.target.checked)}
                      />
                      Solo con stock
                    </label>
                    <span className="chip">{inventarioOperativo.totalElements} variantes</span>
                  </div>
                </div>

                {inventarioOperativo.content.length === 0 ? (
                  <EmptyState
                    title="Sin variantes operativas"
                    description="Ajusta los filtros globales o registra lotes activos para alimentar esta vista."
                  />
                ) : (
                  <>
                    <div className="responsive-table-wrap">
                      <table className="table">
                        <thead>
                          <tr>
                            <th>Codigo variante</th>
                            <th>Producto base</th>
                            <th>Marca / modelo</th>
                            <th>Calidad</th>
                            <th>Stock total</th>
                            <th>Lotes activos</th>
                            <th>P. Venta</th>
                            <th>Accion</th>
                          </tr>
                        </thead>
                        <tbody>
                          {inventarioOperativo.content.map((item) => (
                            <tr
                              key={item.varianteId}
                              className={varianteSeleccionada?.id === item.varianteId ? 'catalogo-row-active' : ''}
                            >
                              <td>
                                <strong>{item.codigoVariante}</strong>
                              </td>
                              <td>
                                <strong>{item.nombreBase}</strong>
                                <div>{item.codigoBase}</div>
                              </td>
                              <td>
                                <strong>{item.marcaNombre || 'Sin marca'}</strong>
                                <div>{item.modelo || 'Sin modelo'}</div>
                              </td>
                              <td>{item.calidad || 'Sin calidad'}</td>
                              <td>{item.stockDisponibleTotal || 0}</td>
                              <td>{item.lotesActivos || 0}</td>
                              <td>Bs {currency.format(Number(item.precioVentaSugerido || 0))}</td>
                              <td>
                                <button
                                  type="button"
                                  className="secondary compact"
                                  onClick={() =>
                                    setVarianteSeleccionada({
                                      id: item.varianteId,
                                      codigoVariante: item.codigoVariante,
                                      calidad: item.calidad,
                                      precioVentaSugerido: item.precioVentaSugerido,
                                      productoBase: {
                                        id: item.productoBaseId,
                                        nombreBase: item.nombreBase,
                                      },
                                    })
                                  }
                                >
                                  Ver detalle
                                </button>
                              </td>
                            </tr>
                          ))}
                        </tbody>
                      </table>
                    </div>

                    <PaginationRow pagina={inventarioOperativo} onChange={setPaginaOperativo} />
                  </>
                )}
              </section>

              <section className="catalogo-panel">
                <div className="inventory-panel-header inventory-panel-header-tight">
                  <div>
                    <h4>Detalle de variante</h4>
                    <p>
                      {detalleOperativo
                        ? `${detalleOperativo.codigoVariante} muestra solo lotes activos y visibles para venta.`
                        : 'Selecciona una variante en la tabla principal para ver su stock real por lote.'}
                    </p>
                  </div>
                  {detalleOperativo && <span className="chip">Stock {detalleOperativo.stockDisponibleTotal || 0}</span>}
                </div>

                {!detalleOperativo ? (
                  <EmptyState
                    title="Sin variante seleccionada"
                    description="El panel de detalle evita abrir otra tabla maestra paralela y mantiene el foco en la operacion."
                  />
                ) : (
                  <div className="catalogo-detail-stack">
                    <div className="catalogo-detail-hero">
                      <strong>{detalleOperativo.nombreBase}</strong>
                      <span>
                        {detalleOperativo.codigoVariante} · {detalleOperativo.calidad || 'Sin calidad'}
                      </span>
                      <span>
                        {detalleOperativo.marcaNombre || 'Sin marca'} · {detalleOperativo.modelo || 'Sin modelo'} · Precio sugerido Bs {currency.format(Number(detalleOperativo.precioVentaSugerido || 0))}
                      </span>
                    </div>

                    <div className="catalogo-kpi-row">
                      <article className="catalogo-kpi-card">
                        <span>Stock disponible</span>
                        <strong>{detalleOperativo.stockDisponibleTotal || 0}</strong>
                      </article>
                      <article className="catalogo-kpi-card">
                        <span>Lotes activos</span>
                        <strong>{detalleOperativo.lotesActivos || 0}</strong>
                      </article>
                      <article className="catalogo-kpi-card">
                        <span>Precio Venta</span>
                        <strong>Bs {currency.format(Number(detalleOperativo.precioVentaSugerido || 0))}</strong>
                      </article>
                    </div>

                    {lotesDetallePaginados.content.length === 0 ? (
                      <EmptyState
                        title="Sin lotes operativos"
                        description="Esta variante no tiene lotes activos disponibles para venta."
                      />
                    ) : (
                      <>
                        <div className="responsive-table-wrap">
                          <table className="table">
                            <thead>
                              <tr>
                                <th>Lote</th>
                                <th>Ingreso</th>
                                <th>Disponible</th>
                                <th>P. Compra</th>
                                <th>Visible</th>
                              </tr>
                            </thead>
                            <tbody>
                              {lotesDetallePaginados.content.map((lote) => (
                                <tr key={lote.id}>
                                  <td>
                                    <strong>{lote.codigoLote}</strong>
                                    <div>{lote.codigoProveedor || 'Sin cod. proveedor'}</div>
                                  </td>
                                  <td>{lote.fechaIngreso}</td>
                                  <td>{lote.cantidadRestante} / {lote.cantidadInicial}</td>
                                  <td>Bs {currency.format(Number(lote.costoUnitario || 0))}</td>
                                  <td>{lote.visibleEnVentas ? 'Si' : 'No'}</td>
                                </tr>
                              ))}
                            </tbody>
                          </table>
                        </div>

                        <PaginationRow pagina={lotesDetallePaginados} onChange={setPaginaDetalleLotes} />
                      </>
                    )}
                  </div>
                )}
              </section>

              <section className="catalogo-panel">
                <div className="inventory-panel-header inventory-panel-header-tight">
                  <div>
                    <h4>Historico de lotes</h4>
                    <p>El historico queda abajo y separado para auditar sin ensuciar la operacion diaria.</p>
                  </div>
                  <div className="inventory-header-actions catalogo-history-actions">
                    {varianteSeleccionada && (
                      <label className="catalogo-checkbox">
                        <input
                          type="checkbox"
                          checked={historialLigadoAVariante}
                          onChange={(event) => setHistorialLigadoAVariante(event.target.checked)}
                        />
                        Solo variante seleccionada
                      </label>
                    )}
                    <select value={estadoHistorialFiltro} onChange={(event) => setEstadoHistorialFiltro(event.target.value)}>
                      <option value="TODOS">Todos</option>
                      <option value="ACTIVO">Activos</option>
                      <option value="AGOTADO">Agotados</option>
                      <option value="CERRADO_MANUAL">Cerrados</option>
                    </select>
                    <span className="chip">{historialLotes.totalElements} lotes</span>
                  </div>
                </div>

                {historialLigadoAVariante && varianteSeleccionada && (
                  <div className="catalogo-context-note">
                    Viendo historico de: <strong>{seleccionVarianteTexto}</strong>
                  </div>
                )}

                {historialLotes.content.length === 0 ? (
                  <EmptyState
                    title="Sin historico para mostrar"
                    description="Ajusta el estado del lote o desliga el historico de la variante seleccionada."
                  />
                ) : (
                  <>
                    <div className="responsive-table-wrap">
                      <table className="table">
                        <thead>
                          <tr>
                            <th>Lote</th>
                            <th>Variante</th>
                            <th>Ingreso</th>
                            <th>Cierre</th>
                            <th>Costo</th>
                            <th>Inicial</th>
                            <th>Vendida</th>
                            <th>Restante</th>
                            <th>Estado</th>
                          </tr>
                        </thead>
                        <tbody>
                          {historialLotes.content.map((lote) => (
                            <tr key={lote.id}>
                              <td>
                                <strong>{lote.codigoLote}</strong>
                                <div>{lote.codigoProveedor || 'Sin cod. proveedor'}</div>
                              </td>
                              <td>
                                <strong>{lote.codigoVariante}</strong>
                                <div>{lote.nombreBase}</div>
                              </td>
                              <td>{lote.fechaIngreso}</td>
                              <td>{formatFecha(lote.fechaCierre)}</td>
                              <td>Bs {currency.format(Number(lote.costoUnitario || 0))}</td>
                              <td>{lote.cantidadInicial || 0}</td>
                              <td>{lote.cantidadVendida || 0}</td>
                              <td>{lote.cantidadRestante || 0}</td>
                              <td>{lote.estado}</td>
                            </tr>
                          ))}
                        </tbody>
                      </table>
                    </div>

                    <PaginationRow pagina={historialLotes} onChange={setPaginaHistorico} />
                  </>
                )}
              </section>
            </>
          ) : (
            <>
              <section className="catalogo-panel catalogo-panel-prominent">
                <div className="inventory-panel-header inventory-panel-header-tight">
                  <div>
                    <h4>Gestion de catalogo</h4>
                    <p>Este bloque sirve para mantener productos base, variantes y lotes manuales sin distraer la operacion diaria.</p>
                  </div>
                  <div className="inventory-header-actions">
                    <label className="catalogo-checkbox">
                      <input
                        type="checkbox"
                        checked={soloActivos}
                        onChange={(event) => setSoloActivos(event.target.checked)}
                      />
                      Solo activos
                    </label>
                    <span className="chip">{productosBase.length} bases</span>
                  </div>
                </div>

                <div className="catalogo-management-grid">
                  <section className="catalogo-panel catalogo-panel-nested">
                    <div className="inventory-panel-header inventory-panel-header-tight">
                      <div>
                        <h4>Productos base</h4>
                        <p>Definicion de la pieza madre sin precio, costo ni stock.</p>
                      </div>
                    </div>

                    {productosBasePaginados.content.length === 0 ? (
                      <EmptyState
                        title="Sin productos base"
                        description="Registra la primera pieza base para empezar a estructurar el catalogo."
                      />
                    ) : (
                      <>
                        <div className="responsive-table-wrap">
                          <table className="table">
                            <thead>
                              <tr>
                                <th>Codigo</th>
                                <th>Nombre</th>
                                <th>Marca</th>
                                <th>Modelo</th>
                                <th>Estado</th>
                                <th>Accion</th>
                              </tr>
                            </thead>
                            <tbody>
                              {productosBasePaginados.content.map((productoBase) => (
                                <tr
                                  key={productoBase.id}
                                  className={productoBaseSeleccionado?.id === productoBase.id ? 'catalogo-row-active' : ''}
                                >
                                  <td>{productoBase.codigoBase}</td>
                                  <td>
                                    <strong>{productoBase.nombreBase}</strong>
                                    <div>{productoBase.categoria?.nombre || 'Sin categoria'}</div>
                                  </td>
                                  <td>{productoBase.marca?.nombre || 'Sin marca'}</td>
                                  <td>{productoBase.modelo || 'Sin modelo'}</td>
                                  <td>{productoBase.activo ? 'Activo' : 'Inactivo'}</td>
                                  <td>
                                    <div className="inventory-inline-actions">
                                      <button
                                        type="button"
                                        className="secondary compact"
                                        onClick={() => setProductoBaseSeleccionado(productoBase)}
                                      >
                                        Ver variantes
                                      </button>
                                      <button
                                        type="button"
                                        className="secondary compact"
                                        onClick={() => editarProductoBase(productoBase)}
                                      >
                                        Editar
                                      </button>
                                    </div>
                                  </td>
                                </tr>
                              ))}
                            </tbody>
                          </table>
                        </div>

                        <PaginationRow pagina={productosBasePaginados} onChange={setPaginaProductosBase} />
                      </>
                    )}
                  </section>

                  <section className="catalogo-panel catalogo-panel-nested">
                    <div className="inventory-panel-header inventory-panel-header-tight">
                      <div>
                        <h4>Variantes</h4>
                        <p>
                          {productoBaseSeleccionado
                            ? `Versiones comerciales de ${productoBaseSeleccionado.nombreBase}.`
                            : 'Selecciona un producto base para enfocar las variantes.'}
                        </p>
                      </div>
                      <span className="chip">{variantes.length}</span>
                    </div>

                    {variantesPaginadas.content.length === 0 ? (
                      <EmptyState
                        title="Sin variantes"
                        description="Selecciona un producto base o crea una variante nueva para empezar."
                      />
                    ) : (
                      <>
                        <div className="responsive-table-wrap">
                          <table className="table">
                            <thead>
                              <tr>
                                <th>Codigo</th>
                                <th>Calidad</th>
                                <th>Color</th>
                                <th>P. sugerido</th>
                                <th>Stock lote</th>
                                <th>Accion</th>
                              </tr>
                            </thead>
                            <tbody>
                              {variantesPaginadas.content.map((variante) => (
                                <tr
                                  key={variante.id}
                                  className={varianteSeleccionada?.id === variante.id ? 'catalogo-row-active' : ''}
                                >
                                  <td>
                                    <strong>{variante.codigoVariante}</strong>
                                    <div>{variante.tipoPresentacion || 'Sin presentacion'}</div>
                                  </td>
                                  <td>{variante.calidad || 'Sin calidad'}</td>
                                  <td>{variante.color || 'Sin color'}</td>
                                  <td>Bs {currency.format(Number(variante.precioVentaSugerido || 0))}</td>
                                  <td>{variante.stockDisponibleTotal || 0}</td>
                                  <td>
                                    <div className="inventory-inline-actions">
                                      <button
                                        type="button"
                                        className="secondary compact"
                                        onClick={() => setVarianteSeleccionada(variante)}
                                      >
                                        Lotes
                                      </button>
                                      <button
                                        type="button"
                                        className="secondary compact"
                                        onClick={() => editarVariante(variante)}
                                      >
                                        Editar
                                      </button>
                                    </div>
                                  </td>
                                </tr>
                              ))}
                            </tbody>
                          </table>
                        </div>

                        <PaginationRow pagina={variantesPaginadas} onChange={setPaginaVariantes} />
                      </>
                    )}
                  </section>
                </div>
              </section>

              <section className="catalogo-panel">
                <div className="inventory-panel-header inventory-panel-header-tight">
                  <div>
                    <h4>Lotes por variante</h4>
                    <p>
                      {varianteSeleccionada
                        ? `Lotes de ${seleccionVarianteTexto}.`
                        : 'Selecciona una variante para administrar sus lotes manuales e historicos operativos.'}
                    </p>
                  </div>
                  <div className="inventory-header-actions">
                    <select value={estadoLoteFiltro} onChange={(event) => setEstadoLoteFiltro(event.target.value)}>
                      <option value="OPERATIVOS">Activos operativos</option>
                      <option value="ACTIVO">Activos</option>
                      <option value="AGOTADO">Agotados</option>
                      <option value="CERRADO_MANUAL">Cerrados</option>
                      <option value="TODOS">Todos</option>
                    </select>
                    <span className="chip">{lotes.length} lotes</span>
                  </div>
                </div>

                {!varianteSeleccionada ? (
                  <EmptyState
                    title="Sin variante seleccionada"
                    description="Primero elige una variante del catalogo para ver o crear sus lotes."
                  />
                ) : lotesCatalogoPaginados.content.length === 0 ? (
                  <EmptyState
                    title="Sin lotes para mostrar"
                    description="Cambia el estado del filtro o crea un lote nuevo para esta variante."
                  />
                ) : (
                  <>
                    <div className="responsive-table-wrap">
                      <table className="table">
                        <thead>
                          <tr>
                            <th>Lote</th>
                            <th>Ingreso</th>
                            <th>Disponible</th>
                            <th>Costo</th>
                            <th>Estado</th>
                            <th>Accion</th>
                          </tr>
                        </thead>
                        <tbody>
                          {lotesCatalogoPaginados.content.map((lote) => (
                            <tr key={lote.id}>
                              <td>
                                <strong>{lote.codigoLote}</strong>
                                <div>{lote.codigoProveedor || 'Sin cod. proveedor'}</div>
                              </td>
                              <td>{lote.fechaIngreso}</td>
                              <td>{lote.cantidadDisponible} / {lote.cantidadInicial}</td>
                              <td>Bs {currency.format(Number(lote.costoUnitario || 0))}</td>
                              <td>{lote.estado}</td>
                              <td>
                                <div className="inventory-inline-actions">
                                  <button
                                    type="button"
                                    className="secondary compact"
                                    onClick={() => editarLote(lote)}
                                  >
                                    Editar
                                  </button>
                                  <button
                                    type="button"
                                    className="secondary compact"
                                    onClick={() => cerrarLoteManual(lote)}
                                  >
                                    Cerrar
                                  </button>
                                </div>
                              </td>
                            </tr>
                          ))}
                        </tbody>
                      </table>
                    </div>

                    <PaginationRow pagina={lotesCatalogoPaginados} onChange={setPaginaLotesCatalogo} />
                  </>
                )}
              </section>
            </>
          )}
        </div>
      </div>

      <Modal
        open={modalBaseOpen}
        onClose={() => setModalBaseOpen(false)}
        title={productoBaseEditando ? 'Editar producto base' : 'Nuevo producto base'}
        subtitle="Define solo la pieza base del catalogo, sin stock ni precios operativos."
      >
        <form className="entity-form" onSubmit={guardarProductoBase}>
          <div className="form-grid two-columns">
            <label>
              <span>Codigo base</span>
              <input
                value={productoBaseForm.codigoBase}
                onChange={(event) => {
                  setCodigoBaseManual(true);
                  setProductoBaseForm((actual) => ({ ...actual, codigoBase: event.target.value }));
                }}
                required
              />
            </label>
            <label>
              <span>Nombre base</span>
              <input
                value={productoBaseForm.nombreBase}
                onChange={(event) => setProductoBaseForm((actual) => ({ ...actual, nombreBase: event.target.value }))}
                required
              />
            </label>
            <label>
              <span>Categoria</span>
              <div className="catalogo-selector-with-action">
                <select
                  value={productoBaseForm.categoriaId}
                  onChange={(event) =>
                    setProductoBaseForm((actual) => ({ ...actual, categoriaId: event.target.value }))
                  }
                  required
                >
                  <option value="">Selecciona una categoria</option>
                  {categorias.map((categoria) => (
                    <option key={categoria.id} value={categoria.id}>
                      {categoria.nombre}
                    </option>
                  ))}
                </select>
                <button
                  type="button"
                  className="secondary compact catalogo-selector-action"
                  onClick={onOpenCategorias}
                  title="Administrar categorias"
                >
                  <FolderTree size={16} />
                  Categoria
                </button>
              </div>
            </label>
            <label>
              <span>Marca</span>
              <div className="catalogo-selector-with-action">
                <select
                  value={productoBaseForm.marcaId}
                  onChange={(event) =>
                    setProductoBaseForm((actual) => ({ ...actual, marcaId: event.target.value }))
                  }
                  required
                >
                  <option value="">Selecciona una marca</option>
                  {marcas.map((marca) => (
                    <option key={marca.id} value={marca.id}>
                      {marca.nombre}
                    </option>
                  ))}
                </select>
                <button
                  type="button"
                  className="secondary compact catalogo-selector-action"
                  onClick={onOpenMarcas}
                  title="Administrar marcas"
                >
                  <Tags size={16} />
                  Marca
                </button>
              </div>
            </label>
            <label>
              <span>Modelo</span>
              <input
                value={productoBaseForm.modelo}
                onChange={(event) => setProductoBaseForm((actual) => ({ ...actual, modelo: event.target.value }))}
              />
            </label>
            <label className="catalogo-checkbox">
              <span>Activo</span>
              <input
                type="checkbox"
                checked={productoBaseForm.activo}
                onChange={(event) => setProductoBaseForm((actual) => ({ ...actual, activo: event.target.checked }))}
              />
            </label>
          </div>
          {!productoBaseEditando && codigoBaseSugerido && (
            <div className="catalogo-context-note">
              Codigo sugerido automatico: <strong>{codigoBaseSugerido}</strong>. Puedes editarlo si necesitas otro formato.
            </div>
          )}
          <label>
            <span>Descripcion</span>
            <textarea
              value={productoBaseForm.descripcion}
              onChange={(event) => setProductoBaseForm((actual) => ({ ...actual, descripcion: event.target.value }))}
            />
          </label>
          <div className="modal-actions-row">
            <button type="button" className="secondary" onClick={() => setModalBaseOpen(false)}>
              Cancelar
            </button>
            <button type="submit">{productoBaseEditando ? 'Actualizar base' : 'Crear base'}</button>
          </div>
        </form>
      </Modal>

      <Modal
        open={modalVarianteOpen}
        onClose={() => setModalVarianteOpen(false)}
        title={varianteEditando ? 'Editar variante' : 'Nueva variante'}
        subtitle="La variante define calidad, presentacion y precio sugerido, sin stock real todavia."
      >
        <form className="entity-form" onSubmit={guardarVariante}>
          <div className="form-grid two-columns">
            <label>
              <span>Producto base</span>
              <select
                value={varianteForm.productoBaseId}
                onChange={(event) => setVarianteForm((actual) => ({ ...actual, productoBaseId: event.target.value }))}
                required
              >
                <option value="">Selecciona un producto base</option>
                {productosBase.map((productoBase) => (
                  <option key={productoBase.id} value={productoBase.id}>
                    {productoBase.codigoBase} - {productoBase.nombreBase}
                  </option>
                ))}
              </select>
            </label>
            <label>
              <span>Codigo variante</span>
              <input
                value={varianteForm.codigoVariante}
                onChange={(event) => {
                  setCodigoVarianteManual(true);
                  setVarianteForm((actual) => ({ ...actual, codigoVariante: event.target.value }));
                }}
                required
              />
            </label>
            <label>
              <span>Calidad</span>
              <input
                value={varianteForm.calidad}
                onChange={(event) => setVarianteForm((actual) => ({ ...actual, calidad: event.target.value }))}
                required
              />
            </label>
            <label>
              <span>Tipo presentacion</span>
              <input
                value={varianteForm.tipoPresentacion}
                onChange={(event) => setVarianteForm((actual) => ({ ...actual, tipoPresentacion: event.target.value }))}
              />
            </label>
            <label>
              <span>Color</span>
              <input
                value={varianteForm.color}
                onChange={(event) => setVarianteForm((actual) => ({ ...actual, color: event.target.value }))}
              />
            </label>
            <label>
              <span>Precio venta sugerido</span>
              <input
                type="number"
                min="0"
                step="0.01"
                value={varianteForm.precioVentaSugerido}
                onChange={(event) => setVarianteForm((actual) => ({ ...actual, precioVentaSugerido: event.target.value }))}
                required
              />
            </label>
            <label>
              <span>Stock por lotes activos</span>
              <input value={varianteEditando?.stockDisponibleTotal ?? 0} readOnly />
            </label>
            <label className="catalogo-checkbox">
              <span>Activo</span>
              <input
                type="checkbox"
                checked={varianteForm.activo}
                onChange={(event) => setVarianteForm((actual) => ({ ...actual, activo: event.target.checked }))}
              />
            </label>
          </div>
          {!varianteEditando && codigoVarianteSugerido && (
            <div className="catalogo-context-note">
              Codigo sugerido automatico: <strong>{codigoVarianteSugerido}</strong>. Puedes editarlo si necesitas otro formato.
            </div>
          )}
          <div className="modal-actions-row">
            <button type="button" className="secondary" onClick={() => setModalVarianteOpen(false)}>
              Cancelar
            </button>
            <button type="submit">{varianteEditando ? 'Actualizar variante' : 'Crear variante'}</button>
          </div>
        </form>
      </Modal>

      <Modal
        open={modalLoteOpen}
        onClose={() => setModalLoteOpen(false)}
        title={loteEditando ? 'Editar lote' : 'Nuevo lote'}
        subtitle="Cada lote pertenece a una sola variante y mantiene su propio costo y disponibilidad."
      >
        <form className="entity-form" onSubmit={guardarLote}>
          <div className="form-grid two-columns">
            <label>
              <span>Variante</span>
              <select
                value={loteForm.varianteId}
                onChange={(event) => setLoteForm((actual) => ({ ...actual, varianteId: event.target.value }))}
                required
              >
                <option value="">Selecciona una variante</option>
                {variantes.map((variante) => (
                  <option key={variante.id} value={variante.id}>
                    {variante.codigoVariante} - {variante.productoBase?.nombreBase}
                  </option>
                ))}
              </select>
            </label>
            <label>
              <span>Codigo lote</span>
              <input
                value={loteForm.codigoLote}
                onChange={(event) => setLoteForm((actual) => ({ ...actual, codigoLote: event.target.value }))}
                required
              />
            </label>
            <label>
              <span>Codigo proveedor</span>
              <input
                value={loteForm.codigoProveedor}
                onChange={(event) => setLoteForm((actual) => ({ ...actual, codigoProveedor: event.target.value }))}
              />
            </label>
            <label>
              <span>Fecha ingreso</span>
              <input
                type="date"
                value={loteForm.fechaIngreso}
                onChange={(event) => setLoteForm((actual) => ({ ...actual, fechaIngreso: event.target.value }))}
                required
              />
            </label>
            <label>
              <span>Cantidad inicial</span>
              <input
                type="number"
                min="0"
                value={loteForm.cantidadInicial}
                onChange={(event) => setLoteForm((actual) => ({ ...actual, cantidadInicial: event.target.value }))}
                required
              />
            </label>
            <label>
              <span>Cantidad disponible</span>
              <input
                type="number"
                min="0"
                value={loteForm.cantidadDisponible}
                onChange={(event) => setLoteForm((actual) => ({ ...actual, cantidadDisponible: event.target.value }))}
                required
              />
            </label>
            <label>
              <span>Costo unitario</span>
              <input
                type="number"
                min="0"
                step="0.01"
                value={loteForm.costoUnitario}
                onChange={(event) => setLoteForm((actual) => ({ ...actual, costoUnitario: event.target.value }))}
                required
              />
            </label>
            <label>
              <span>Subtotal compra</span>
              <input
                type="number"
                min="0"
                step="0.01"
                value={loteForm.subtotalCompra}
                onChange={(event) => setLoteForm((actual) => ({ ...actual, subtotalCompra: event.target.value }))}
                placeholder="Opcional: se calcula si no lo indicas"
              />
            </label>
            <label>
              <span>Compra origen</span>
              <input
                type="number"
                min="0"
                value={loteForm.compraId}
                onChange={(event) => setLoteForm((actual) => ({ ...actual, compraId: event.target.value }))}
                placeholder="Opcional"
              />
            </label>
            <label className="catalogo-checkbox">
              <span>Activo</span>
              <input
                type="checkbox"
                checked={loteForm.activo}
                onChange={(event) => setLoteForm((actual) => ({ ...actual, activo: event.target.checked }))}
              />
            </label>
            <label className="catalogo-checkbox">
              <span>Visible en ventas</span>
              <input
                type="checkbox"
                checked={loteForm.visibleEnVentas}
                onChange={(event) => setLoteForm((actual) => ({ ...actual, visibleEnVentas: event.target.checked }))}
              />
            </label>
          </div>
          <label>
            <span>Motivo de cierre</span>
            <textarea
              value={loteForm.motivoCierre}
              onChange={(event) => setLoteForm((actual) => ({ ...actual, motivoCierre: event.target.value }))}
              placeholder="Obligatorio si cierras o inactivas un lote con stock disponible"
            />
          </label>
          <div className="alert">
            Si dejas la cantidad disponible en 0, el lote pasa automaticamente a AGOTADO y deja de verse en el flujo operativo.
          </div>
          <div className="modal-actions-row">
            <button type="button" className="secondary" onClick={() => setModalLoteOpen(false)}>
              Cancelar
            </button>
            <button type="submit">{loteEditando ? 'Actualizar lote' : 'Crear lote'}</button>
          </div>
        </form>
      </Modal>
    </section>
  );
}
