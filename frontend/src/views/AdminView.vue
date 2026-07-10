<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import request from '../request'
import { productFields } from '../data/productFields'
import { columnLabel, formatCell } from '../data/columnLabels'
import { columnStyle, defaultColumnWidths, startColumnResize } from '../utils/columnResize'

const router = useRouter()
const route = useRoute()
const user = JSON.parse(localStorage.getItem('user') || '{}')
const message = ref('')

const tabs = [
  ['internal', '主表'],
  ['priceTrend', '价格趋势表'],
  ['supplier', '供应商产品表'],
  ['orders', '客户订单表'],
  ['customerProducts', '客户产品表'],
  ['quotes', '供应商报价表'],
  ['pricingAudit', '定价审核表'],
  ['users', '账号管理']
]

const protectedCodeFields = ['code', 'newCode']
const supplierProductFields = productFields.filter(([key]) => key !== 'salePrice' && !protectedCodeFields.includes(key))
const customerProductFields = productFields.filter(([key]) => key !== 'purchasePrice' && !protectedCodeFields.includes(key))
const priceTrendManualFields = [
  ['manual_price_1', '参考价格1'],
  ['manual_price_2', '参考价格2'],
  ['manual_price_3', '参考价格3'],
  ['manual_price_4', '参考价格4'],
  ['manual_price_5', '参考价格5']
]
const priceTrendOrderFields = [
  ['order_price_1', '最近订单价格1'],
  ['order_price_2', '最近订单价格2'],
  ['order_price_3', '最近订单价格3'],
  ['order_price_4', '最近订单价格4'],
  ['order_price_5', '最近订单价格5']
]

const extraProductFields = {
  internal: [],
  supplier: [
    ['status', '状态'],
    ['supplierUsername', '供应商账号'],
  ],
  customerProducts: [
    ['status', '状态'],
    ['customerUsername', '客户账号']
  ],
  unmatched: [
    ['orderNo', '订单编号'],
    ['customerUsername', '客户账号'],
    ['status', '状态']
  ]
}

const nonProductFields = {
  orders: [
    ['orderNo', '订单编号'],
    ['customerUsername', '客户账号'],
    ['status', '状态']
  ],
  quotes: [
    ['orderNo', '订单编号'],
    ['customerItemId', '客户明细ID'],
    ['supplierUsername', '供应商账号'],
    ['customerUsername', '客户账号'],
    ['code', '编码'],
    ['specModel', '规格型号'],
    ['purchasePrice', '供应价'],
    ['salePrice', '销售价'],
    ['status', '状态']
  ]
}

const productColumnKeys = productFields.map(([key]) => camelToSnake(key))
const supplierProductColumnKeys = supplierProductFields.map(([key]) => camelToSnake(key))
const customerProductColumnKeys = customerProductFields.map(([key]) => camelToSnake(key))
const tableColumns = {
  internal: ['id', ...productColumnKeys, 'created_at', 'updated_at'],
  priceTrend: ['brand', 'code', 'craft_material', 'spec_model', ...priceTrendManualFields.map(([key]) => key), ...priceTrendOrderFields.map(([key]) => key)],
  supplier: ['id', 'status', 'code', 'new_code', ...supplierProductColumnKeys, 'supplier_username', 'created_at', 'updated_at'],
  orders: ['id', 'order_no', 'customer_username', 'status', 'created_at', 'updated_at'],
  customerProducts: ['id', 'status', 'code', 'new_code', ...customerProductColumnKeys, 'customer_username', 'created_at', 'updated_at'],
  quotes: ['id', 'order_no', 'customer_username', 'supplier_username', 'status', 'updated_at'],
  pricingAudit: [
    'id', 'order_no', 'customer_item_id', 'customer_username', 'supplier_username',
    'series', 'brand', 'code', 'new_code', 'color', 'category', 'craft_material',
    'spec_model', 'common_model', 'size_value', 'resolution', 'model_remark',
    'purchase_price', 'sale_price', 'pricing_status', 'created_at', 'updated_at'
  ],
  users: ['id', 'username', 'role', 'enabled', 'created_at', 'updated_at']
}

const state = reactive({
  tab: tabs.some(tab => tab[0] === route.query.tab) ? route.query.tab : 'internal',
  rows: [],
  users: [],
  passwordForm: { password: '', confirmPassword: '' },
  modal: '',
  product: {},
  account: { username: '', password: '123456', role: 'SUPPLIER', enabled: true },
  accountEdit: {},
  approve: { id: null, code: '', newCode: '' },
  editRow: {},
  pricingQuote: {},
  activeQuoteTitle: '',
  quoteItems: [],
  importFile: null,
  selectedOrders: [],
  selectedRows: [],
  page: 1,
  pageSize: 15,
  globalSearch: '',
  columnFilters: {},
  filterMenu: '',
  filterSearch: '',
  filterMenuStyle: {},
  columnOrder: {},
  columnWidths: {},
  dragColumn: '',
  linkCounts: { supplier: 0, orders: 0, customerProducts: 0, quotes: 0, pricingAudit: 0 }
})

const hiddenColumns = ['source_type', 'sourceType', 'serial_no', 'serialNo']
const activeRows = computed(() => {
  if (state.tab === 'users') return state.users
  if (state.tab === 'quotes') return groupedQuoteRows(state.rows)
  return state.rows
})
const baseColumns = computed(() => (tableColumns[state.tab] || []).filter(key => !hiddenColumns.includes(key)))
const columns = computed(() => {
  const saved = state.columnOrder[state.tab] || []
  const known = saved.filter(key => baseColumns.value.includes(key))
  const missing = baseColumns.value.filter(key => !known.includes(key))
  return [...known, ...missing]
})
const filteredRows = computed(() => activeRows.value.filter(row => rowMatches(row)))
const pageTotal = computed(() => Math.max(1, Math.ceil(filteredRows.value.length / state.pageSize)))
const pagedRows = computed(() => {
  const start = (state.page - 1) * state.pageSize
  return filteredRows.value.slice(start, start + state.pageSize)
})
const selectedOrderSet = computed(() => new Set(state.selectedOrders))
const selectedRowSet = computed(() => new Set(state.selectedRows.map(row => `${row.table}:${row.id}`)))
const pageOrderNos = computed(() => pagedRows.value.map(orderNoOf).filter(Boolean))
const pageOrdersChecked = computed(() => pageOrderNos.value.length > 0 && pageOrderNos.value.every(orderNo => selectedOrderSet.value.has(orderNo)))
const pageRowKeys = computed(() => pagedRows.value.map(row => rowKey(row)).filter(Boolean))
const pageRowsChecked = computed(() => pageRowKeys.value.length > 0 && pageRowKeys.value.every(key => selectedRowSet.value.has(key)))
const dataColumnWidths = computed(() => defaultColumnWidths(columns.value))
const editFields = computed(() => {
  if (state.tab === 'supplier') {
    return [...supplierProductFields, ...(extraProductFields.supplier || [])]
  }
  if (state.tab === 'customerProducts') {
    return [...customerProductFields, ...(extraProductFields.customerProducts || [])]
  }
  if (state.tab === 'internal') {
    return [...productFields]
  }
  if (state.tab === 'priceTrend') {
    return priceTrendManualFields
  }
  return nonProductFields[state.tab] || []
})

function toast(text) {
  message.value = text
  setTimeout(() => {
    if (message.value === text) message.value = ''
  }, 3000)
}

async function load() {
  if (state.tab === 'users') {
    state.users = await request.get('/admin/users')
  } else {
    state.rows = await request.get(`/admin/table/${state.tab}`)
  }
  if (state.page > pageTotal.value) {
    state.page = pageTotal.value
  }
}

async function refreshLinkCounts() {
  const [supplierRows, orderRows, unmatchedRows, quoteRows, pricingRows] = await Promise.all([
    request.get('/admin/table/supplier'),
    request.get('/admin/table/orders'),
    request.get('/admin/table/customerProducts'),
    request.get('/admin/table/quotes'),
    request.get('/admin/table/pricingAudit')
  ])
  state.linkCounts.supplier = supplierRows.filter(rowStillUnlinked).length
  state.linkCounts.orders = orderRows.filter(rowNeedsQuote).length
  state.linkCounts.customerProducts = unmatchedRows.filter(rowStillUnlinked).length
  state.linkCounts.quotes = quoteRows.filter(rowNeedsSupplierPrice).length
  state.linkCounts.pricingAudit = pricingRows.filter(rowNeedsPricing).length
}

async function switchTab(tab) {
  state.tab = tab
  state.page = 1
  state.selectedOrders = []
  state.selectedRows = []
  state.globalSearch = ''
  state.columnFilters = {}
  closeModal()
  await load()
}

function openModal(name) {
  state.modal = name
}

function closeModal() {
  state.modal = ''
}

async function addAccount() {
  await request.post('/admin/users', state.account)
  state.account = { username: '', password: '123456', role: 'SUPPLIER', enabled: true }
  closeModal()
  await load()
  toast('账号已添加')
}

function openAccountEdit(row) {
  state.accountEdit = { ...row, password: '' }
  openModal('accountEdit')
}

async function saveAccountEdit() {
  await request.put(`/admin/users/${state.accountEdit.id}`, state.accountEdit)
  closeModal()
  await load()
  toast('账号已修改')
}

async function deleteAccount(id) {
  if (!window.confirm('确定删除这个账号吗？')) return
  await request.delete(`/admin/users/${id}`)
  await load()
  toast('账号已删除')
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
  closeModal()
  toast('密码已修改')
}

async function addMaster() {
  await request.post('/admin/internal-products', state.product)
  state.product = {}
  closeModal()
  await load()
  toast('已添加到主表')
}

function openApprove(row) {
  state.approve = { id: row.id, sourceTable: sourceTableOf(row), code: pick(row, 'code'), newCode: pick(row, 'newCode') }
  openModal('approve')
}

async function approveRow() {
  const path = state.approve.sourceTable === 'supplier'
    ? `/admin/supplier-submissions/${state.approve.id}/approve`
    : state.approve.sourceTable === 'customerProducts'
      ? `/admin/customer-products/${state.approve.id}/approve`
      : `/admin/unmatched-items/${state.approve.id}/approve`
  await request.post(path, { code: state.approve.code, newCode: state.approve.newCode })
  closeModal()
  await load()
  await refreshLinkCounts()
  toast('链接完成')
}

async function generateQuotes() {
  if (!state.selectedOrders.length) {
    toast('请先勾选订单')
    return
  }
  let total = 0
  for (const orderNo of state.selectedOrders) {
    const result = await request.post(`/admin/orders/${orderNo}/generate-quotes`)
    const count = Number(String(result.message || '').match(/\d+/)?.[0] || 0)
    total += count
  }
  state.selectedOrders = []
  await load()
  await refreshLinkCounts()
  toast(`已生成 ${total} 条供应商报价任务`)
}

function openEdit(row) {
  const editRow = { id: row.id, sourceTable: sourceTableOf(row) }
  editFields.value.forEach(([key]) => {
    editRow[key] = pick(row, key)
  })
  state.editRow = editRow
  openModal('edit')
}

async function saveEdit() {
  const tableName = editTableName()
  await request.put(`/admin/table/${tableName}/${state.editRow.id}`, state.editRow)
  closeModal()
  await load()
  await refreshLinkCounts()
  toast('修改已保存')
}

async function deleteRow(row) {
  if (!window.confirm('确定删除这条记录吗？')) return
  await request.delete(`/admin/table/${sourceTableOf(row)}/${row.id}`)
  await load()
  await refreshLinkCounts()
  toast('记录已删除')
}

async function deleteSelectedRows() {
  if (state.tab === 'orders') {
    if (!state.selectedOrders.length) {
      toast('请先勾选要删除的订单')
      return
    }
    if (!window.confirm(`确定删除已选的 ${state.selectedOrders.length} 个订单吗？`)) return
    for (const orderNo of state.selectedOrders) {
      const row = activeRows.value.find(item => orderNoOf(item) === orderNo)
      if (row?.id) {
        await request.delete(`/admin/table/orders/${row.id}`)
      }
    }
    state.selectedOrders = []
    await load()
    toast('已批量删除')
    return
  }
  if (!state.selectedRows.length) {
    toast('请先勾选要删除的记录')
    return
  }
  if (!window.confirm(`确定删除已选的 ${state.selectedRows.length} 条记录吗？`)) return
  for (const row of state.selectedRows) {
    if (row.table === 'users') {
      await request.delete(`/admin/users/${row.id}`)
    } else {
      await request.delete(`/admin/table/${row.table}/${row.id}`)
    }
  }
  state.selectedRows = []
  await load()
  await refreshLinkCounts()
  toast('已批量删除')
}

function openOrderDetail(row) {
  router.push({ name: 'admin-order-detail', params: { orderNo: row.order_no || row.orderNo } })
}

function openQuoteProducts(row) {
  const orderNo = valueOf(row, 'order_no')
  const supplierUsername = valueOf(row, 'supplier_username')
  router.push({ name: 'admin-quote-detail', params: { orderNo, supplierUsername } })
}

function selectedQuoteRows() {
  const selected = new Set(state.selectedRows.map(row => `${row.table}:${row.id}`))
  return activeRows.value.filter(row => selected.has(rowKey(row)))
}

async function downloadAdminQuoteOrders() {
  const rows = selectedQuoteRows()
  if (!rows.length) {
    toast('请先勾选要下载的报价订单')
    return
  }
  const response = await request.post('/admin/quote-orders/download', rows.map(row => row.id), { responseType: 'blob' })
  downloadBlob(response.data, '总后台供应商报价订单.xlsx')
}

function openAdminQuoteModal(row) {
  const targetRows = row ? [row] : selectedQuoteRows()
  if (!targetRows.length) {
    toast('请先勾选要报价的订单')
    return
  }
  if (targetRows.length > 1) {
    toast('一次只能选择一个报价订单')
    return
  }
  const target = targetRows[0]
  const orderNo = valueOf(target, 'order_no')
  const supplierUsername = valueOf(target, 'supplier_username')
  state.activeQuoteTitle = `${orderNo} / ${supplierUsername}`
  state.quoteItems = state.rows
    .filter(item => valueOf(item, 'order_no') === orderNo && valueOf(item, 'supplier_username') === supplierUsername)
    .map(item => ({ ...item }))
  openModal('quotePrice')
}

async function saveAdminQuoteItems() {
  await request.put('/admin/quotes/batch', state.quoteItems.map(item => ({
    id: item.id,
    purchasePrice: item.purchase_price ?? item.purchasePrice,
    salePrice: item.sale_price ?? item.salePrice
  })))
  closeModal()
  await load()
  await refreshLinkCounts()
  toast('报价已保存')
}

function openUseSupplierQuote(item) {
  state.pricingQuote = {
    id: item.id,
    orderNo: valueOf(item, 'order_no'),
    code: valueOf(item, 'code'),
    supplierUsername: valueOf(item, 'supplier_username'),
    purchasePrice: valueOf(item, 'purchase_price'),
    salePrice: valueOf(item, 'sale_price')
  }
  openModal('usePrice')
}

async function useSupplierQuote() {
  const price = state.pricingQuote.purchasePrice
  if (!price) {
    toast('请先填写供应价')
    return
  }
  if (!state.pricingQuote.salePrice) {
    toast('请先填写销售价')
    return
  }
  await request.put(`/admin/quotes/${state.pricingQuote.id}`, {
    purchasePrice: price,
    salePrice: state.pricingQuote.salePrice
  })
  await request.post(`/admin/quotes/${state.pricingQuote.id}/use`)
  state.pricingQuote = {}
  closeModal()
  await load()
  await refreshLinkCounts()
  toast('已采用价格，并同步到客户订单和价格趋势表')
}

function canBatchDelete() {
  return state.tab !== 'priceTrend' && state.tab !== 'pricingAudit'
}

function canUseTableImport() {
  return state.tab !== 'users' && state.tab !== 'quotes' && state.tab !== 'priceTrend' && state.tab !== 'pricingAudit'
}

async function importAdminQuotePrices() {
  if (!state.importFile) {
    toast('请选择 Excel 文件')
    return
  }
  const formData = new FormData()
  formData.append('file', state.importFile)
  const result = await request.post('/admin/quote-orders/import', formData)
  state.importFile = null
  closeModal()
  await load()
  await refreshLinkCounts()
  toast(`导入完成，读取 ${result.total} 行，成功填价 ${result.updated} 行`)
}

async function cancelOrder(row) {
  if (!orderCanCancel(row)) {
    toast('已生成报价任务的订单不能作废')
    return
  }
  if (!window.confirm('确定作废整个订单吗？订单里的产品也会一起作废。')) return
  await request.put(`/admin/orders/${row.order_no || row.orderNo}/cancel`)
  toggleOrderSelection(orderNoOf(row), false)
  await load()
  await refreshLinkCounts()
  toast('订单已作废')
}

function orderNoOf(row) {
  return row?.order_no || row?.orderNo || ''
}

function orderStatusOf(row) {
  return String(valueOf(row, 'status') || '').toUpperCase()
}

function orderCanCancel(row) {
  const status = orderStatusOf(row)
  return status !== 'QUOTE_GENERATED' && status !== 'CANCELLED' && status !== 'COMPLETED'
}

function toggleOrderSelection(orderNo, checked) {
  if (!orderNo) return
  const set = new Set(state.selectedOrders)
  if (checked) {
    set.add(orderNo)
  } else {
    set.delete(orderNo)
  }
  state.selectedOrders = Array.from(set)
}

function togglePageOrders(checked) {
  const set = new Set(state.selectedOrders)
  pageOrderNos.value.forEach(orderNo => {
    if (checked) {
      set.add(orderNo)
    } else {
      set.delete(orderNo)
    }
  })
  state.selectedOrders = Array.from(set)
}

function rowKey(row) {
  if (!row?.id) return ''
  return `${sourceTableOf(row)}:${row.id}`
}

function toggleRowSelection(row, checked) {
  const key = rowKey(row)
  if (!key) return
  const next = state.selectedRows.filter(item => `${item.table}:${item.id}` !== key)
  if (checked) {
    next.push({ table: sourceTableOf(row), id: row.id })
  }
  state.selectedRows = next
}

function togglePageRows(checked) {
  let next = state.selectedRows.filter(item => !pageRowKeys.value.includes(`${item.table}:${item.id}`))
  if (checked) {
    next = next.concat(pagedRows.value.map(row => ({ table: sourceTableOf(row), id: row.id })))
  }
  state.selectedRows = next
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

function rowMatches(row) {
  const global = state.globalSearch.trim().toLowerCase()
  if (global && !columns.value.some(key => cellText(row, key).includes(global))) {
    return false
  }
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

function columnChoices(key) {
  const choices = allColumnValues(key)
  const search = state.filterSearch.trim().toLowerCase()
  return choices.filter(item => !search || item.value.toLowerCase().includes(search))
}

function allColumnValues(key) {
  const counts = new Map()
  activeRows.value.forEach(row => {
    const value = displayValue(row, key)
    counts.set(value, (counts.get(value) || 0) + 1)
  })
  return Array.from(counts.entries())
    .map(([value, count]) => ({ value, count }))
}

function groupedQuoteRows(rows) {
  const map = new Map()
  rows.forEach(row => {
    const key = `${valueOf(row, 'order_no')}|${valueOf(row, 'supplier_username')}|${valueOf(row, 'customer_username')}`
    const current = map.get(key)
    const status = String(valueOf(row, 'status') || '').toUpperCase()
    if (!current) {
      map.set(key, {
        id: row.id,
        order_no: valueOf(row, 'order_no'),
        customer_username: valueOf(row, 'customer_username'),
        supplier_username: valueOf(row, 'supplier_username'),
        status: status === 'WAIT_SUPPLIER_PRICE' ? 'WAIT_SUPPLIER_PRICE' : 'SUPPLIER_PRICED',
        updated_at: valueOf(row, 'updated_at')
      })
    } else {
      if (status === 'WAIT_SUPPLIER_PRICE') current.status = 'WAIT_SUPPLIER_PRICE'
      current.updated_at = valueOf(row, 'updated_at') || current.updated_at
    }
  })
  return Array.from(map.values())
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
  if (checked) {
    current.add(value)
  } else {
    current.delete(value)
  }
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
  state.columnOrder[state.tab] = next
  state.dragColumn = ''
}

function headerStyle(key) {
  return columnStyle(state.columnWidths, state.tab, key, dataColumnWidths.value[key])
}

function resizeHeader(key, event) {
  startColumnResize(state.columnWidths, state.tab, columns.value, key, event)
}

function resetColumns() {
  state.columnOrder[state.tab] = [...baseColumns.value]
  state.columnWidths[state.tab] = {}
}

function clearFilters() {
  state.globalSearch = ''
  state.columnFilters = {}
  closeFilterMenu()
  state.page = 1
}

async function downloadTableTemplate() {
  const response = await request.get(`/admin/table/${apiTableName()}/template`, { responseType: 'blob' })
  downloadBlob(response.data, `${tabs.find(tab => tab[0] === state.tab)?.[1] || state.tab}模板.xlsx`)
}

async function importTable() {
  if (!state.importFile) {
    toast('请选择 Excel 文件')
    return
  }
  try {
    const formData = new FormData()
    formData.append('file', state.importFile)
    const result = await request.post(`/admin/table/${apiTableName()}/import`, formData)
    state.importFile = null
    closeModal()
    await load()
    await refreshLinkCounts()
    if (state.tab === 'orders') {
      toast(`导入完成，生成 ${result.orders} 个订单，共 ${result.total} 条，未匹配 ${result.unmatched} 条`)
    } else {
      toast(`导入完成，共 ${result.total} 条`)
    }
  } catch (error) {
    toast(error.message)
  }
}

function apiTableName() {
  if (state.tab === 'pricingAudit') return 'pricingAudit'
  return state.tab
}

function sourceTableOf(row) {
  if (row?.__table) return row.__table
  return state.tab
}

function editTableName() {
  return state.editRow.sourceTable || apiTableName()
}

function canLink(row) {
  return ['supplier', 'unmatched', 'customerProducts'].includes(sourceTableOf(row)) && rowCanRelink(row)
}

function rowCanRelink(row) {
  const status = String(valueOf(row, 'status') || '').toUpperCase()
  const matched = valueOf(row, 'matched')
  return ['PENDING', 'WAIT_CODE', 'APPROVED', 'ACTIVE'].includes(status) || matched === false || matched === 'false'
}

function rowStillUnlinked(row) {
  const status = String(valueOf(row, 'status') || '').toUpperCase()
  const matched = valueOf(row, 'matched')
  if (['APPROVED', 'ACTIVE'].includes(status)) {
    return false
  }
  if (['PENDING', 'WAIT_CODE'].includes(status)) {
    return true
  }
  return (matched === false || matched === 'false') && !valueOf(row, 'code')
}

function rowNeedsQuote(row) {
  return String(valueOf(row, 'status') || '').toUpperCase() === 'SUBMITTED'
}

function rowNeedsSupplierPrice(row) {
  return String(valueOf(row, 'status') || '').toUpperCase() === 'WAIT_SUPPLIER_PRICE'
}

function rowNeedsPricing(row) {
  return String(valueOf(row, 'pricing_status') || '').toUpperCase() === 'WAIT_USE_PRICE'
}

function tabLinkCount(tab) {
  return state.linkCounts[tab] || 0
}

function statusClass(row, key) {
  if (key !== 'status' && key !== 'pricing_status') return ''
  const status = key === 'pricing_status'
    ? String(valueOf(row, key) || '').toUpperCase()
    : orderStatusOf(row)
  if (status === 'CANCELLED') return 'status-cancelled'
  if (status === 'COMPLETED') return 'status-completed'
  if (status === 'WAIT_USE_PRICE') return 'status-submitted'
  if (status === 'USED_PRICE') return 'status-completed'
  if (status === 'NOT_USE_PRICE') return 'status-cancelled'
  if (status === 'SUBMITTED') return 'status-submitted'
  if (status === 'QUOTE_GENERATED') return 'status-quote'
  if (status === 'WAIT_SUPPLIER_PRICE') return 'status-submitted'
  if (status === 'SUPPLIER_PRICED') return 'status-quote'
  if (status === 'PENDING' || status === 'WAIT_CODE') return 'status-wait'
  if (status === 'APPROVED' || status === 'ACTIVE') return 'status-linked'
  return rowStillUnlinked(row) ? 'status-wait' : ''
}

function downloadButtonText() {
  return state.tab === 'orders' ? '下载下单模板' : '下载模板'
}

function importButtonText() {
  return state.tab === 'orders' ? '导入表格进行下单' : '导入 Excel'
}

function downloadBlob(blob, filename) {
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = filename
  link.click()
  URL.revokeObjectURL(url)
}

function pick(row, camelKey) {
  const snakeKey = camelToSnake(camelKey)
  return row[camelKey] ?? row[snakeKey] ?? ''
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
  await load()
  await refreshLinkCounts()
})
</script>

<template>
  <main class="workspace">
    <header class="topbar">
      <div><strong>{{ user.username }}</strong><span>总后台</span></div>
      <div class="topbar-actions">
        <button @click="openModal('password')">修改密码</button>
        <button @click="logout">退出</button>
      </div>
    </header>

    <p v-if="message" class="notice">{{ message }}</p>

    <section class="dashboard">
      <aside class="side">
        <button v-for="tab in tabs" :key="tab[0]" :class="{ active: state.tab === tab[0] }" @click="switchTab(tab[0])">
          <span>{{ tab[1] }}</span>
          <em v-if="tabLinkCount(tab[0])">{{ tabLinkCount(tab[0]) }}</em>
        </button>
      </aside>

      <div class="content">
        <section class="panel">
          <div class="panel-title-row">
            <h2>{{ tabs.find(tab => tab[0] === state.tab)?.[1] }}</h2>
            <div class="panel-actions">
              <button v-if="state.tab === 'users'" @click="openModal('account')">新增账号</button>
              <button v-if="state.tab === 'internal'" @click="openModal('master')">
                新增主表
              </button>
              <button v-if="state.tab === 'orders'" @click="generateQuotes">生成报价任务</button>
              <button v-if="state.tab === 'quotes'" class="primary" @click="downloadAdminQuoteOrders">下载订单</button>
              <button v-if="state.tab === 'quotes'" class="primary" @click="openModal('quoteImport')">导入表格填价</button>
              <button v-if="state.tab === 'quotes'" class="primary" @click="openAdminQuoteModal()">报价</button>
              <button v-if="canBatchDelete()" class="danger" @click="deleteSelectedRows">批量删除</button>
              <button v-if="canUseTableImport()" @click="downloadTableTemplate">{{ downloadButtonText() }}</button>
              <button v-if="canUseTableImport()" @click="openModal('import')">{{ importButtonText() }}</button>
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
                <col class="select-col">
                <col v-for="key in columns" :key="key" :style="headerStyle(key)">
                <col class="action-col action-col-wide">
              </colgroup>
              <thead>
                <tr>
                  <th v-if="state.tab === 'orders'" class="check-cell">
                    <input type="checkbox" :checked="pageOrdersChecked" @change="togglePageOrders($event.target.checked)">
                  </th>
                  <th v-else class="check-cell">
                    <input type="checkbox" :checked="pageRowsChecked" @change="togglePageRows($event.target.checked)">
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
                  <th class="action-cell">操作</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="row in pagedRows" :key="`${sourceTableOf(row)}-${row.id}`">
                  <td v-if="state.tab === 'orders'" class="check-cell">
                    <input type="checkbox" :checked="selectedOrderSet.has(orderNoOf(row))" @change="toggleOrderSelection(orderNoOf(row), $event.target.checked)">
                  </td>
                  <td v-else class="check-cell">
                    <input type="checkbox" :checked="selectedRowSet.has(rowKey(row))" @change="toggleRowSelection(row, $event.target.checked)">
                  </td>
                  <td v-for="key in columns" :key="key">
                    <span :class="statusClass(row, key)">{{ formatCell(valueOf(row, key)) }}</span>
                  </td>
                  <td class="row-actions action-cell">
                    <button v-if="state.tab === 'users'" @click="openAccountEdit(row)">修改</button>
                    <button v-else-if="state.tab !== 'orders' && state.tab !== 'quotes' && state.tab !== 'pricingAudit'" @click="openEdit(row)">修改</button>
                    <button v-if="state.tab === 'orders'" @click="openOrderDetail(row)">查看产品</button>
                    <button v-if="state.tab === 'quotes'" @click="openQuoteProducts(row)">查看产品</button>
                    <button v-if="state.tab === 'quotes'" class="primary" @click="openAdminQuoteModal(row)">报价</button>
                    <button v-if="state.tab === 'pricingAudit'" class="primary" @click="openUseSupplierQuote(row)">采用价格</button>
                    <button v-if="canLink(row)" @click="openApprove(row)">链接物料编码</button>
                    <button v-if="state.tab === 'orders'" class="danger" :disabled="!orderCanCancel(row)" @click="cancelOrder(row)">整单作废</button>
                    <button v-else-if="state.tab === 'users'" class="danger" @click="deleteAccount(row.id)">删除</button>
                    <button v-else-if="state.tab !== 'priceTrend' && state.tab !== 'pricingAudit'" class="danger" @click="deleteRow(row)">删除</button>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>

          <div class="pager">
            <span>共 {{ filteredRows.length }} 条</span>
            <span v-if="state.tab === 'orders'">已选 {{ state.selectedOrders.length }} 单</span>
            <span v-else>已选 {{ state.selectedRows.length }} 条</span>
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

    <div v-if="state.modal" class="modal-mask" @click.self="closeModal">
      <section class="modal">
        <header class="modal-head">
          <h3 v-if="state.modal === 'account'">新增账号</h3>
          <h3 v-if="state.modal === 'accountEdit'">修改账号</h3>
          <h3 v-if="state.modal === 'master'">新增主表记录</h3>
          <h3 v-if="state.modal === 'approve'">链接物料编码</h3>
          <h3 v-if="state.modal === 'edit'">修改记录</h3>
          <h3 v-if="state.modal === 'quotePrice'">订单报价 {{ state.activeQuoteTitle }}</h3>
          <h3 v-if="state.modal === 'usePrice'">采用价格</h3>
          <h3 v-if="state.modal === 'quoteImport'">导入表格填价</h3>
          <h3 v-if="state.modal === 'import'">{{ importButtonText() }}</h3>
          <h3 v-if="state.modal === 'password'">修改密码</h3>
          <button @click="closeModal">关闭</button>
        </header>

        <div v-if="state.modal === 'account'" class="modal-body form-grid">
          <input v-model="state.account.username" placeholder="账号">
          <input v-model="state.account.password" placeholder="密码">
          <select v-model="state.account.role">
            <option value="SUPPLIER">供应商</option>
            <option value="CUSTOMER">客户</option>
            <option value="ADMIN">总后台</option>
          </select>
          <button class="primary" @click="addAccount">保存</button>
        </div>

        <div v-if="state.modal === 'accountEdit'" class="modal-body form-grid">
          <input v-model="state.accountEdit.username" placeholder="账号" disabled>
          <input v-model="state.accountEdit.password" placeholder="新密码，不填则不修改">
          <select v-model="state.accountEdit.role">
            <option value="SUPPLIER">供应商</option>
            <option value="CUSTOMER">客户</option>
            <option value="ADMIN">总后台</option>
          </select>
          <select v-model="state.accountEdit.enabled">
            <option :value="true">启用</option>
            <option :value="false">停用</option>
          </select>
          <button class="primary" @click="saveAccountEdit">保存修改</button>
        </div>

        <div v-if="state.modal === 'master'" class="modal-body product-form">
          <input v-for="field in productFields" :key="field[0]" v-model="state.product[field[0]]" :placeholder="field[1]">
          <button class="primary" @click="addMaster">保存</button>
        </div>

        <div v-if="state.modal === 'approve'" class="modal-body form-grid">
          <input v-model="state.approve.code" placeholder="编码">
          <input v-model="state.approve.newCode" placeholder="新编码">
          <button class="primary" @click="approveRow">确认链接物料编码</button>
        </div>

        <div v-if="state.modal === 'edit'" class="modal-body product-form">
          <input v-for="field in editFields" :key="field[0]" v-model="state.editRow[field[0]]" :placeholder="field[1]">
          <button class="primary" @click="saveEdit">保存修改</button>
        </div>

        <div v-if="state.modal === 'quotePrice'" class="modal-body">
          <div class="table-wrap compact">
            <table>
              <thead>
                <tr><th>订单编号</th><th>供应商账号</th><th>物料编码</th><th>规格型号</th><th>供应价</th><th>状态</th></tr>
              </thead>
              <tbody>
                <tr v-for="item in state.quoteItems" :key="item.id">
                  <td>{{ item.order_no || item.orderNo }}</td>
                  <td>{{ item.supplier_username || item.supplierUsername }}</td>
                  <td>{{ item.code }}</td>
                  <td>{{ item.spec_model || item.specModel }}</td>
                  <td><input v-model="item.purchase_price" placeholder="供应价"></td>
                  <td><span :class="statusClass(item, 'status')">{{ formatCell(item.status) }}</span></td>
                </tr>
              </tbody>
            </table>
          </div>
          <div class="modal-body form-grid">
            <button class="primary" @click="saveAdminQuoteItems">保存报价</button>
          </div>
        </div>

        <div v-if="state.modal === 'usePrice'" class="modal-body form-grid">
          <input :value="state.pricingQuote.orderNo" disabled>
          <input :value="state.pricingQuote.code" disabled>
          <input :value="state.pricingQuote.supplierUsername" disabled>
          <input v-model="state.pricingQuote.purchasePrice" placeholder="供应价">
          <input v-model="state.pricingQuote.salePrice" placeholder="销售价">
          <button class="primary" @click="useSupplierQuote">确认采用价格</button>
        </div>

        <div v-if="state.modal === 'quoteImport'" class="modal-body form-grid">
          <input type="file" accept=".xlsx" @change="state.importFile = $event.target.files[0]">
          <button class="primary" @click="importAdminQuotePrices">上传并填价</button>
        </div>

        <div v-if="state.modal === 'import'" class="modal-body form-grid">
          <input type="file" accept=".xlsx" @change="state.importFile = $event.target.files[0]">
          <button class="primary" @click="importTable">上传并导入</button>
        </div>

        <div v-if="state.modal === 'password'" class="modal-body form-grid">
          <input v-model="state.passwordForm.password" type="password" placeholder="新密码">
          <input v-model="state.passwordForm.confirmPassword" type="password" placeholder="再次输入新密码">
          <button class="primary" @click="changePassword">确认修改</button>
        </div>
      </section>
    </div>
  </main>
</template>
