export default function TableCard({ title, children }) {
  return (
    <div className="card table-card">
      {title && <h3>{title}</h3>}
      {children}
    </div>
  );
}
