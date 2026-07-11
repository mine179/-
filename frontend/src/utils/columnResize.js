export function columnStyle(widths, scope, key, fallback) {
  const width = widths?.[scope]?.[key] || fallback
  return { width: `${width}px` }
}

export function defaultColumnWidths(keys) {
  const result = {}
  keys.forEach((key) => {
    result[key] = defaultWidth(key)
  })
  return result
}

function defaultWidth(key) {
  if (key === 'id') return 72
  if (key === 'status' || key === 'enabled' || key === 'role') return 130
  if (key.includes('price')) return 130
  if (key.includes('username')) return 180
  if (key.includes('order')) return 180
  if (key.includes('model') || key.includes('spec')) return 220
  if (key.includes('material') || key.includes('craft')) return 180
  if (key.includes('remark')) return 240
  if (key.includes('created') || key.includes('updated')) return 180
  if (key.includes('resolution') || key.includes('category')) return 150
  if (key.includes('code')) return 150
  if (key.includes('permissions')) return 180
  return 130
}

export function startColumnResize(widths, scope, keys, key, event, minWidth = 24) {
  event.preventDefault()
  event.stopPropagation()

  if (!keys.includes(key)) return
  const current = {
    ...(widths[scope] || {})
  }
  const startWidth = current[key] || defaultWidth(key)
  const startX = event.clientX

  function move(moveEvent) {
    const width = Math.max(minWidth, startWidth + moveEvent.clientX - startX)
    widths[scope] = {
      ...(widths[scope] || {}),
      [key]: width
    }
  }

  function up() {
    document.removeEventListener('mousemove', move)
    document.removeEventListener('mouseup', up)
  }

  document.addEventListener('mousemove', move)
  document.addEventListener('mouseup', up)
}
