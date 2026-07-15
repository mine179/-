<script setup>
import { computed, nextTick, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import request from '../request'
import { productFields } from '../data/productFields'
import { columnLabel, formatCell } from '../data/columnLabels'
import { columnStyle, defaultColumnWidths, startColumnResize } from '../utils/columnResize'

const router = useRouter()
const user = JSON.parse(localStorage.getItem('user') || '{}')
const message = ref('')
const quoteModalTitle = '\u62a5\u4ef7'
const closeText = '\u5173\u95ed'
const saveTableStyleText = '\u4fdd\u5b58\u8868\u683c\u6837\u5f0f'

const views = [
  ['internal', '主表'],
  ['products', '\u4f9b\u5e94\u5546\u4ea7\u54c1\u4ef7\u683c\u8868']
]

const supplierInternalHiddenColumns = [
  'serial_no',
  'serialNo',
  'sale_price',
  'salePrice',
  'purchase_price',
  'purchasePrice',
  'price_valid_until',
  'priceValidUntil',
  'manual_price_1',
  'manual_price_2',
  'manual_price_3',
  'manual_price_4',
  'manual_price_5',
  'order_price_1',
  'order_price_2',
  'order_price_3',
  'order_price_4',
  'order_price_5',
  'master_product_id',
  'masterProductId',
  'created_at',
  'createdAt',
  'updated_at',
  'updatedAt'
]
const supplierProductFields = productFields.filter(([key]) => key !== 'salePrice' && key !== 'priceValidUntil')
const supplierProductCreateFields = productFields.filter(([key]) => key !== 'salePrice')
const supplierProductDisplayFields = productFields.filter(([key]) => key !== 'salePrice')
const quoteProductFields = supplierProductFields.filter(([key]) => key !== 'priceValidUntil')
const productColumns = ['id', 'link_status', 'quote_status', ...supplierProductDisplayFields.map(([key]) => key), 'updatedAt']
const internalColumns = ['id', ...productFields.map(([key]) => key), 'updatedAt']
const quoteColumns = ['id', 'status', ...quoteProductFields.map(([key]) => key), 'updatedAt']
const quoteItemColumns = ['brand', 'code', 'newCode', 'craftMaterial', 'specModel', 'commonModel', 'purchasePrice', 'priceValidUntil']

const state = reactive({
  view: 'products',
  product: {},
  internalProducts: [],
  products: [],
  quoteOrders: [],
  quoteItems: [],
  selectedOrders: [],
  activeOrderNo: '',
  passwordForm: { password: '', confirmPassword: '' },
  modal: '',
  file: null,
  quoteFile: null,
  page: 1,
  pageSize: 1000,
  globalSearch: '',
  columnFilters: {},
  filterMenu: '',
  filterSearch: '',
  filterMenuStyle: {},
  actionMenu: '',
  importMode: '',
  columnOrder: {},
  columnWidths: {},
  dragColumn: '',
  sidebarCollapsed: false,
  quoteReminderShown: false
})

const pageSizeOptions = [1000, 2000, 3000, 5000]

const sourceRows = computed(() => state.view === 'internal' ? state.internalProducts : state.products)
const baseColumns = computed(() => {
  if (state.view === 'internal') {
    return internalColumns.filter(key => !supplierInternalHiddenColumns.includes(key))
  }
  return productColumns
})
const columns = computed(() => {
  const saved = state.columnOrder[state.view] || []
  const known = saved.filter(key => baseColumns.value.includes(key))
  const missing = baseColumns.value.filter(key => !known.includes(key))
  return [...known, ...missing]
})
const filteredRows = computed(() => sourceRows.value.filter(row => rowMatches(row)))
const pageTotal = computed(() => Math.max(1, Math.ceil(filteredRows.value.length / state.pageSize)))
const pagedRows = computed(() => {
  const start = (state.page - 1) * state.pageSize
  return filteredRows.value.slice(start, start + state.pageSize)
})
const selectedOrderSet = computed(() => new Set(state.selectedOrders))
const pageOrderNos = computed(() => pagedRows.value.map(orderNoOf).filter(Boolean))
const pageOrdersChecked = computed(() => pageOrderNos.value.length > 0 && pageOrderNos.value.every(orderNo => selectedOrderSet.value.has(orderNo)))
const dataColumnWidths = computed(() => defaultColumnWidths(columns.value))

function toast(text) {
  message.value = text
  setTimeout(() => { if (message.value === text) message.value = '' }, 3000)
}

function fieldInputType(fieldKey) {
  return fieldKey === 'priceValidUntil' ? 'date' : 'text'
}

async function load() {
  if (state.view === 'internal') {
    state.internalProducts = await request.get('/supplier/internal-products')
  } else {
    state.products = await request.get('/supplier/submissions')
  }
  if (state.page > pageTotal.value) state.page = pageTotal.value
  if (state.view === 'products') {
    await nextTick()
    window.setTimeout(showQuoteReminder, 0)
  }
}

function showQuoteReminder() {
  if (state.quoteReminderShown) return
  const count = state.products.filter(row => valueOf(row, 'quote_status') === 'NEED_QUOTE').length
  state.quoteReminderShown = true
  if (count > 0) {
    toast(`\u60a8\u6709${count}\u4e2a\u4ea7\u54c1\u9700\u8981\u66f4\u65b0\u62a5\u4ef7`)
  }
}

async function switchView(view) {
  state.view = view
  state.page = 1
  state.globalSearch = ''
  state.columnFilters = {}
  state.selectedOrders = []
  closeFilterMenu()
  await load()
}

async function submitProduct() {
  await request.post('/supplier/submissions', state.product)
  state.product = {}
  state.modal = ''
  await load()
  toast('产品已提交')
}

async function downloadTemplate() {
  const response = await request.get('/supplier/template', { responseType: 'blob' })
  downloadBlob(response.data, '供应商产品上传模板.xlsx')
}

function selectedCurrentRows() {
  const selected = new Set(state.selectedOrders)
  return sourceRows.value.filter(row => selected.has(orderNoOf(row)))
}

function exportSelectedRows() {
  const rows = selectedCurrentRows()
  if (!rows.length) {
    toast('\u8bf7\u5148\u52fe\u9009\u8981\u5bfc\u51fa\u7684\u6570\u636e')
    return
  }
  const title = views.find(view => view[0] === state.view)?.[1] || state.view
  exportRowsAsExcel(rows, columns.value, `${title}-选中数据.xls`)
}

function exportRowsAsExcel(rows, keys, filename) {
  const escape = value => String(formatCell(value ?? '')).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
  const header = keys.map(key => `<th>${escape(columnLabel(key))}</th>`).join('')
  const body = rows.map(row => `<tr>${keys.map(key => `<td>${escape(valueOf(row, key))}</td>`).join('')}</tr>`).join('')
  const html = `<html><head><meta charset="UTF-8"></head><body><table><tr>${header}</tr>${body}</table></body></html>`
  downloadBlob(new Blob([html], { type: 'application/vnd.ms-excel;charset=utf-8' }), filename)
}

function toggleActionMenu(menu) {
  state.actionMenu = state.actionMenu === menu ? '' : menu
}

function openImportMode(mode) {
  state.importMode = mode
  state.actionMenu = ''
  state.modal = mode === 'quote' ? 'quoteImport' : 'upload'
}

async function uploadFile() {
  if (!state.file) {
    toast('请选择 Excel 文件')
    return
  }
  try {
    const formData = new FormData()
    formData.append('file', state.file)
    const result = await request.post('/supplier/upload', formData)
    state.file = null
    state.modal = ''
    await load()
    toast(`导入完成，共 ${result.total} 条`)
  } catch (error) {
    toast(error.message)
  }
}

async function openQuoteModal(row) {
  const rows = row ? [row] : state.products.filter(item => selectedOrderSet.value.has(orderNoOf(item)))
  if (!rows.length) {
    toast('\u8bf7\u5148\u52fe\u9009\u8981\u62a5\u4ef7\u7684\u4ea7\u54c1')
    return
  }
  state.activeOrderNo = `${rows.length} ?`
  state.quoteItems = rows.map(normalizeQuoteItem)
  state.modal = 'quote'
}

function normalizeQuoteItem(item) {
  return {
    ...item,
    purchasePrice: valueOf(item, 'purchasePrice'),
    purchase_price: valueOf(item, 'purchasePrice'),
    priceValidUntil: valueOf(item, 'priceValidUntil'),
    price_valid_until: valueOf(item, 'priceValidUntil')
  }
}

async function openSelectedQuoteModal() {
  if (!state.selectedOrders.length) {
    toast('\u8bf7\u5148\u52fe\u9009\u8981\u62a5\u4ef7\u7684\u4ea7\u54c1')
    return
  }
  await openQuoteModal()
}

async function saveQuoteItems() {
  await request.put('/supplier/products/quotes/batch', state.quoteItems.map(item => ({
    code: valueOf(item, 'code'),
    purchasePrice: item.purchasePrice ?? item.purchase_price,
    priceValidUntil: item.priceValidUntil ?? item.price_valid_until
  })))
  state.modal = ''
  await load()
  toast('报价已保存')
}

async function downloadSelectedOrders() {
  if (!state.selectedOrders.length) {
    toast('请先勾选要下载的报价')
    return
  }
  const selectedKeys = new Set(state.selectedOrders)
  const downloadKeys = state.quoteOrders
    .filter(row => selectedKeys.has(orderNoOf(row)))
    .map(row => valueOf(row, 'pricingGroup') || valueOf(row, 'orderNo'))
    .filter(Boolean)
  const response = await request.post('/supplier/quote-orders/download', Array.from(new Set(downloadKeys)), { responseType: 'blob' })
  downloadBlob(response.data, '供应商报价表.xlsx')
}

async function importQuotePrices() {
  if (!state.quoteFile) {
    toast('请选择 Excel 文件')
    return
  }
  const formData = new FormData()
  formData.append('file', state.quoteFile)
  const result = await request.post('/supplier/quote-orders/import', formData)
  state.quoteFile = null
  state.modal = ''
  await load()
  toast(`导入完成，读取 ${result.total} 行，成功填价 ${result.updated} 行`)
}

function rowMatches(row) {
  const global = state.globalSearch.trim().toLowerCase()
  if (global && !columns.value.some(key => cellText(row, key).includes(global))) return false
  return columns.value.every(key => {
    const selectedValues = state.columnFilters[key]
    return !selectedValues || selectedValues.includes(displayValue(row, key))
  })
}

function cellText(row, key) {
  return String(formatCell(valueOf(row, key))).toLowerCase()
}

function valueOf(row, key) {
  return row?.[key] ?? row?.[camelToSnake(key)] ?? ''
}

function displayValue(row, key) {
  const text = String(formatCell(valueOf(row, key)))
  return text || '(空)'
}

function columnChoices(key) {
  const search = state.filterSearch.trim().toLowerCase()
  return allColumnValues(key).filter(item => !search || item.value.toLowerCase().includes(search))
}

function allColumnValues(key) {
  const counts = new Map()
  sourceRows.value.forEach(row => {
    const value = displayValue(row, key)
    counts.set(value, (counts.get(value) || 0) + 1)
  })
  return Array.from(counts.entries()).map(([value, count]) => ({ value, count }))
}

function openFilterMenu(key, event) {
  state.filterMenu = state.filterMenu === key ? '' : key
  state.filterSearch = ''
  if (state.filterMenu) {
    const rect = event.currentTarget.getBoundingClientRect()
    const width = 320
    const left = Math.min(rect.left, window.innerWidth - width - 12)
    state.filterMenuStyle = {
      left: `${Math.max(12, left)}px`,
      top: `${rect.bottom + 6}px`
    }
  }
}

function closeFilterMenu() {
  state.filterMenu = ''
  state.filterSearch = ''
  state.filterMenuStyle = {}
}

function isFilterValueChecked(key, value) {
  return !state.columnFilters[key] || state.columnFilters[key].includes(value)
}

function toggleFilterValue(key, value, checked) {
  const allValues = allColumnValues(key).map(item => item.value)
  const current = new Set(state.columnFilters[key] || allValues)
  checked ? current.add(value) : current.delete(value)
  const next = Array.from(current)
  if (next.length === allValues.length) {
    delete state.columnFilters[key]
  } else {
    state.columnFilters[key] = next
  }
  state.page = 1
}

function selectAllFilterValues(key) {
  delete state.columnFilters[key]
  state.page = 1
}

function clearAllFilterValues(key) {
  state.columnFilters[key] = []
  state.page = 1
}

function columnFilterActive(key) {
  return Boolean(state.columnFilters[key])
}

function startColumnDrag(key) {
  state.dragColumn = key
}

function dropColumn(targetKey) {
  const sourceKey = state.dragColumn
  if (!sourceKey || sourceKey === targetKey) return
  const next = [...columns.value]
  const sourceIndex = next.indexOf(sourceKey)
  const targetIndex = next.indexOf(targetKey)
  if (sourceIndex < 0 || targetIndex < 0) return
  next.splice(sourceIndex, 1)
  next.splice(targetIndex, 0, sourceKey)
  state.columnOrder[state.view] = next
  state.dragColumn = ''
}

function resetColumns() {
  state.columnOrder[state.view] = [...baseColumns.value]
  state.columnWidths[state.view] = {}
}

function tableStyleKey(scope = state.view) {
  return `table-style:${user.role || 'SUPPLIER'}:${user.username || 'anonymous'}:${scope}`
}

function saveTableStyle() {
  localStorage.setItem(tableStyleKey(), JSON.stringify({
    columnOrder: state.columnOrder[state.view] || [...columns.value],
    columnWidths: state.columnWidths[state.view] || {}
  }))
  toast('\u8868\u683c\u6837\u5f0f\u5df2\u4fdd\u5b58')
}

function loadTableStyles() {
  views.forEach(([scope]) => {
    const saved = localStorage.getItem(tableStyleKey(scope))
    if (!saved) return
    try {
      const style = JSON.parse(saved)
      if (Array.isArray(style.columnOrder)) {
        state.columnOrder[scope] = style.columnOrder
      }
      if (style.columnWidths && typeof style.columnWidths === 'object') {
        state.columnWidths[scope] = style.columnWidths
      }
    } catch (error) {
      localStorage.removeItem(tableStyleKey(scope))
    }
  })
}

function clearFilters() {
  state.globalSearch = ''
  state.columnFilters = {}
  closeFilterMenu()
  state.page = 1
}

function changePageSize() {
  state.page = 1
}

function prevPage() {
  state.page = Math.max(1, state.page - 1)
}

function nextPage() {
  state.page = Math.min(pageTotal.value, state.page + 1)
}

function toggleOrder(orderNo, checked) {
  const set = new Set(state.selectedOrders)
  checked ? set.add(orderNo) : set.delete(orderNo)
  state.selectedOrders = Array.from(set)
}

function togglePageOrders(checked) {
  const set = new Set(state.selectedOrders)
  pageOrderNos.value.forEach(orderNo => {
    checked ? set.add(orderNo) : set.delete(orderNo)
  })
  state.selectedOrders = Array.from(set)
}

function orderNoOf(row) {
  return String(row?.id || '')
}

function statusClass(row, key) {
  if (!['status', 'link_status', 'quote_status'].includes(key)) return ''
  const value = String(valueOf(row, key) || valueOf(row, 'status') || '').toUpperCase()
  if (value === 'WAIT_SUPPLIER_PRICE') return 'status-submitted'
  if (value === 'NEED_QUOTE') return 'status-submitted'
  if (value === 'QUOTED') return 'status-quote'
  if (value === 'SUPPLIER_PRICED' || value === 'APPROVED' || value === 'ACTIVE') return 'status-linked'
  if (value === 'PENDING' || value === 'WAIT_CODE' || value === 'CODE_NOT_FOUND') return 'status-wait'
  if (value === 'CANCELLED') return 'status-cancelled'
  return ''
}

async function changePassword() {
  if (!state.passwordForm.password) {
    toast('请输入新密码')
    return
  }
  if (state.passwordForm.password !== state.passwordForm.confirmPassword) {
    toast('两次输入的密码不一致')
    return
  }
  await request.put('/me/password', { newPassword: state.passwordForm.password })
  state.passwordForm = { password: '', confirmPassword: '' }
  state.modal = ''
  toast('密码已修改')
}

function headerStyle(key) {
  return columnStyle(state.columnWidths, state.view, key, dataColumnWidths.value[key])
}

function resizeHeader(key, event) {
  startColumnResize(state.columnWidths, state.view, columns.value, key, event)
}

function quoteItemHeaderStyle(key) {
  return columnStyle(state.columnWidths, 'quoteItems', key, defaultColumnWidths(quoteItemColumns)[key])
}

function resizeQuoteItemHeader(key, event) {
  startColumnResize(state.columnWidths, 'quoteItems', quoteItemColumns, key, event)
}

function camelToSnake(value) {
  return value.replace(/[A-Z]/g, letter => `_${letter.toLowerCase()}`)
}

function logout() {
  localStorage.removeItem('token')
  localStorage.removeItem('user')
  router.push('/')
}

function downloadBlob(blob, filename) {
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = filename
  link.click()
  URL.revokeObjectURL(url)
}

onMounted(async () => {
  loadTableStyles()
  await load()
})
</script>

<template>
  <main class="workspace">
    <header class="topbar">
      <div><strong>{{ user.username }}</strong><span>供应商</span></div>
      <div class="topbar-actions">
        <button @click="state.sidebarCollapsed = !state.sidebarCollapsed">
          {{ state.sidebarCollapsed ? '展开栏目' : '收起栏目' }}
        </button>
        <button @click="state.modal = 'password'">修改密码</button>
        <button @click="logout">退出</button>
      </div>
    </header>
    <p v-if="message" class="notice">{{ message }}</p>

    <section class="dashboard" :class="{ collapsed: state.sidebarCollapsed }">
      <aside v-if="!state.sidebarCollapsed" class="side">
        <button v-for="view in views" :key="view[0]" :class="{ active: state.view === view[0] }" @click="switchView(view[0])">
          <span>{{ view[1] }}</span>
        </button>
      </aside>

      <div class="content">
        <section class="panel">
          <div class="panel-title-row">
            <h2>{{ views.find(view => view[0] === state.view)?.[1] }}</h2>
            <div class="panel-actions">
              <button @click="saveTableStyle">{{ saveTableStyleText }}</button>
              <button v-if="state.view === 'internal'" @click="exportSelectedRows">导出选中数据</button>
              <div v-if="state.view === 'products'" class="menu-wrap">
                <button @click="toggleActionMenu('download')">{{ '\u4e0b\u8f7d\u8868\u683c' }}</button>
                <div v-if="state.actionMenu === 'download'" class="action-menu">
                  <button @click="downloadTemplate(); state.actionMenu = ''">{{ '\u53ea\u5bfc\u51fa\u6a21\u677f' }}</button>
                  <button @click="exportSelectedRows(); state.actionMenu = ''">{{ '\u5bfc\u51fa\u9009\u4e2d\u6570\u636e' }}</button>
                </div>
              </div>
              <div v-if="state.view === 'products'" class="menu-wrap">
                <button @click="toggleActionMenu('import')">{{ '\u5bfc\u5165\u8868\u683c' }}</button>
                <div v-if="state.actionMenu === 'import'" class="action-menu">
                  <button @click="openImportMode('create')">{{ '\u5bfc\u5165\u65b0\u589e' }}</button>
                  <button @click="openImportMode('update')">{{ '\u5bfc\u5165\u4fee\u6539' }}</button>
                  <button @click="openImportMode('quote')">{{ '\u5bfc\u5165\u62a5\u4ef7' }}</button>
                </div>
              </div>
              <button v-if="state.view === 'products'" @click="state.modal = 'product'">{{ '\u65b0\u589e\u4ea7\u54c1\u4fe1\u606f' }}</button>
              <button v-if="state.view === 'products'" class="primary" @click="openSelectedQuoteModal">{{ quoteModalTitle }}</button>
            </div>
          </div>

          <div class="table-tools">
            <input v-model="state.globalSearch" placeholder="搜索当前表" @input="state.page = 1">
            <button @click="clearFilters">清除筛选</button>
            <button @click="resetColumns">恢复列顺序</button>
          </div>

          <div class="table-wrap">
            <table>
              <colgroup>
                <col v-if="state.view === 'products' || state.view === 'internal'" class="select-col">
                <col v-for="key in columns" :key="key" :style="headerStyle(key)">
                <col v-if="state.view === 'products'" class="action-col">
              </colgroup>
              <thead>
                <tr>
                  <th v-if="state.view === 'products' || state.view === 'internal'" class="check-cell">
                    <input type="checkbox" :checked="pageOrdersChecked" @change="togglePageOrders($event.target.checked)">
                  </th>
                  <th
                    v-for="key in columns"
                    :key="key"
                    :style="headerStyle(key)"
                    draggable="true"
                    class="draggable-th"
                    @dragstart="startColumnDrag(key)"
                    @dragover.prevent
                    @drop="dropColumn(key)"
                  >
                    <div class="th-filter-head">
                      <span>{{ columnLabel(key) }}</span>
                      <button
                        type="button"
                        class="filter-trigger"
                        :class="{ active: columnFilterActive(key) }"
                        @click.stop="openFilterMenu(key, $event)"
                      >
                        ▾
                      </button>
                    </div>
                    <span class="col-resizer" @mousedown="resizeHeader(key, $event)"></span>
                    <div v-if="state.filterMenu === key" class="excel-filter-menu" :style="state.filterMenuStyle" @click.stop>
                      <input v-model="state.filterSearch" placeholder="搜索此列">
                      <div class="filter-links">
                        <button type="button" @click="selectAllFilterValues(key)">全选</button>
                        <button type="button" @click="clearAllFilterValues(key)">清空</button>
                      </div>
                      <div class="filter-values">
                        <label v-for="item in columnChoices(key)" :key="item.value">
                          <input
                            type="checkbox"
                            :checked="isFilterValueChecked(key, item.value)"
                            @change="toggleFilterValue(key, item.value, $event.target.checked)"
                          >
                          <span>{{ item.value }}</span>
                          <em>({{ item.count }})</em>
                        </label>
                      </div>
                      <div class="filter-actions">
                        <button type="button" class="primary" @click="closeFilterMenu">确定</button>
                        <button type="button" @click="closeFilterMenu">取消</button>
                      </div>
                    </div>
                  </th>
                  <th v-if="state.view === 'products'" class="action-cell">操作</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="row in pagedRows" :key="`${state.view}-${row.id}`">
                  <td v-if="state.view === 'products' || state.view === 'internal'" class="check-cell">
                    <input type="checkbox" :checked="selectedOrderSet.has(orderNoOf(row))" @change="toggleOrder(orderNoOf(row), $event.target.checked)">
                  </td>
                  <td v-for="key in columns" :key="key">
                    <span :class="statusClass(row, key)">{{ formatCell(valueOf(row, key)) }}</span>
                  </td>
                  <td v-if="state.view === 'products'" class="row-actions action-cell">
                    <button class="primary" @click="openQuoteModal(row)">报价</button>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>

          <div class="pager">
            <span>共 {{ filteredRows.length }} 条</span>
            <span v-if="state.view === 'products' || state.view === 'internal'">已选 {{ state.selectedOrders.length }} 条</span>
            <label>
              每页
              <select v-model.number="state.pageSize" @change="changePageSize">
                <option v-for="size in pageSizeOptions" :key="size" :value="size">{{ size }}</option>
              </select>
              条
            </label>
            <button :disabled="state.page <= 1" @click="prevPage">上一页</button>
            <span>{{ state.page }} / {{ pageTotal }}</span>
            <button :disabled="state.page >= pageTotal" @click="nextPage">下一页</button>
          </div>
        </section>
      </div>
    </section>

    <div v-if="state.modal === 'quote'" class="modal-mask" @click.self="state.modal = ''">
      <section class="modal">
        <header class="modal-head"><h3>{{ quoteModalTitle }}</h3><button @click="state.modal = ''">{{ closeText }}</button></header>
        <div class="table-wrap compact quote-modal">
          <table>
            <colgroup>
              <col v-for="key in quoteItemColumns" :key="key" :style="quoteItemHeaderStyle(key)">
            </colgroup>
            <thead>
              <tr>
                <th v-for="key in quoteItemColumns" :key="key" :style="quoteItemHeaderStyle(key)" class="resizable-th">
                  {{ columnLabel(key) }}
                  <span class="col-resizer" @mousedown="resizeQuoteItemHeader(key, $event)"></span>
                </th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="item in state.quoteItems" :key="item.id">
                <td>{{ valueOf(item, 'brand') }}</td>
                <td>{{ valueOf(item, 'code') }}</td>
                <td>{{ valueOf(item, 'newCode') }}</td>
                <td>{{ valueOf(item, 'craftMaterial') }}</td>
                <td>{{ valueOf(item, 'specModel') }}</td>
                <td>{{ valueOf(item, 'commonModel') }}</td>
                <td><input v-model="item.purchasePrice"></td>
                <td><input v-model="item.priceValidUntil" type="date"></td>
              </tr>
            </tbody>
          </table>
        </div>
        <div class="modal-body form-grid">
          <button class="primary" @click="saveQuoteItems">保存报价</button>
        </div>
      </section>
    </div>

    <div v-if="state.modal === 'product'" class="modal-mask" @click.self="state.modal = ''">
      <section class="modal">
        <header class="modal-head"><h3>{{ '\u65b0\u589e\u4ea7\u54c1\u4fe1\u606f' }}</h3><button @click="state.modal = ''">{{ closeText }}</button></header>
        <div class="modal-body product-form">
          <input v-for="field in supplierProductCreateFields" :key="field[0]" v-model="state.product[field[0]]" :type="fieldInputType(field[0])" :placeholder="field[1]">
          <button class="primary" @click="submitProduct">{{ '\u4fdd\u5b58' }}</button>
        </div>
      </section>
    </div>

    <div v-if="state.modal === 'upload'" class="modal-mask" @click.self="state.modal = ''">
      <section class="modal">
        <header class="modal-head"><h3>导入产品</h3><button @click="state.modal = ''">关闭</button></header>
        <div class="modal-body form-grid">
          <input type="file" accept=".xlsx" @change="state.file = $event.target.files[0]">
          <button class="primary" @click="uploadFile">上传并提交审核</button>
        </div>
      </section>
    </div>

    <div v-if="state.modal === 'quoteImport'" class="modal-mask" @click.self="state.modal = ''">
      <section class="modal">
        <header class="modal-head"><h3>导入表格填价</h3><button @click="state.modal = ''">关闭</button></header>
        <div class="modal-body form-grid">
          <input type="file" accept=".xlsx" @change="state.quoteFile = $event.target.files[0]">
          <button class="primary" @click="importQuotePrices">上传并填价</button>
        </div>
      </section>
    </div>

    <div v-if="state.modal === 'password'" class="modal-mask" @click.self="state.modal = ''">
      <section class="modal">
        <header class="modal-head"><h3>修改密码</h3><button @click="state.modal = ''">关闭</button></header>
        <div class="modal-body form-grid">
          <input v-model="state.passwordForm.password" type="password" placeholder="新密码">
          <input v-model="state.passwordForm.confirmPassword" type="password" placeholder="再次输入新密码">
          <button class="primary" @click="changePassword">确认修改</button>
        </div>
      </section>
    </div>
  </main>
</template>
