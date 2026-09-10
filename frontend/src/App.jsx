import { useState } from 'react'

const API_URL = 'http://localhost:8080/api/orders'

// Matches the seed data in sql/schema.sql. There's no GET /api/products
// endpoint in this lab's scope, so the dropdown is a static list.
const PRODUCTS = [
  { id: 'P100', label: 'P100 — Wireless Mouse' },
  { id: 'P200', label: 'P200 — Mechanical Keyboard' },
  { id: 'P300', label: 'P300 — USB-C Hub' },
]

export default function App() {
  const [productId, setProductId] = useState(PRODUCTS[0].id)
  const [quantity, setQuantity] = useState(1)
  const [result, setResult] = useState(null)
  const [error, setError] = useState(null)
  const [loading, setLoading] = useState(false)

  async function handleSubmit(e) {
    e.preventDefault()
    setLoading(true)
    setError(null)
    setResult(null)

    try {
      const res = await fetch(API_URL, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ productId, quantity: Number(quantity) }),
      })

      const data = await res.json()

      if (!res.ok) {
        setError(typeof data === 'object' ? JSON.stringify(data) : String(data))
      } else {
        setResult(data)
      }
    } catch (err) {
      setError(`Request failed: ${err.message}. Is the backend running on :8080?`)
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="page">
      <h1>Place an Order</h1>

      <form onSubmit={handleSubmit} className="order-form">
        <label>
          Product
          <select value={productId} onChange={(e) => setProductId(e.target.value)}>
            {PRODUCTS.map((p) => (
              <option key={p.id} value={p.id}>
                {p.label}
              </option>
            ))}
          </select>
        </label>

        <label>
          Quantity
          <input
            type="number"
            min="1"
            value={quantity}
            onChange={(e) => setQuantity(e.target.value)}
          />
        </label>

        <button type="submit" disabled={loading}>
          {loading ? 'Submitting…' : 'Submit Order'}
        </button>
      </form>

      {error && <div className="result rejected">Error: {error}</div>}

      {result && (
        <div className={`result ${result.status === 'CONFIRMED' ? 'confirmed' : 'rejected'}`}>
          <h2>{result.status}</h2>
          {result.orderId && <p>Order ID: {result.orderId}</p>}
          {result.reason && <p>Reason: {result.reason}</p>}
          {result.inventory && (
            <p>
              Remaining stock for {result.inventory.name} ({result.inventory.productId}):{' '}
              {result.inventory.stock}
            </p>
          )}
        </div>
      )}
    </div>
  )
}
