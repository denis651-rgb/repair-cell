import { useEffect, useState } from 'react';
import { api } from '../api/api';
import Modal from '../components/common/Modal';
import CatalogoBaseManager from '../components/inventory/CatalogoBaseManager';
import '../styles/pages/inventario.css';

const categoriaInicial = { nombre: '', descripcion: '' };
const marcaInicial = { nombre: '', descripcion: '', activa: true };

const crearErrorVisual = (titulo, error) => ({
  titulo,
  detalle: error?.message || 'No se pudo completar la operacion.',
});

export default function InventoryPage() {
  const [categorias, setCategorias] = useState([]);
  const [marcas, setMarcas] = useState([]);
  const [modalCategoriaOpen, setModalCategoriaOpen] = useState(false);
  const [modalMarcaOpen, setModalMarcaOpen] = useState(false);
  const [categoriaForm, setCategoriaForm] = useState(categoriaInicial);
  const [marcaForm, setMarcaForm] = useState(marcaInicial);
  const [categoriaEditando, setCategoriaEditando] = useState(null);
  const [marcaEditando, setMarcaEditando] = useState(null);
  const [error, setError] = useState(null);

  const cargarResumen = async () => {
    try {
      setError(null);
      const [listaCategorias, listaMarcas] = await Promise.all([
        api.get('/inventario/categorias'),
        api.get('/inventario/marcas'),
      ]);
      setCategorias(listaCategorias || []);
      setMarcas(listaMarcas || []);
    } catch (err) {
      setError(crearErrorVisual('No se pudo cargar el resumen de inventario.', err));
    }
  };

  useEffect(() => {
    cargarResumen();
  }, []);

  const abrirCategorias = () => {
    setCategoriaEditando(null);
    setCategoriaForm(categoriaInicial);
    setModalCategoriaOpen(true);
  };

  const abrirMarcas = () => {
    setMarcaEditando(null);
    setMarcaForm(marcaInicial);
    setModalMarcaOpen(true);
  };

  const editarCategoria = (categoria) => {
    setCategoriaEditando(categoria);
    setCategoriaForm({
      nombre: categoria.nombre || '',
      descripcion: categoria.descripcion || '',
    });
  };

  const editarMarca = (marca) => {
    setMarcaEditando(marca);
    setMarcaForm({
      nombre: marca.nombre || '',
      descripcion: marca.descripcion || '',
      activa: marca.activa ?? true,
    });
  };

  const guardarCategoria = async (event) => {
    event.preventDefault();
    try {
      if (categoriaEditando) {
        await api.put(`/inventario/categorias/${categoriaEditando.id}`, categoriaForm);
      } else {
        await api.post('/inventario/categorias', categoriaForm);
      }
      setCategoriaForm(categoriaInicial);
      setCategoriaEditando(null);
      await cargarResumen();
    } catch (err) {
      setError(
        crearErrorVisual(
          categoriaEditando ? 'No se pudo actualizar la categoria.' : 'No se pudo guardar la categoria.',
          err,
        ),
      );
    }
  };

  const guardarMarca = async (event) => {
    event.preventDefault();
    try {
      if (marcaEditando) {
        await api.put(`/inventario/marcas/${marcaEditando.id}`, marcaForm);
      } else {
        await api.post('/inventario/marcas', marcaForm);
      }
      setMarcaForm(marcaInicial);
      setMarcaEditando(null);
      await cargarResumen();
    } catch (err) {
      setError(
        crearErrorVisual(
          marcaEditando ? 'No se pudo actualizar la marca.' : 'No se pudo guardar la marca.',
          err,
        ),
      );
    }
  };

  const eliminarCategoria = async (categoria) => {
    if (!window.confirm(`Eliminar la categoria "${categoria.nombre}"?`)) return;
    try {
      await api.delete(`/inventario/categorias/${categoria.id}`);
      await cargarResumen();
    } catch (err) {
      setError(crearErrorVisual(`No se pudo eliminar la categoria "${categoria.nombre}".`, err));
    }
  };

  const eliminarMarca = async (marca) => {
    if (!window.confirm(`Eliminar la marca "${marca.nombre}"?`)) return;
    try {
      await api.delete(`/inventario/marcas/${marca.id}`);
      await cargarResumen();
    } catch (err) {
      setError(crearErrorVisual(`No se pudo eliminar la marca "${marca.nombre}".`, err));
    }
  };

  return (
    <div className="page-stack inventory-page">
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

      <section className="inventory-catalogo-stage">
        <CatalogoBaseManager
          categorias={categorias}
          marcas={marcas}
          onOpenCategorias={abrirCategorias}
          onOpenMarcas={abrirMarcas}
        />
      </section>

      <Modal
        open={modalCategoriaOpen}
        onClose={() => setModalCategoriaOpen(false)}
        title="CRUD de categorias"
        subtitle="Crea, edita o elimina categorias maestras del inventario."
        size="lg"
      >
        <div className="inventory-admin-modal">
          <form className="entity-form" onSubmit={guardarCategoria}>
            <div className="form-grid two-columns">
              <label>
                <span>Nombre</span>
                <input
                  value={categoriaForm.nombre}
                  onChange={(event) => setCategoriaForm((actual) => ({ ...actual, nombre: event.target.value }))}
                  required
                />
              </label>
              <label>
                <span>Descripcion</span>
                <input
                  value={categoriaForm.descripcion}
                  onChange={(event) => setCategoriaForm((actual) => ({ ...actual, descripcion: event.target.value }))}
                />
              </label>
            </div>
            <div className="modal-actions-row">
              {categoriaEditando ? (
                <button
                  type="button"
                  className="secondary"
                  onClick={() => {
                    setCategoriaEditando(null);
                    setCategoriaForm(categoriaInicial);
                  }}
                >
                  Cancelar edicion
                </button>
              ) : (
                <span />
              )}
              <button type="submit">{categoriaEditando ? 'Actualizar categoria' : 'Guardar categoria'}</button>
            </div>
          </form>

          <div className="inventory-admin-list">
            {categorias.map((categoria) => (
              <article key={categoria.id} className="inventory-admin-item">
                <div>
                  <strong>{categoria.nombre}</strong>
                  <p>{categoria.descripcion || 'Sin descripcion'}</p>
                </div>
                <div className="inventory-admin-actions">
                  <button type="button" className="secondary compact" onClick={() => editarCategoria(categoria)}>
                    Editar
                  </button>
                  <button
                    type="button"
                    className="secondary compact inventory-danger-soft"
                    onClick={() => eliminarCategoria(categoria)}
                  >
                    Eliminar
                  </button>
                </div>
              </article>
            ))}
          </div>
        </div>
      </Modal>

      <Modal
        open={modalMarcaOpen}
        onClose={() => setModalMarcaOpen(false)}
        title="CRUD de marcas"
        subtitle="Gestiona las marcas maestras que usan el catalogo y las compras."
        size="lg"
      >
        <div className="inventory-admin-modal">
          <form className="entity-form" onSubmit={guardarMarca}>
            <div className="form-grid two-columns">
              <label>
                <span>Nombre</span>
                <input
                  value={marcaForm.nombre}
                  onChange={(event) => setMarcaForm((actual) => ({ ...actual, nombre: event.target.value }))}
                  required
                />
              </label>
              <label>
                <span>Descripcion</span>
                <input
                  value={marcaForm.descripcion}
                  onChange={(event) => setMarcaForm((actual) => ({ ...actual, descripcion: event.target.value }))}
                />
              </label>
            </div>
            <div className="modal-actions-row">
              {marcaEditando ? (
                <button
                  type="button"
                  className="secondary"
                  onClick={() => {
                    setMarcaEditando(null);
                    setMarcaForm(marcaInicial);
                  }}
                >
                  Cancelar edicion
                </button>
              ) : (
                <span />
              )}
              <button type="submit">{marcaEditando ? 'Actualizar marca' : 'Guardar marca'}</button>
            </div>
          </form>

          <div className="inventory-admin-list">
            {marcas.map((marca) => (
              <article key={marca.id} className="inventory-admin-item">
                <div>
                  <strong>{marca.nombre}</strong>
                  <p>{marca.descripcion || 'Sin descripcion'}</p>
                </div>
                <div className="inventory-admin-actions">
                  <button type="button" className="secondary compact" onClick={() => editarMarca(marca)}>
                    Editar
                  </button>
                  <button
                    type="button"
                    className="secondary compact inventory-danger-soft"
                    onClick={() => eliminarMarca(marca)}
                  >
                    Eliminar
                  </button>
                </div>
              </article>
            ))}
          </div>
        </div>
      </Modal>
    </div>
  );
}
