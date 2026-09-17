import { useEffect, useState, useCallback } from 'react'

const API_BASE = 'http://localhost:8080/api'

const PRODUCTS = [
  { id: 'P100', label: 'P100 · Wireless Mouse' },
  { id: 'P200', label: 'P200 · Mechanical Keyboard' },
  { id: 'P300', label: 'P300 · USB-C Hub' },
]

function formatTime(iso) {
  if (!iso) return ''
  const d = new Date(iso)
  return d.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', second: '2-digit' })
}

export default function App() {
  const [cart, setCart] = useState([{ productId: PRODUCTS[0].id, quantity: 1 }])
  const [orderResult, setOrderResult] = useState(null)
  const [orderError, setOrderError] = useState(null)
  const [submitting, setSubmitting] = useState(false)

  const [inventory, setInventory] = useState([])
  const [orders, setOrders] = useState([])
  const [notifications, setNotifications] = useState([])

  const refreshAll = useCallback(async () => {
    try {
      const [invRes, ordersRes, notifRes] = await Promise.all([
        fetch(`${API_BASE}/inventory`),
        fetch(`${API_BASE}/orders`),
        fetch(`${API_BASE}/notifications`),
      ])
      setInventory(await invRes.json())
      setOrders(await ordersRes.json())
      setNotifications(await notifRes.json())
    } catch (err) {
      console.error('Failed to refresh dashboard data', err)
    }
  }, [])

  useEffect(() => {
    refreshAll()
  }, [refreshAll])

  function addCartLine() {
    setCart((c) => [...c, { productId: PRODUCTS[0].id, quantity: 1 }])
  }

  function removeCartLine(index) {
    setCart((c) => c.filter((_, i) => i !== index))
  }

  function updateCartLine(index, field, value) {
    setCart((c) => c.map((line, i) => (i === index ? { ...line, [field]: value } : line)))
  }

  async function handleSubmitOrder(e) {
    e.preventDefault()
    setSubmitting(true)
    setOrderError(null)
    setOrderResult(null)

    try {
      const res = await fetch(`${API_BASE}/orders`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          items: cart.map((line) => ({ productId: line.productId, quantity: Number(line.quantity) })),
        }),
      })

      const data = await res.json()

      if (!res.ok) {
        setOrderError(typeof data === 'object' ? JSON.stringify(data) : String(data))
      } else {
        setOrderResult(data)
        await refreshAll()
      }
    } catch (err) {
      setOrderError(`Request failed — is the backend running on :8080? (${err.message})`)
    } finally {
      setSubmitting(false)
    }
  }

  async function handleCancel(orderId) {
    try {
      const res = await fetch(`${API_BASE}/orders/${orderId}/cancel`, { method: 'POST' })
      const data = await res.json()
      if (!res.ok) {
        alert(`Cancel failed: ${data.error ?? JSON.stringify(data)}`)
      }
      await refreshAll()
    } catch (err) {
      alert(`Cancel request failed: ${err.message}`)
    }
  }

  return (
    <div className="console">
      <header className="console__header">
        <div className="console__mark">Stock Console</div>
        <div className="console__meta">Order &amp; Inventory · edu.cit.carcueva</div>
      </header>

      <main className="console__grid">
        <section className="panel">
          <h2>Compose order</h2>

          <form onSubmit={handleSubmitOrder} className="composer">
            {cart.map((line, index) => (
              <div className="composer__row" key={index}>
                <select
                  value={line.productId}
                  onChange={(e) => updateCartLine(index, 'productId', e.target.value)}
                >
                  {PRODUCTS.map((p) => (
                    <option key={p.id} value={p.id}>
                      {p.label}
                    </option>
                  ))}
                </select>
                <input
                  className="qty-input"
                  type="number"
                  min="1"
                  value={line.quantity}
                  onChange={(e) => updateCartLine(index, 'quantity', e.target.value)}
                />
                {cart.length > 1 && (
                  <button type="button" className="btn-text" onClick={() => removeCartLine(index)}>
                    Remove
                  </button>
                )}
              </div>
            ))}

            <div className="composer__actions">
              <button type="button" className="btn-text" onClick={addCartLine}>
                + Add item
              </button>
              <button type="submit" className="btn-primary" disabled={submitting}>
                {submitting ? 'Placing order…' : 'Place order'}
              </button>
            </div>
          </form>

          {orderError && (
            <div className="log-line log-line--rejected">
              <span className="log-line__status">Error</span>
              <span>{orderError}</span>
            </div>
          )}

          {orderResult && (
            <div className={`log-line log-line--${orderResult.status.toLowerCase()}`}>
              <div className="log-line__head">
                <span className="log-line__status">{orderResult.status}</span>
                <span className="mono">#{orderResult.orderId}</span>
              </div>
              {orderResult.reason && <p className="log-line__reason">{orderResult.reason}</p>}
              <ul className="log-line__items">
                {orderResult.items?.map((item, i) => (
                  <li key={i}>
                    <span className="mono">
                      {item.productId} × {item.quantity}
                    </span>
                    {item.reason ? <span className="muted"> — {item.reason}</span> : null}
                  </li>
                ))}
              </ul>
            </div>
          )}
        </section>

        <section className="panel">
          <h2>Inventory</h2>
          <table className="data-table">
            <thead>
              <tr>
                <th>Product</th>
                <th>Name</th>
                <th className="align-right">Stock</th>
              </tr>
            </thead>
            <tbody>
              {inventory.map((item) => (
                <tr key={item.productId} className={item.stock < 5 ? 'row--low-stock' : ''}>
                  <td className="mono">{item.productId}</td>
                  <td>{item.name}</td>
                  <td className="mono align-right">{item.stock}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </section>
      </main>

      <section className="panel panel--full">
        <h2>Order log</h2>
        <div className="order-log">
          {orders
            .slice()
            .reverse()
            .map((order) => (
              <div key={order.orderId} className={`order-row order-row--${order.status.toLowerCase()}`}>
                <div className="order-row__main">
                  <span className="mono order-row__id">#{order.orderId}</span>
                  <span className="order-row__status">{order.status}</span>
                  <span className="mono order-row__items">
                    {order.items.map((item) => `${item.productId}×${item.quantity}`).join('  ')}
                  </span>
                  {order.reason && <span className="muted order-row__reason">{order.reason}</span>}
                </div>
                {order.status === 'CONFIRMED' && (
                  <button className="btn-text btn-text--danger" onClick={() => handleCancel(order.orderId)}>
                    Cancel
                  </button>
                )}
              </div>
            ))}
          {orders.length === 0 && <p className="muted">No orders placed yet.</p>}
        </div>
      </section>

      <section className="panel panel--full">
        <h2>Activity</h2>
        <div className="activity-feed">
          {notifications.map((n) => (
            <div className="activity-feed__row" key={n.notificationId}>
              <span className="mono activity-feed__time">{formatTime(n.createdAt)}</span>
              <span>{n.message}</span>
            </div>
          ))}
          {notifications.length === 0 && <p className="muted">No activity yet.</p>}
        </div>
      </section>
    </div>
  )
}
