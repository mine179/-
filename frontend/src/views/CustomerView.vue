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
const saveTableStyleText = '\u4fdd\u5b58\u8868\u683c\u6837\u5f0f'

const hiddenColumns = ['serial_no', 'serialNo', 'purchase_price', 'purchasePrice', 'customer_username', 'customerUsername']
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
const customerProductFields = productFields.filter(([key]) => !['purchasePrice'].includes(key))
const customerOrderFields = [...customerProductFields, ['orderRemark', '备注']]
const productColumnKeys = productFields.map(([key]) => camelToSnake(key))
const customerProductColumnKeys = customerProductFields.map(([key]) => camelToSnake(key))

const views = [
  ['internal', '主表'],
  ['orders', '订单表'],
  ['products', '客户产品表']
]

const navTree = [
  { view: 'internal' },
  { view: 'orders', children: ['products'] }
]

const tableColumns = {
  orders: ['id', 'order_no', 'customer_username', 'created_at', 'status', 'material_link_status', 'order_remark', ...productColumnKeys, 'updated_at'],
  products: ['id', 'status', 'material_link_status', ...customerProductColumnKeys, 'customer_username', 'updated_at'],
  internal: ['id', ...productColumnKeys, 'updated_at']
}

const state = reactive({
  view: 'orders',
  orders: [],
  products: [],
  internalProducts: [],
  selectedInternal: [],
  file: null,
  product: {},
  orderRemark: { id: null, orderRemark: '' },
  orderLink: { id: null, code: '', newCode: '' },
  internalOrderRemark: '',
  passwordForm: { password: '', confirmPassword: '' },
  modal: '',
  page: 1,
  pageSize: 1000,
  globalSearch: '',
  columnFilters: {},
  filterMenu: '',
  filterSearch: '',
  filterMenuStyle: {},
  columnOrder: {},
  columnWidths: {},
  dragColumn: '',
  sidebarCollapsed: false,
  navOpen: { orders: false }
})

const pageSizeOptions = [1000, 2000, 3000, 5000]

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
const activeOrderCount = computed(() => countActiveOrderNos(state.orders))

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
    toast(`导入成功，共有 ${result.changed ?? result.total ?? 0} 条信息进行改动，订单编号 ${result.orderNo}，未匹配 ${result.unmatched} 条`)
  } catch (error) {
    toast(error.message)
  }
}

async function addOrder() {
  const result = await request.post('/customer/orders', state.product)
  state.product = {}
  state.modal = ''
  await switchView('orders')
  toast(`\u8ba2\u5355\u5df2\u65b0\u589e\uff1a${result.orderNo}`)
}


function openInternalOrderModal() {
  if (!state.selectedInternal.length) {
    toast('\u8bf7\u5148\u52fe\u9009\u4e3b\u8868\u4ea7\u54c1')
    return
  }
  state.internalOrderRemark = ''
  state.modal = 'internalOrder'
}

async function orderFromInternal() {
  const result = await request.post('/customer/orders/from-internal', {
    ids: state.selectedInternal,
    orderRemark: state.internalOrderRemark
  })
  state.selectedInternal = []
  state.internalOrderRemark = ''
  state.modal = ''
  await switchView('orders')
  toast(`\u4e0b\u5355\u5b8c\u6210\uff1a${result.orderNo}\uff0c\u5171 ${result.total} \u6761\u4ea7\u54c1`)
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

function exportSelectedInternalRows() {
  const selected = new Set(state.selectedInternal)
  const rows = state.internalProducts.filter(row => selected.has(row.id))
  if (!rows.length) {
    toast('请先勾选要导出的主表产品')
    return
  }
  exportRowsAsExcel(rows, columns.value, '主表-选中数据.xls')
}

function exportRowsAsExcel(rows, keys, filename) {
  const escape = value => String(formatCell(value ?? '')).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
  const header = keys.map(key => `<th>${escape(columnLabel(key))}</th>`).join('')
  const body = rows.map(row => `<tr>${keys.map(key => `<td>${escape(valueOf(row, key))}</td>`).join('')}</tr>`).join('')
  const html = `<html><head><meta charset="UTF-8"></head><body><table><tr>${header}</tr>${body}</table></body></html>`
  downloadBlob(new Blob([html], { type: 'application/vnd.ms-excel;charset=utf-8' }), filename)
}

function openOrderDetail(row) {
  router.push({ name: 'customer-order-detail', params: { orderNo: valueOf(row, 'order_no') } })
}

function orderStatusOf(row) {
  return String(valueOf(row, 'status') || '').toUpperCase()
}

function orderCanCancel(row) {
  const status = orderStatusOf(row)
  return status !== 'QUOTE_GENERATED' && status !== 'CANCELLED' && status !== 'COMPLETED' && status !== 'QUOTE_COMPLETED'
}

async function cancelOrderItem(row) {
  if (!orderCanCancel(row)) {
    toast('\u5f53\u524d\u4ea7\u54c1\u4e0d\u80fd\u4f5c\u5e9f')
    return
  }
  if (!window.confirm('\u786e\u5b9a\u4f5c\u5e9f\u8fd9\u4e2a\u4ea7\u54c1\u5417\uff1f')) return
  await request.put(`/customer/order-items/${row.id}/cancel`)
  await loadCurrent()
  toast('\u4ea7\u54c1\u5df2\u4f5c\u5e9f')
}

function openOrderRemark(row) {
  state.orderRemark = {
    id: row.id,
    orderRemark: valueOf(row, 'order_remark') || valueOf(row, 'orderRemark') || ''
  }
  state.modal = 'orderRemark'
}

async function saveOrderRemark() {
  await request.put(`/customer/order-items/${state.orderRemark.id}/remark`, {
    orderRemark: state.orderRemark.orderRemark || ''
  })
  state.orderRemark = { id: null, orderRemark: '' }
  state.modal = ''
  await loadCurrent()
  toast('备注已修改')
}

function openOrderLink(row) {
  state.orderLink = {
    id: row.id,
    code: valueOf(row, 'code') || '',
    newCode: valueOf(row, 'new_code') || valueOf(row, 'newCode') || ''
  }
  state.modal = 'orderLink'
}

async function saveOrderLink() {
  if (!state.orderLink.code) {
    toast('请填写物料编码')
    return
  }
  await request.put(`/customer/order-items/${state.orderLink.id}/link-code`, {
    code: state.orderLink.code,
    newCode: state.orderLink.newCode
  })
  state.orderLink = { id: null, code: '', newCode: '' }
  state.modal = ''
  await loadCurrent()
  toast('物料编码已链接')
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

function orderNoOf(row) {
  return valueOf(row, 'order_no') || valueOf(row, 'orderNo')
}

function displayOrderCell(row, key, rowIndex) {
  if (state.view === 'orders' && ['order_no', 'customer_username', 'created_at'].includes(key)) {
    const previous = pagedRows.value[rowIndex - 1]
    if (previous && orderNoOf(previous) === orderNoOf(row)) {
      return ''
    }
  }
  return formatCell(valueOf(row, key))
}

function statusClass(row, key) {
  if (!['status', 'material_link_status'].includes(key)) return ''
  const status = key === 'material_link_status' ? String(valueOf(row, key) || '').toUpperCase() : orderStatusOf(row)
  if (status === 'UNLINKED') return 'status-wait'
  if (status === 'LINKED') return 'status-linked'
  if (status === 'CANCELLED') return 'status-cancelled'
  if (status === 'QUOTE_COMPLETED' || status === 'COMPLETED') return 'status-completed'
  if (status === 'SUBMITTED' || status === 'SUBMITTED_ORDER') return 'status-submitted'
  if (status === 'QUOTE_GENERATED') return 'status-linked'
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

function tableStyleKey(scope = state.view) {
  return `table-style:${user.role || 'CUSTOMER'}:${user.username || 'anonymous'}:${scope}`
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

function viewLabel(view) {
  return views.find(item => item[0] === view)?.[1] || view
}

function navNodeActive(node) {
  return state.view === node.view || Boolean(node.children?.includes(state.view))
}

function toggleNavGroup(view) {
  state.navOpen[view] = !state.navOpen[view]
}

function tabLinkCount(view) {
  return view === 'orders' ? activeOrderCount.value : 0
}

function countActiveOrderNos(rows) {
  const set = new Set()
  rows.forEach(row => {
    const status = String(valueOf(row, 'status') || '').toUpperCase()
    const orderNo = orderNoOf(row)
    if (orderNo && status !== 'COMPLETED' && status !== 'CANCELLED') {
      set.add(orderNo)
    }
  })
  return set.size
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
  localStorage.removeItem('token')
  localStorage.removeItem('user')
  router.push('/')
}

onMounted(async () => {
  loadTableStyles()
  await loadCurrent()
})
</script>

<template>
  <main class="workspace">
    <header class="topbar">
      <div><strong>{{ user.username }}</strong><span>客户</span></div>
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
        <div v-for="node in navTree" :key="node.view" class="tree-group">
          <div class="tree-row">
            <button class="tree-main" :class="{ active: state.view === node.view, 'child-active': navNodeActive(node) && state.view !== node.view }" @click="switchView(node.view)">
              <span>{{ viewLabel(node.view) }}</span>
              <em v-if="tabLinkCount(node.view)">{{ tabLinkCount(node.view) }}</em>
            </button>
            <button v-if="node.children?.length" class="tree-toggle" @click.stop="toggleNavGroup(node.view)">
              {{ state.navOpen[node.view] ? '▾' : '▸' }}
            </button>
          </div>
          <div v-if="node.children?.length && state.navOpen[node.view]" class="tree-children">
            <button v-for="child in node.children" :key="child" class="tree-child" :class="{ active: state.view === child }" @click="switchView(child)">
              <span>{{ viewLabel(child) }}</span>
              <em v-if="tabLinkCount(child)">{{ tabLinkCount(child) }}</em>
            </button>
          </div>
        </div>
      </aside>

      <div class="content">
        <section class="panel">
          <div class="panel-title-row">
            <h2>{{ viewLabel(state.view) }}</h2>
            <div class="panel-actions">
              <button @click="saveTableStyle">{{ saveTableStyleText }}</button>
              <button v-if="state.view === 'orders'" @click="state.modal = 'product'">新增订单</button>
              <button v-if="state.view === 'orders'" @click="state.modal = 'upload'">上传订单</button>
              <button v-if="state.view === 'internal'" @click="exportSelectedInternalRows">导出选中数据</button>
              <button v-if="state.view === 'internal'" class="primary" @click="openInternalOrderModal">勾选下单</button>
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
                <tr v-for="(row, rowIndex) in pagedRows" :key="row.id">
                  <td v-if="state.view === 'internal'" class="check-cell">
                    <input type="checkbox" :checked="selectedInternalSet.has(row.id)" @change="toggleInternal(row.id, $event.target.checked)">
                  </td>
                  <td v-for="key in columns" :key="key">
                    <span :class="statusClass(row, key)">{{ displayOrderCell(row, key, rowIndex) }}</span>
                  </td>
                  <td v-if="state.view !== 'internal'" class="row-actions action-cell">
                    <button v-if="state.view === 'orders'" @click="openOrderRemark(row)">修改备注</button>
                    <button v-if="state.view === 'orders'" @click="openOrderLink(row)">链接物料编码</button>
                    <button v-if="state.view === 'orders'" class="danger" :disabled="!orderCanCancel(row)" @click="cancelOrderItem(row)">产品作废</button>
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

    <div v-if="state.modal === 'product'" class="modal-mask" @click.self="state.modal = ''">
      <section class="modal">
        <header class="modal-head"><h3>新增订单</h3><button @click="state.modal = ''">关闭</button></header>
        <div class="modal-body product-form">
          <input v-for="field in customerOrderFields" :key="field[0]" v-model="state.product[field[0]]" :placeholder="field[1]">
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

    <div v-if="state.modal === 'internalOrder'" class="modal-mask" @click.self="state.modal = ''">
      <section class="modal">
        <header class="modal-head"><h3>主表下单</h3><button @click="state.modal = ''">关闭</button></header>
        <div class="modal-body form-grid">
          <textarea v-model="state.internalOrderRemark" placeholder="备注"></textarea>
          <button class="primary" @click="orderFromInternal">确认下单</button>
        </div>
      </section>
    </div>

    <div v-if="state.modal === 'orderRemark'" class="modal-mask" @click.self="state.modal = ''">
      <section class="modal">
        <header class="modal-head"><h3>修改备注</h3><button @click="state.modal = ''">关闭</button></header>
        <div class="modal-body form-grid">
          <textarea v-model="state.orderRemark.orderRemark" placeholder="备注"></textarea>
          <button class="primary" @click="saveOrderRemark">保存备注</button>
        </div>
      </section>
    </div>

    <div v-if="state.modal === 'orderLink'" class="modal-mask" @click.self="state.modal = ''">
      <section class="modal">
        <header class="modal-head"><h3>链接物料编码</h3><button @click="state.modal = ''">关闭</button></header>
        <div class="modal-body form-grid">
          <input v-model="state.orderLink.code" placeholder="物料编码">
          <input v-model="state.orderLink.newCode" placeholder="新编码">
          <button class="primary" @click="saveOrderLink">确认链接</button>
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
