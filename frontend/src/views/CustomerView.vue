<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import request from '../request'
import { columnLabel, formatCell } from '../data/columnLabels'
import { productFields } from '../data/productFields'
import { columnStyle, defaultColumnWidths, startColumnResize } from '../utils/columnResize'

const router = useRouter()
const user = JSON.parse(localStorage.getItem('user') || '{}')
const message = ref('')

const hiddenColumns = ['serial_no', 'serialNo', 'code', 'newCode', 'new_code', 'purchase_price', 'purchasePrice']
const internalHiddenColumns = [
  ...hiddenColumns,
  'master_product_id',
  'masterProductId',
  'sale_price',
  'salePrice',
  'created_at',
  'createdAt',
  'updated_at',
  'updatedAt'
]
const customerProductFields = productFields.filter(([key]) => !['code', 'newCode', 'purchasePrice'].includes(key))
const productColumnKeys = productFields.map(([key]) => camelToSnake(key))
const customerProductColumnKeys = customerProductFields.map(([key]) => camelToSnake(key))

const views = [
  ['internal', '主表'],
  ['orders', '订单表'],
  ['products', '客户产品表']
]

const tableColumns = {
  orders: ['id', 'order_no', 'customer_username', 'status', 'created_at', 'updated_at'],
  products: ['id', 'status', ...customerProductColumnKeys, 'customer_username', 'created_at', 'updated_at'],
  internal: ['id', ...productColumnKeys, 'created_at', 'updated_at']
}

const state = reactive({
  view: 'orders',
  orders: [],
  products: [],
  internalProducts: [],
  selectedInternal: [],
  file: null,
  product: {},
  passwordForm: { password: '', confirmPassword: '' },
  modal: '',
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

const sourceRows = computed(() => {
  if (state.view === 'orders') return state.orders
  if (state.view === 'products') return state.products
  return state.internalProducts
})
const baseColumns = computed(() => {
  const hidden = state.view === 'internal' ? internalHiddenColumns : hiddenColumns
  return (tableColumns[state.view] || []).filter(key => !hidden.includes(key))
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
const selectedInternalSet = computed(() => new Set(state.selectedInternal))
const pageInternalIds = computed(() => state.view === 'internal' ? pagedRows.value.map(row => row.id).filter(Boolean) : [])
const pageInternalChecked = computed(() => pageInternalIds.value.length > 0 && pageInternalIds.value.every(id => selectedInternalSet.value.has(id)))
const dataColumnWidths = computed(() => defaultColumnWidths(columns.value))

function toast(text) {
  message.value = text
  setTimeout(() => { if (message.value === text) message.value = '' }, 3000)
}

async function loadCurrent() {
  if (state.view === 'orders') state.orders = await request.get('/customer/orders')
  if (state.view === 'products') state.products = await request.get('/customer/products')
  if (state.view === 'internal') state.internalProducts = await request.get('/customer/internal-products')
  if (state.page > pageTotal.value) state.page = pageTotal.value
}

async function switchView(view) {
  state.view = view
  state.page = 1
  state.globalSearch = ''
  state.columnFilters = {}
  closeFilterMenu()
  await loadCurrent()
}

async function downloadTemplate() {
  const response = await request.get('/customer/template', { responseType: 'blob' })
  downloadBlob(response.data, '客户下单模板.xlsx')
}

async function uploadFile() {
  if (!state.file) {
    toast('请选择 Excel 文件')
    return
  }
  try {
    const formData = new FormData()
    formData.append('file', state.file)
    const result = await request.post('/customer/upload', formData)
    state.file = null
    state.modal = ''
    await switchView('orders')
    toast(`上传完成：${result.orderNo}，共 ${result.total} 条，未匹配 ${result.unmatched} 条`)
  } catch (error) {
    toast(error.message)
  }
}

async function addOrder() {
  const result = await request.post('/customer/orders', state.product)
  state.product = {}
  state.modal = ''
  await switchView('orders')
  toast(`订单已新增：${result.orderNo}`)
}

async function orderFromInternal() {
  if (!state.selectedInternal.length) {
    toast('请先勾选主表产品')
    return
  }
  const result = await request.post('/customer/orders/from-internal', state.selectedInternal)
  state.selectedInternal = []
  await switchView('orders')
  toast(`下单完成：${result.orderNo}，共 ${result.total} 条`)
}

function toggleInternal(id, checked) {
  const set = new Set(state.selectedInternal)
  checked ? set.add(id) : set.delete(id)
  state.selectedInternal = Array.from(set)
}

function togglePageInternal(checked) {
  const set = new Set(state.selectedInternal)
  pageInternalIds.value.forEach(id => {
    checked ? set.add(id) : set.delete(id)
  })
  state.selectedInternal = Array.from(set)
}

function openOrderDetail(row) {
  router.push({ name: 'customer-order-detail', params: { orderNo: valueOf(row, 'order_no') } })
}

function orderStatusOf(row) {
  return String(valueOf(row, 'status') || '').toUpperCase()
}

function orderCanCancel(row) {
  const status = orderStatusOf(row)
  return status !== 'QUOTE_GENERATED' && status !== 'CANCELLED' && status !== 'COMPLETED'
}

async function cancelOrder(row) {
  if (!orderCanCancel(row)) {
    toast('已生成报价任务的订单不能作废')
    return
  }
  if (!window.confirm('确定作废整个订单吗？订单里的产品也会一起作废。')) return
  await request.put(`/customer/orders/${valueOf(row, 'order_no')}/cancel`)
  await loadCurrent()
  toast('订单已作废')
}

function openProductEdit(row) {
  state.product = { ...row }
  state.modal = 'editProduct'
}

async function saveProductEdit() {
  await request.put(`/customer/products/${state.product.id}`, state.product)
  state.product = {}
  state.modal = ''
  await loadCurrent()
  toast('客户产品已保存')
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
  return row?.[key] ?? row?.[snakeToCamel(key)] ?? ''
}

function displayValue(row, key) {
  const text = String(formatCell(valueOf(row, key)))
  return text || '(空)'
}

function statusClass(row, key) {
  if (key !== 'status') return ''
  const status = orderStatusOf(row)
  if (status === 'CANCELLED') return 'status-cancelled'
  if (status === 'COMPLETED') return 'status-completed'
  if (status === 'SUBMITTED') return 'status-submitted'
  if (status === 'QUOTE_GENERATED') return 'status-quote'
  if (status === 'PENDING' || status === 'WAIT_CODE') return 'status-wait'
  if (status === 'APPROVED' || status === 'ACTIVE') return 'status-linked'
  return ''
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

function headerStyle(key) {
  return columnStyle(state.columnWidths, state.view, key, dataColumnWidths.value[key])
}

function resizeHeader(key, event) {
  startColumnResize(state.columnWidths, state.view, columns.value, key, event)
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

function downloadBlob(blob, filename) {
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = filename
  link.click()
  URL.revokeObjectURL(url)
}

function camelToSnake(value) {
  return value.replace(/[A-Z]/g, letter => `_${letter.toLowerCase()}`)
}

function snakeToCamel(value) {
  return value.replace(/_([a-z])/g, (_, letter) => letter.toUpperCase())
}

function logout() {
  localStorage.clear()
  router.push('/')
}

onMounted(async () => {
  await loadCurrent()
})
</script>

<template>
  <main class="workspace">
    <header class="topbar">
      <div><strong>{{ user.username }}</strong><span>客户</span></div>
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
              <button v-if="state.view === 'orders'" @click="state.modal = 'product'">新增订单</button>
              <button v-if="state.view === 'orders'" @click="state.modal = 'upload'">上传订单</button>
              <button v-if="state.view === 'internal'" class="primary" @click="orderFromInternal">勾选下单</button>
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
                <col v-if="state.view === 'internal'" class="select-col">
                <col v-for="key in columns" :key="key" :style="headerStyle(key)">
                <col v-if="state.view !== 'internal'" class="action-col">
              </colgroup>
              <thead>
                <tr>
                  <th v-if="state.view === 'internal'" class="check-cell">
                    <input type="checkbox" :checked="pageInternalChecked" @change="togglePageInternal($event.target.checked)">
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
                  <th v-if="state.view !== 'internal'" class="action-cell">操作</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="row in pagedRows" :key="row.id">
                  <td v-if="state.view === 'internal'" class="check-cell">
                    <input type="checkbox" :checked="selectedInternalSet.has(row.id)" @change="toggleInternal(row.id, $event.target.checked)">
                  </td>
                  <td v-for="key in columns" :key="key">
                    <span :class="statusClass(row, key)">{{ formatCell(valueOf(row, key)) }}</span>
                  </td>
                  <td v-if="state.view !== 'internal'" class="row-actions action-cell">
                    <button v-if="state.view === 'orders'" @click="openOrderDetail(row)">查看产品</button>
                    <button v-if="state.view === 'orders'" class="danger" :disabled="!orderCanCancel(row)" @click="cancelOrder(row)">整单作废</button>
                    <button v-if="state.view === 'products'" @click="openProductEdit(row)">修改名称</button>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>

          <div class="pager">
            <span>共 {{ filteredRows.length }} 条</span>
            <span v-if="state.view === 'internal'">已选 {{ state.selectedInternal.length }} 条</span>
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

    <div v-if="state.modal === 'product'" class="modal-mask" @click.self="state.modal = ''">
      <section class="modal">
        <header class="modal-head"><h3>新增订单</h3><button @click="state.modal = ''">关闭</button></header>
        <div class="modal-body product-form">
          <input v-for="field in customerProductFields" :key="field[0]" v-model="state.product[field[0]]" :placeholder="field[1]">
          <button class="primary" @click="addOrder">提交订单</button>
        </div>
      </section>
    </div>

    <div v-if="state.modal === 'upload'" class="modal-mask" @click.self="state.modal = ''">
      <section class="modal">
        <header class="modal-head"><h3>上传订单</h3><button @click="state.modal = ''">关闭</button></header>
        <div class="modal-body form-grid">
          <button @click="downloadTemplate">下载 Excel 模板</button>
          <input type="file" accept=".xlsx" @change="state.file = $event.target.files[0]">
          <button class="primary" @click="uploadFile">上传并生成订单</button>
        </div>
      </section>
    </div>

    <div v-if="state.modal === 'editProduct'" class="modal-mask" @click.self="state.modal = ''">
      <section class="modal">
        <header class="modal-head"><h3>修改客户产品名称</h3><button @click="state.modal = ''">关闭</button></header>
        <div class="modal-body product-form">
          <input v-for="field in customerProductFields" :key="field[0]" v-model="state.product[field[0]]" :placeholder="field[1]">
          <button class="primary" @click="saveProductEdit">保存</button>
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
