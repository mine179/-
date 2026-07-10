<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import request from '../request'
import { productFields } from '../data/productFields'
import { columnLabel, formatCell } from '../data/columnLabels'
import { columnStyle, defaultColumnWidths, startColumnResize } from '../utils/columnResize'

const router = useRouter()
const user = JSON.parse(localStorage.getItem('user') || '{}')
const message = ref('')

const views = [
  ['products', '供应商产品表'],
  ['quotes', '供应商报价表']
]

const supplierProductFields = productFields.filter(([key]) => !['code', 'newCode', 'salePrice'].includes(key))
const productColumns = ['id', 'status', ...supplierProductFields.map(([key]) => key), 'updatedAt']
const quoteColumns = ['id', 'orderNo', 'customerUsername', 'status', 'updatedAt']
const quoteItemColumns = ['code', 'specModel', 'purchasePrice', 'status']

const state = reactive({
  view: 'products',
  product: {},
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
  pageSize: 15,
  globalSearch: '',
  columnFilters: {},
  filterMenu: '',
  filterSearch: '',
  filterMenuStyle: {},
  columnOrder: {},
  columnWidths: {},
  dragColumn: ''
})

const sourceRows = computed(() => state.view === 'quotes' ? state.quoteOrders : state.products)
const baseColumns = computed(() => state.view === 'quotes' ? quoteColumns : productColumns)
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
const pageOrderNos = computed(() => state.view === 'quotes' ? pagedRows.value.map(orderNoOf).filter(Boolean) : [])
const pageOrdersChecked = computed(() => pageOrderNos.value.length > 0 && pageOrderNos.value.every(orderNo => selectedOrderSet.value.has(orderNo)))
const dataColumnWidths = computed(() => defaultColumnWidths(columns.value))

function toast(text) {
  message.value = text
  setTimeout(() => { if (message.value === text) message.value = '' }, 3000)
}

async function load() {
  state.products = await request.get('/supplier/submissions')
  state.quoteOrders = await request.get('/supplier/quote-orders')
  if (state.page > pageTotal.value) state.page = pageTotal.value
}

async function switchView(view) {
  state.view = view
  state.page = 1
  state.globalSearch = ''
  state.columnFilters = {}
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
  const orderNo = orderNoOf(row)
  state.activeOrderNo = orderNo
  state.quoteItems = await request.get(`/supplier/quote-orders/${orderNo}/items`)
  state.modal = 'quote'
}

function openQuoteDetail(row) {
  router.push({ name: 'supplier-quote-detail', params: { orderNo: orderNoOf(row) } })
}

async function openSelectedQuoteModal() {
  if (!state.selectedOrders.length) {
    toast('请先勾选要报价的订单')
    return
  }
  if (state.selectedOrders.length > 1) {
    toast('一次只能选择一个订单报价')
    return
  }
  await openQuoteModal({ orderNo: state.selectedOrders[0] })
}

async function saveQuoteItems() {
  await request.put('/supplier/quotes/batch', state.quoteItems)
  state.modal = ''
  await load()
  toast('报价已保存')
}

async function downloadSelectedOrders() {
  if (!state.selectedOrders.length) {
    toast('请先勾选要下载的订单')
    return
  }
  const response = await request.post('/supplier/quote-orders/download', state.selectedOrders, { responseType: 'blob' })
  downloadBlob(response.data, '供应商报价订单.xlsx')
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
  return row?.order_no || row?.orderNo || ''
}

function statusClass(row, key) {
  if (key !== 'status') return ''
  const value = String(valueOf(row, 'status') || '').toUpperCase()
  if (value === 'WAIT_SUPPLIER_PRICE') return 'status-submitted'
  if (value === 'SUPPLIER_PRICED' || value === 'APPROVED' || value === 'ACTIVE') return 'status-linked'
  if (value === 'PENDING' || value === 'WAIT_CODE') return 'status-wait'
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
  localStorage.clear()
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

onMounted(load)
</script>

<template>
  <main class="workspace">
    <header class="topbar">
      <div><strong>{{ user.username }}</strong><span>供应商</span></div>
      <div class="topbar-actions">
        <button @click="state.modal = 'password'">修改密码</button>
        <button @click="logout">退出</button>
      </div>
    </header>
    <p v-if="message" class="notice">{{ message }}</p>

    <section class="dashboard">
      <aside class="side">
        <button v-for="view in views" :key="view[0]" :class="{ active: state.view === view[0] }" @click="switchView(view[0])">
          <span>{{ view[1] }}</span>
        </button>
      </aside>

      <div class="content">
        <section class="panel">
          <div class="panel-title-row">
            <h2>{{ views.find(view => view[0] === state.view)?.[1] }}</h2>
            <div class="panel-actions">
              <button v-if="state.view === 'products'" @click="downloadTemplate">下载模板</button>
              <button v-if="state.view === 'products'" @click="state.modal = 'upload'">导入 Excel</button>
              <button v-if="state.view === 'products'" @click="state.modal = 'product'">提交产品</button>
              <button v-if="state.view === 'quotes'" class="primary" @click="downloadSelectedOrders">下载订单</button>
              <button v-if="state.view === 'quotes'" class="primary" @click="state.modal = 'quoteImport'">导入表格填价</button>
              <button v-if="state.view === 'quotes'" class="primary" @click="openSelectedQuoteModal">报价</button>
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
                <col v-if="state.view === 'quotes'" class="select-col">
                <col v-for="key in columns" :key="key" :style="headerStyle(key)">
                <col v-if="state.view === 'quotes'" class="action-col">
              </colgroup>
              <thead>
                <tr>
                  <th v-if="state.view === 'quotes'" class="check-cell">
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
                  <th v-if="state.view === 'quotes'" class="action-cell">操作</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="row in pagedRows" :key="`${state.view}-${row.id}`">
                  <td v-if="state.view === 'quotes'" class="check-cell">
                    <input type="checkbox" :checked="selectedOrderSet.has(orderNoOf(row))" @change="toggleOrder(orderNoOf(row), $event.target.checked)">
                  </td>
                  <td v-for="key in columns" :key="key">
                    <span :class="statusClass(row, key)">{{ formatCell(valueOf(row, key)) }}</span>
                  </td>
                  <td v-if="state.view === 'quotes'" class="row-actions action-cell">
                    <button @click="openQuoteDetail(row)">查看产品</button>
                    <button class="primary" @click="openQuoteModal(row)">报价</button>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>

          <div class="pager">
            <span>共 {{ filteredRows.length }} 条</span>
            <span v-if="state.view === 'quotes'">已选 {{ state.selectedOrders.length }} 单</span>
            <label>
              每页
              <select v-model.number="state.pageSize" @change="changePageSize">
                <option :value="15">15</option>
                <option :value="30">30</option>
                <option :value="50">50</option>
                <option :value="100">100</option>
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
        <header class="modal-head"><h3>订单报价 {{ state.activeOrderNo }}</h3><button @click="state.modal = ''">关闭</button></header>
        <div class="table-wrap compact">
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
                <td>{{ item.code }}</td>
                <td>{{ item.specModel }}</td>
                <td><input v-model="item.purchasePrice"></td>
                <td><span :class="statusClass(item, 'status')">{{ formatCell(item.status) }}</span></td>
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
        <header class="modal-head"><h3>提交产品</h3><button @click="state.modal = ''">关闭</button></header>
        <div class="modal-body product-form">
          <input v-for="field in supplierProductFields" :key="field[0]" v-model="state.product[field[0]]" :placeholder="field[1]">
          <button class="primary" @click="submitProduct">提交审核</button>
        </div>
      </section>
    </div>

    <div v-if="state.modal === 'upload'" class="modal-mask" @click.self="state.modal = ''">
      <section class="modal">
        <header class="modal-head"><h3>导入产品</h3><button @click="state.modal = ''">关闭</button></header>
        <div class="modal-body form-grid">
          <button @click="downloadTemplate">下载 Excel 模板</button>
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
