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
const saveTableStyleText = '\u4fdd\u5b58\u8868\u683c\u6837\u5f0f'

const tabs = [
  ['internal', '\u4e3b\u8868'],
  ['supplier', '\u4f9b\u5e94\u5546\u4ea7\u54c1\u4ef7\u683c\u8868'],
  ['orders', '\u5ba2\u6237\u8ba2\u5355\u8868'],
  ['customerProducts', '\u5ba2\u6237\u4ea7\u54c1\u8868'],
  ['pricingAudit', '\u5b9a\u4ef7\u5ba1\u6838\u8868'],
  ['users', '\u8d26\u53f7\u7ba1\u7406']
]

const navTree = [
  { tab: 'internal', children: ['supplier', 'pricingAudit'] },
  { tab: 'orders' },
  { tab: 'customerProducts' },
  { tab: 'users' }
]

const protectedCodeFields = ['code', 'newCode']
const supplierProductFields = productFields.filter(([key]) => key !== 'salePrice' && key !== 'priceValidUntil')
const supplierProductCreateFields = productFields.filter(([key]) => key !== 'salePrice')
const supplierProductDisplayFields = productFields.filter(([key]) => key !== 'salePrice')
const customerProductFields = productFields.filter(([key]) => key !== 'purchasePrice' && !protectedCodeFields.includes(key))
const priceTrendOrderFields = [
  ['order_price_1', '\u6700\u8fd1\u8ba2\u5355\u4ef7\u683c1'],
  ['order_price_2', '\u6700\u8fd1\u8ba2\u5355\u4ef7\u683c2'],
  ['order_price_3', '\u6700\u8fd1\u8ba2\u5355\u4ef7\u683c3'],
  ['order_price_4', '\u6700\u8fd1\u8ba2\u5355\u4ef7\u683c4'],
  ['order_price_5', '\u6700\u8fd1\u8ba2\u5355\u4ef7\u683c5']
]
const extraProductFields = {
  internal: [],
  supplier: [
    ['status', '\u72b6\u6001'],
    ['supplierUsername', '\u4f9b\u5e94\u5546\u8d26\u53f7']
  ],
  customerProducts: [
    ['status', '\u72b6\u6001'],
    ['customerUsername', '\u5ba2\u6237\u8d26\u53f7']
  ],
  unmatched: [
    ['orderNo', '\u8ba2\u5355\u7f16\u53f7'],
    ['customerUsername', '\u5ba2\u6237\u8d26\u53f7'],
    ['status', '\u72b6\u6001']
  ]
}
const nonProductFields = {
  orders: [
    ['orderNo', '\u8ba2\u5355\u7f16\u53f7'],
    ['customerUsername', '\u5ba2\u6237\u8d26\u53f7'],
    ['status', '\u72b6\u6001']
  ],
  quotes: [
    ['orderNo', '\u8ba2\u5355\u7f16\u53f7'],
    ['customerItemId', '\u5ba2\u6237\u660e\u7ec6ID'],
    ['supplierUsername', '\u4f9b\u5e94\u5546\u8d26\u53f7'],
    ['customerUsername', '\u5ba2\u6237\u8d26\u53f7'],
    ['code', '\u7269\u6599\u7f16\u7801'],
    ['specModel', '\u89c4\u683c\u578b\u53f7'],
    ['purchasePrice', '\u4f9b\u5e94\u4ef7'],
    ['salePrice', '\u9500\u552e\u4ef7'],
    ['status', '\u72b6\u6001']
  ],
  pricingAudit: []
}
const productColumnKeys = productFields.map(([key]) => camelToSnake(key))
const supplierProductColumnKeys = supplierProductDisplayFields.map(([key]) => camelToSnake(key))
const quoteProductColumnKeys = supplierProductColumnKeys.filter(key => key !== 'price_valid_until')
const customerProductColumnKeys = customerProductFields.map(([key]) => camelToSnake(key))
const tableColumns = {
  internal: ['id', ...productColumnKeys, 'updated_at'],
  supplier: ['id', 'link_status', 'quote_status', ...supplierProductColumnKeys, 'supplier_username', 'updated_at'],
  orders: ['id', 'order_no', 'customer_username', 'created_at', 'status', ...productColumnKeys, 'updated_at'],
  customerProducts: ['id', 'status', 'code', 'new_code', ...customerProductColumnKeys, 'customer_username', 'updated_at'],
  quotes: ['id', 'status', ...quoteProductColumnKeys, 'supplier_username', 'updated_at'],
  pricingAudit: [
    'id', 'current_order_status', 'price_source_status', 'code', 'new_code', 'brand', 'craft_material', 'spec_model',
    'common_model', 'purchase_price', 'sale_price', 'price_valid_until', 'valid_price',
    'ref_price_1', 'supplier_1', 'ref_valid_until_1', 'ref_price_2', 'supplier_2', 'ref_valid_until_2',
    'ref_price_3', 'supplier_3', 'ref_valid_until_3', 'ref_price_4', 'supplier_4', 'ref_valid_until_4',
    'ref_price_5', 'supplier_5', 'ref_valid_until_5'
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
  priceTrendRows: [],
  pricingQuote: {},
  priceItem: {},
  activeQuoteTitle: '',
  quoteItems: [],
  importFile: null,
  selectedOrders: [],
  selectedRows: [],
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
  navOpen: { internal: false },
  linkCounts: { supplier: 0, orders: 0, customerProducts: 0, quotes: 0, pricingAudit: 0 }
})

const businessPageSizes = [1000, 2000, 3000, 5000]
const accountPageSizes = [15, 30, 50, 100]
const pageSizeOptions = computed(() => state.tab === 'users' ? accountPageSizes : businessPageSizes)

const hiddenColumns = ['source_type', 'sourceType', 'serial_no', 'serialNo']
const activeRows = computed(() => {
  if (state.tab === 'users') return state.users
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
const selectedRowSet = computed(() => new Set(state.selectedRows.map(row => `${row.table}:${row.id}`)))
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
  const [supplierRows, orderRows, unmatchedRows, pricingRows] = await Promise.all([
    request.get('/admin/table/supplier'),
    request.get('/admin/table/orders'),
    request.get('/admin/table/customerProducts'),
    request.get('/admin/table/pricingAudit')
  ])
  state.linkCounts.supplier = supplierRows.filter(rowNeedsSupplierPrice).length
  state.linkCounts.orders = orderRows.filter(rowNeedsQuote).length
  state.linkCounts.customerProducts = unmatchedRows.filter(rowStillUnlinked).length
  state.linkCounts.pricingAudit = pricingRows.filter(rowNeedsPricing).length
}

async function switchTab(tab) {
  state.tab = tab
  state.page = 1
  state.pageSize = tab === 'users' ? 15 : 1000
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

async function addSupplierProduct() {
  await request.post('/admin/supplier-products', state.product)
  state.product = {}
  closeModal()
  await load()
  await refreshLinkCounts()
  toast('\u4f9b\u5e94\u5546\u4ea7\u54c1\u5df2\u65b0\u589e')
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
  const itemIds = state.selectedRows
    .filter(row => row.table === 'orders')
    .map(row => row.id)
  if (!itemIds.length) {
    toast('请先勾选订单')
    return
  }
  const result = await request.post('/admin/order-items/generate-quotes', itemIds)
  const total = Number(String(result.message || '').match(/\d+/)?.[0] || 0)
  state.selectedRows = state.selectedRows.filter(row => row.table !== 'orders')
  await load()
  await refreshLinkCounts()
  toast(`已生成${total}条供应商报价任务`)
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
    await batchCancelOrderItems()
    return
  }
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

function openPriceTrendModal() {
  if (state.tab !== 'internal') return
  const selectedIds = new Set(
    state.selectedRows
      .filter(row => row.table === 'internal')
      .map(row => Number(row.id))
  )
  if (!selectedIds.size) {
    toast('请先勾选主表产品')
    return
  }
  state.priceTrendRows = state.rows.filter(row => selectedIds.has(Number(row.id)))
  if (!state.priceTrendRows.length) {
    toast('未找到已勾选的主表产品')
    return
  }
  openModal('priceTrend')
}

function openOrderDetail(row) {
  router.push({ name: 'admin-order-detail', params: { orderNo: row.order_no || row.orderNo } })
}

function selectedQuoteRows() {
  const selected = new Set(state.selectedRows.map(row => `${row.table}:${row.id}`))
  return activeRows.value.filter(row => selected.has(rowKey(row)))
}

function selectedCurrentRows() {
  const selected = new Set(state.selectedRows.map(row => `${row.table}:${row.id}`))
  return activeRows.value.filter(row => selected.has(rowKey(row)))
}

async function downloadAdminQuoteOrders() {
  const rows = selectedQuoteRows()
  if (!rows.length) {
    toast('请先勾选要下载的报价')
    return
  }
  const response = await request.post('/admin/quote-orders/download', rows.map(row => row.id), { responseType: 'blob' })
  downloadBlob(response.data, '总后台供应商报价订单.xlsx')
}

function openAdminQuoteModal(row) {
  const targetRows = row ? [row] : selectedQuoteRows()
  if (!targetRows.length) {
    toast('\u8bf7\u5148\u52fe\u9009\u8981\u62a5\u4ef7\u7684\u4ea7\u54c1')
    return
  }
  state.activeQuoteTitle = `${targetRows.length} ?`
  state.quoteItems = targetRows.map(normalizeQuoteItem)
  openModal('quotePrice')
}

function normalizeQuoteItem(item) {
  return {
    ...item,
    purchase_price: valueOf(item, 'purchase_price'),
    purchasePrice: valueOf(item, 'purchase_price'),
    price_valid_until: valueOf(item, 'price_valid_until'),
    priceValidUntil: valueOf(item, 'price_valid_until')
  }
}

async function saveAdminQuoteItems() {
  if (state.tab === 'supplier') {
    await request.put('/admin/supplier-products/quotes/batch', state.quoteItems.map(item => ({
      supplierUsername: valueOf(item, 'supplier_username') || valueOf(item, 'supplierUsername'),
      code: valueOf(item, 'code'),
      purchasePrice: item.purchase_price ?? item.purchasePrice,
      priceValidUntil: item.price_valid_until ?? item.priceValidUntil
    })))
  } else {
    await request.put('/admin/quotes/batch', state.quoteItems.map(item => ({
      id: item.id,
      purchasePrice: item.purchase_price ?? item.purchasePrice,
      salePrice: item.sale_price ?? item.salePrice,
      priceValidUntil: item.price_valid_until ?? item.priceValidUntil
    })))
  }
  closeModal()
  await load()
  await refreshLinkCounts()
  toast('报价已保存')
}

function openUseSupplierQuote(item) {
  const options = []
  const validPrice = valueOf(item, 'valid_price')
  if (validPrice) {
    options.push({
      source: '\u6709\u6548\u671f\u5185\u4ef7\u683c',
      purchasePrice: validPrice,
      supplierUsername: '\u4e3b\u8868',
      priceValidUntil: valueOf(item, 'price_valid_until')
    })
  }
  for (let index = 1; index <= 5; index++) {
    const purchasePrice = valueOf(item, `ref_price_${index}`)
    const supplierUsername = valueOf(item, `supplier_${index}`)
    const priceValidUntil = valueOf(item, `ref_valid_until_${index}`)
    if (purchasePrice) {
      options.push({ source: `\u53c2\u8003\u4ef7${index}`, purchasePrice, supplierUsername, priceValidUntil })
    }
  }
  state.pricingQuote = {
    code: valueOf(item, 'code'),
    options,
    salePrice: valueOf(item, 'sale_price') || '',
    priceValidUntil: valueOf(item, 'price_valid_until')
  }
  openModal('usePrice')
}

async function useSupplierQuote(option) {
  if (false && !option?.quoteId) {
    toast('没有可采用的价格')
    return
  }
  const price = option.purchasePrice
  if (!price) {
    toast('请先填写供应价')
    return
  }
  if (!state.pricingQuote.salePrice) {
    toast('请先填写销售价')
    return
  }
  await request.post('/admin/pricing-audit/use-price', {
    code: state.pricingQuote.code,
    purchasePrice: price,
    salePrice: state.pricingQuote.salePrice,
    priceValidUntil: state.pricingQuote.priceValidUntil || option.priceValidUntil || null,
    supplierUsername: option.supplierUsername
  })
  state.pricingQuote = {}
  closeModal()
  await load()
  await refreshLinkCounts()
  toast('已采用价格，并同步到客户订单和主表')
}

function selectedPricingAuditRows() {
  return state.selectedRows
    .filter(row => row.table === 'pricingAudit')
    .map(row => state.rows.find(item => String(item.id) === String(row.id)))
    .filter(Boolean)
}

async function sendPricingAuditQuotes(row) {
  const rows = row ? [row] : selectedPricingAuditRows()
  const codes = rows
    .filter(item => valueOf(item, 'current_order_status') === 'HAS_ORDER')
    .map(item => valueOf(item, 'code'))
    .filter(Boolean)
  if (!codes.length) {
    toast('\u8bf7\u5148\u9009\u62e9\u6709\u8ba2\u5355\u7684\u7269\u6599')
    return
  }
  const result = await request.post('/admin/pricing-audit/send-quotes', codes)
  state.selectedRows = state.selectedRows.filter(item => item.table !== 'pricingAudit')
  await load()
  await refreshLinkCounts()
  toast(`\u5df2\u53d1\u9001${Number(String(result.message || '').match(/\d+/)?.[0] || 0)}\u6761\u62a5\u4ef7\u4efb\u52a1`)
}

async function batchUsePricingAuditPrice() {
  const rows = selectedPricingAuditRows()
  if (!rows.length) {
    toast('\u8bf7\u5148\u52fe\u9009\u8981\u91c7\u7528\u4ef7\u683c\u7684\u7269\u6599')
    return
  }
  let count = 0
  for (const row of rows) {
    const option = bestPricingOption(row)
    const salePrice = valueOf(row, 'sale_price')
    const priceValidUntil = option?.priceValidUntil || valueOf(row, 'price_valid_until')
    if (!option || !salePrice || !priceValidUntil) continue
    await request.post('/admin/pricing-audit/use-price', {
      code: valueOf(row, 'code'),
      purchasePrice: option.purchasePrice,
      salePrice,
      priceValidUntil,
      supplierUsername: option.supplierUsername
    })
    count++
  }
  state.selectedRows = state.selectedRows.filter(item => item.table !== 'pricingAudit')
  await load()
  await refreshLinkCounts()
  toast(`\u5df2\u6279\u91cf\u91c7\u7528${count}\u6761\u4ef7\u683c`)
}

function bestPricingOption(row) {
  const options = []
  const validPrice = valueOf(row, 'valid_price')
  if (validPrice) {
    options.push({ purchasePrice: validPrice, supplierUsername: '\u4e3b\u8868', priceValidUntil: valueOf(row, 'price_valid_until') })
  }
  for (let index = 1; index <= 5; index++) {
    const purchasePrice = valueOf(row, `ref_price_${index}`)
    if (purchasePrice) {
      options.push({
        purchasePrice,
        supplierUsername: valueOf(row, `supplier_${index}`),
        priceValidUntil: valueOf(row, `ref_valid_until_${index}`)
      })
    }
  }
  return options.sort((left, right) => Number(left.purchasePrice) - Number(right.purchasePrice))[0]
}

function canBatchDelete() {
  return state.tab !== 'pricingAudit'
}

function canUseTableImport() {
  return state.tab !== 'users' && state.tab !== 'quotes' && state.tab !== 'pricingAudit'
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

async function batchCancelOrderItems() {
  const orderItems = state.selectedRows.filter(row => row.table === 'orders')
  if (!orderItems.length) {
    toast('请先勾选要作废的产品')
    return
  }
  if (!window.confirm(`确定作废已选的 ${orderItems.length} 个订单产品吗？`)) return
  for (const row of orderItems) {
    await request.put(`/admin/order-items/${row.id}/cancel`)
  }
  state.selectedRows = state.selectedRows.filter(row => row.table !== 'orders')
  await load()
  await refreshLinkCounts()
  toast('已批量作废')
}

function openOrderItemPrice(row) {
  state.priceItem = {
    id: row.id,
    code: valueOf(row, 'code'),
    purchasePrice: valueOf(row, 'purchase_price'),
    salePrice: valueOf(row, 'sale_price')
  }
  openModal('orderItemPrice')
}

async function saveOrderItemPrice() {
  if (!state.priceItem.purchasePrice && !state.priceItem.salePrice) {
    toast('请填写供应价或销售价')
    return
  }
  await request.put(`/admin/order-items/${state.priceItem.id}/prices`, {
    purchasePrice: state.priceItem.purchasePrice || null,
    salePrice: state.priceItem.salePrice || null
  })
  closeModal()
  await load()
  await refreshLinkCounts()
  toast('价格已保存')
}

async function cancelOrderItem(row) {
  if (!orderCanCancel(row)) {
    toast('当前产品不能作废')
    return
  }
  if (!window.confirm('确定作废这个产品吗？')) return
  await request.put(`/admin/order-items/${row.id}/cancel`)
  toggleRowSelection(row, false)
  await load()
  await refreshLinkCounts()
  toast('产品已作废')
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
  return status !== 'QUOTE_GENERATED' && status !== 'CANCELLED' && status !== 'COMPLETED' && status !== 'QUOTE_COMPLETED'
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
  return text || '(绌?'
}

function displayOrderCell(row, key, rowIndex) {
  if (state.tab === 'orders' && ['order_no', 'customer_username', 'created_at'].includes(key)) {
    const previous = pagedRows.value[rowIndex - 1]
    if (previous && orderNoOf(previous) === orderNoOf(row)) {
      return ''
    }
  }
  return formatCell(valueOf(row, key))
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
    const key = `${valueOf(row, 'pricing_group') || valueOf(row, 'order_no')}|${valueOf(row, 'supplier_username')}|${valueOf(row, 'code')}`
    const current = map.get(key)
    const status = String(valueOf(row, 'status') || '').toUpperCase()
    if (!current) {
      map.set(key, {
        id: row.id,
        pricing_group: valueOf(row, 'pricing_group'),
        code: valueOf(row, 'code'),
        spec_model: valueOf(row, 'spec_model'),
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

function tableStyleKey(scope = state.tab) {
  return `table-style:${user.role || 'ADMIN'}:${user.username || 'anonymous'}:${scope}`
}

function saveTableStyle() {
  localStorage.setItem(tableStyleKey(), JSON.stringify({
    columnOrder: state.columnOrder[state.tab] || [...columns.value],
    columnWidths: state.columnWidths[state.tab] || {}
  }))
  toast('\u8868\u683c\u6837\u5f0f\u5df2\u4fdd\u5b58')
}

function loadTableStyles() {
  tabs.forEach(([scope]) => {
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

async function downloadTableTemplate() {
  const response = await request.get(`/admin/table/${apiTableName()}/template`, { responseType: 'blob' })
  downloadBlob(response.data, `${tabs.find(tab => tab[0] === state.tab)?.[1] || state.tab}妯℃澘.xlsx`)
}

function exportSelectedRows() {
  const rows = selectedCurrentRows()
  if (!rows.length) {
    toast('\u8bf7\u5148\u52fe\u9009\u8981\u5bfc\u51fa\u7684\u6570\u636e')
    return
  }
  exportRowsAsExcel(rows, columns.value, `${tabLabel(state.tab)}-\u9009\u4e2d\u6570\u636e.xls`)
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
  openModal(mode === 'quote' ? 'quoteImport' : 'import')
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
  return ['PENDING', 'WAIT_CODE', 'CODE_NOT_FOUND', 'APPROVED', 'ACTIVE'].includes(status) || matched === false || matched === 'false'
}

function rowStillUnlinked(row) {
  const status = String(valueOf(row, 'status') || '').toUpperCase()
  const matched = valueOf(row, 'matched')
  if (['APPROVED', 'ACTIVE'].includes(status)) {
    return false
  }
  if (['PENDING', 'WAIT_CODE', 'CODE_NOT_FOUND'].includes(status)) {
    return true
  }
  return (matched === false || matched === 'false') && !valueOf(row, 'code')
}

function rowNeedsQuote(row) {
  const status = String(valueOf(row, 'status') || '').toUpperCase()
  return Boolean(valueOf(row, 'code')) && !['CANCELLED', 'COMPLETED', 'QUOTE_COMPLETED'].includes(status)
}

function rowNeedsSupplierPrice(row) {
  const quoteStatus = String(valueOf(row, 'quote_status') || valueOf(row, 'quoteStatus') || '').toUpperCase()
  return quoteStatus === 'NEED_QUOTE' || String(valueOf(row, 'status') || '').toUpperCase() === 'WAIT_SUPPLIER_PRICE'
}

function rowNeedsPricing(row) {
  return String(valueOf(row, 'current_order_status') || '').toUpperCase() === 'HAS_ORDER'
}

function tabLinkCount(tab) {
  return state.linkCounts[tab] || 0
}

function fieldInputType(fieldKey) {
  return fieldKey === 'priceValidUntil' ? 'date' : 'text'
}

function tabLabel(tab) {
  return tabs.find(item => item[0] === tab)?.[1] || tab
}

function navNodeActive(node) {
  return state.tab === node.tab || Boolean(node.children?.includes(state.tab))
}

function toggleNavGroup(tab) {
  state.navOpen[tab] = !state.navOpen[tab]
}

function statusClass(row, key) {
  if (!['status', 'link_status', 'quote_status', 'pricing_status', 'price_source_status', 'current_order_status'].includes(key)) return ''
  const status = key === 'pricing_status'
    ? String(valueOf(row, key) || '').toUpperCase()
    : key === 'quote_status' || key === 'link_status' || key === 'price_source_status' || key === 'current_order_status'
      ? String(valueOf(row, key) || '').toUpperCase()
      : orderStatusOf(row)
  if (status === 'CANCELLED') return 'status-cancelled'
  if (status === 'QUOTE_COMPLETED' || status === 'COMPLETED') return 'status-completed'
  if (status === 'WAIT_USE_PRICE') return 'status-submitted'
  if (status === 'USED_PRICE') return 'status-completed'
  if (status === 'NOT_USE_PRICE') return 'status-cancelled'
  if (status === 'VALID_PRICE' || status === 'NO_ORDER') return 'status-linked'
  if (status === 'HAS_ORDER') return 'status-submitted'
  if (status === 'NO_VALID_PRICE') return 'status-wait'
  if (status === 'SUBMITTED' || status === 'SUBMITTED_ORDER') return 'status-submitted'
  if (status === 'QUOTE_GENERATED') return 'status-linked'
  if (status === 'WAIT_SUPPLIER_PRICE') return 'status-submitted'
  if (status === 'NEED_QUOTE') return 'status-submitted'
  if (status === 'QUOTED') return 'status-quote'
  if (status === 'SUPPLIER_PRICED') return 'status-quote'
  if (status === 'PENDING' || status === 'WAIT_CODE' || status === 'CODE_NOT_FOUND') return 'status-wait'
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
  localStorage.removeItem('token')
  localStorage.removeItem('user')
  router.push('/')
}

onMounted(async () => {
  loadTableStyles()
  await load()
  await refreshLinkCounts()
})
</script>

<template>
  <main class="workspace">
    <header class="topbar">
      <div><strong>{{ user.username }}</strong><span>{{ '\u603b\u540e\u53f0' }}</span></div>
      <div class="topbar-actions">
        <button @click="state.sidebarCollapsed = !state.sidebarCollapsed">
          {{ state.sidebarCollapsed ? '展开栏目' : '收起栏目' }}
        </button>
        <button @click="openModal('password')">修改密码</button>
        <button @click="logout">{{ '\u9000\u51fa' }}</button>
      </div>
    </header>

    <p v-if="message" class="notice">{{ message }}</p>

    <section class="dashboard" :class="{ collapsed: state.sidebarCollapsed }">
      <aside v-if="!state.sidebarCollapsed" class="side">
        <div v-for="node in navTree" :key="node.tab" class="tree-group">
          <div class="tree-row">
            <button class="tree-main" :class="{ active: state.tab === node.tab, 'child-active': navNodeActive(node) && state.tab !== node.tab }" @click="switchTab(node.tab)">
              <span>{{ tabLabel(node.tab) }}</span>
              <em v-if="tabLinkCount(node.tab)">{{ tabLinkCount(node.tab) }}</em>
            </button>
            <button v-if="node.children?.length" class="tree-toggle" @click.stop="toggleNavGroup(node.tab)">
              {{ state.navOpen[node.tab] ? '\u25be' : '\u25b8' }}
            </button>
          </div>
          <div v-if="node.children?.length && state.navOpen[node.tab]" class="tree-children">
            <button v-for="child in node.children" :key="child" class="tree-child" :class="{ active: state.tab === child }" @click="switchTab(child)">
              <span>{{ tabLabel(child) }}</span>
              <em v-if="tabLinkCount(child)">{{ tabLinkCount(child) }}</em>
            </button>
          </div>
        </div>
      </aside>

      <div class="content">
        <section class="panel">
          <div class="panel-title-row">
            <h2>{{ tabLabel(state.tab) }}</h2>
            <div class="panel-actions">
              <button v-if="state.tab === 'users'" @click="openModal('account')">新增账号</button>
              <button v-if="state.tab === 'internal'" @click="openModal('master')">
                新增主表
              </button>
              
              <button v-if="state.tab === 'pricingAudit'" class="primary" @click="sendPricingAuditQuotes()">{{ '\u53d1\u9001\u62a5\u4ef7\u4efb\u52a1' }}</button>
              <button v-if="state.tab === 'pricingAudit'" class="primary" @click="batchUsePricingAuditPrice">{{ '\u6279\u91cf\u91c7\u7528\u4ef7\u683c' }}</button>
              <button v-if="state.tab === 'quotes'" class="primary" @click="downloadAdminQuoteOrders">{{ '\u4e0b\u8f7d\u62a5\u4ef7\u8868' }}</button>
              <button v-if="state.tab === 'quotes'" class="primary" @click="openModal('quoteImport')">{{ '\u5bfc\u5165\u8868\u683c\u586b\u4ef7' }}</button>
              <button v-if="state.tab === 'quotes'" class="primary" @click="openAdminQuoteModal()">报价</button>
              <button v-if="state.tab === 'supplier'" @click="openModal('supplierProduct')">{{ '\u65b0\u589e\u4ea7\u54c1\u4fe1\u606f' }}</button>
              <button v-if="state.tab === 'supplier'" class="primary" @click="openAdminQuoteModal()">{{ '\u62a5\u4ef7' }}</button>
              <button v-if="canBatchDelete()" class="danger" @click="deleteSelectedRows">{{ state.tab === 'orders' ? '批量作废' : '批量删除' }}</button>
              <button v-if="state.tab === 'internal'" class="primary" @click="openPriceTrendModal">查看价格趋势</button>
              <div v-if="state.tab === 'supplier'" class="menu-wrap">
                <button @click="toggleActionMenu('download')">{{ '\u4e0b\u8f7d\u8868\u683c' }}</button>
                <div v-if="state.actionMenu === 'download'" class="action-menu">
                  <button @click="downloadTableTemplate(); state.actionMenu = ''">{{ '\u53ea\u5bfc\u51fa\u6a21\u677f' }}</button>
                  <button @click="exportSelectedRows(); state.actionMenu = ''">{{ '\u5bfc\u51fa\u9009\u4e2d\u6570\u636e' }}</button>
                </div>
              </div>
              <div v-if="state.tab === 'supplier'" class="menu-wrap">
                <button @click="toggleActionMenu('import')">{{ '\u5bfc\u5165\u8868\u683c' }}</button>
                <div v-if="state.actionMenu === 'import'" class="action-menu">
                  <button @click="openImportMode('create')">{{ '\u5bfc\u5165\u65b0\u589e' }}</button>
                  <button @click="openImportMode('update')">{{ '\u5bfc\u5165\u4fee\u6539' }}</button>
                  <button @click="openImportMode('quote')">{{ '\u5bfc\u5165\u62a5\u4ef7' }}</button>
                </div>
              </div>
              <button v-if="canUseTableImport() && state.tab !== 'supplier'" @click="downloadTableTemplate">{{ downloadButtonText() }}</button>
              <button v-if="canUseTableImport() && state.tab !== 'supplier'" @click="openModal('import')">{{ importButtonText() }}</button>
              <button @click="saveTableStyle">{{ saveTableStyleText }}</button>
            </div>
          </div>

          <div class="table-tools">
            <input v-model="state.globalSearch" :placeholder="'\u641c\u7d22\u5f53\u524d\u8868'" @input="state.page = 1">
            <button @click="clearFilters">{{ '\u6e05\u9664\u7b5b\u9009' }}</button>
            <button @click="resetColumns">{{ '\u6062\u590d\u5217\u987a\u5e8f' }}</button>
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
                  <th class="check-cell">
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
                        {{ '\u25be' }}
                      </button>
                    </div>
                    <span class="col-resizer" @mousedown="resizeHeader(key, $event)"></span>
                    <div v-if="state.filterMenu === key" class="excel-filter-menu" :style="state.filterMenuStyle" @click.stop>
                      <input v-model="state.filterSearch" :placeholder="'\u641c\u7d22\u6b64\u5217'">
                      <div class="filter-links">
                        <button type="button" @click="selectAllFilterValues(key)">{{ '\u5168\u9009' }}</button>
                        <button type="button" @click="clearAllFilterValues(key)">{{ '\u6e05\u7a7a' }}</button>
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
                  <th class="action-cell">{{ '\u64cd\u4f5c' }}</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="(row, rowIndex) in pagedRows" :key="`${sourceTableOf(row)}-${row.id}`">
                  <td class="check-cell">
                    <input type="checkbox" :checked="selectedRowSet.has(rowKey(row))" @change="toggleRowSelection(row, $event.target.checked)">
                  </td>
                  <td v-for="key in columns" :key="key">
                    <span :class="statusClass(row, key)">{{ displayOrderCell(row, key, rowIndex) }}</span>
                  </td>
                  <td class="row-actions action-cell">
                    <button v-if="state.tab === 'users'" @click="openAccountEdit(row)">修改</button>
                    <button v-else-if="state.tab !== 'orders' && state.tab !== 'quotes' && state.tab !== 'pricingAudit'" @click="openEdit(row)">修改</button>
                    <button v-if="state.tab === 'orders'" class="primary" @click="openOrderItemPrice(row)">添加价格</button>
                    <button v-if="state.tab === 'quotes'" class="primary" @click="openAdminQuoteModal(row)">报价</button>
                    <button v-if="state.tab === 'supplier'" class="primary" @click="openAdminQuoteModal(row)">{{ '\u62a5\u4ef7' }}</button>
                    <button v-if="state.tab === 'pricingAudit' && valueOf(row, 'current_order_status') === 'HAS_ORDER'" class="primary" @click="sendPricingAuditQuotes(row)">{{ '\u53d1\u9001\u62a5\u4ef7' }}</button>
                    <button v-if="state.tab === 'pricingAudit'" class="primary" @click="openUseSupplierQuote(row)">{{ '\u91c7\u7528\u4ef7\u683c' }}</button>
                    <button v-if="canLink(row)" @click="openApprove(row)">{{ '\u94fe\u63a5\u7269\u6599\u7f16\u7801' }}</button>
                    <button v-if="state.tab === 'orders'" class="danger" :disabled="!orderCanCancel(row)" @click="cancelOrderItem(row)">产品作废</button>
                    <button v-else-if="state.tab === 'users'" class="danger" @click="deleteAccount(row.id)">删除</button>
                    <button v-else-if="state.tab !== 'pricingAudit'" class="danger" @click="deleteRow(row)">删除</button>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>

          <div class="pager">
            <span>{{ '\u5171' }} {{ filteredRows.length }} {{ '\u6761' }}</span>
            <span>{{ '\u5df2\u9009' }} {{ state.selectedRows.length }} {{ '\u6761' }}</span>
            <label>
              {{ '\u6bcf\u9875' }}
              <select v-model.number="state.pageSize" @change="changePageSize">
                <option v-for="size in pageSizeOptions" :key="size" :value="size">{{ size }}</option>
              </select>
              {{ '\u6761' }}
            </label>
            <button :disabled="state.page <= 1" @click="prevPage">{{ '\u4e0a\u4e00\u9875' }}</button>
            <span>{{ state.page }} / {{ pageTotal }}</span>
            <button :disabled="state.page >= pageTotal" @click="nextPage">{{ '\u4e0b\u4e00\u9875' }}</button>
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
          <h3 v-if="state.modal === 'supplierProduct'">{{ '\u65b0\u589e\u4ea7\u54c1\u4fe1\u606f' }}</h3>
          <h3 v-if="state.modal === 'approve'">{{ '\u94fe\u63a5\u7269\u6599\u7f16\u7801' }}</h3>
          <h3 v-if="state.modal === 'edit'">修改记录</h3>
          <h3 v-if="state.modal === 'quotePrice'">报价</h3>
          <h3 v-if="state.modal === 'usePrice'">{{ '\u91c7\u7528\u4ef7\u683c' }}</h3>
          <h3 v-if="state.modal === 'orderItemPrice'">添加价格</h3>
          <h3 v-if="state.modal === 'priceTrend'">{{ '\u4ef7\u683c\u8d8b\u52bf' }}</h3>
          <h3 v-if="state.modal === 'quoteImport'">{{ '\u5bfc\u5165\u8868\u683c\u586b\u4ef7' }}</h3>
          <h3 v-if="state.modal === 'import'">{{ importButtonText() }}</h3>
          <h3 v-if="state.modal === 'password'">修改密码</h3>
          <button @click="closeModal">关闭</button>
        </header>

        <div v-if="state.modal === 'account'" class="modal-body form-grid">
          <input v-model="state.account.username" placeholder="账号">
          <input v-model="state.account.password" placeholder="瀵嗙爜">
          <select v-model="state.account.role">
            <option value="SUPPLIER">{{ '\u4f9b\u5e94\u5546' }}</option>
            <option value="CUSTOMER">客户</option>
            <option value="ADMIN">{{ '\u603b\u540e\u53f0' }}</option>
          </select>
          <button class="primary" @click="addAccount">保存</button>
        </div>

        <div v-if="state.modal === 'accountEdit'" class="modal-body form-grid">
          <input v-model="state.accountEdit.username" placeholder="账号" disabled>
          <input v-model="state.accountEdit.password" placeholder="新密码，不填则不修改">
          <select v-model="state.accountEdit.role">
            <option value="SUPPLIER">{{ '\u4f9b\u5e94\u5546' }}</option>
            <option value="CUSTOMER">客户</option>
            <option value="ADMIN">{{ '\u603b\u540e\u53f0' }}</option>
          </select>
          <select v-model="state.accountEdit.enabled">
            <option :value="true">启用</option>
            <option :value="false">停用</option>
          </select>
          <button class="primary" @click="saveAccountEdit">保存修改</button>
        </div>

        <div v-if="state.modal === 'master'" class="modal-body product-form">
          <input v-for="field in productFields" :key="field[0]" v-model="state.product[field[0]]" :type="fieldInputType(field[0])" :placeholder="field[1]">
          <button class="primary" @click="addMaster">保存</button>
        </div>

        <div v-if="state.modal === 'supplierProduct'" class="modal-body product-form">
          <input v-for="field in [...supplierProductCreateFields, ['supplierUsername', '\u4f9b\u5e94\u5546\u8d26\u53f7']]" :key="field[0]" v-model="state.product[field[0]]" :type="fieldInputType(field[0])" :placeholder="field[1]">
          <button class="primary" @click="addSupplierProduct">{{ '\u4fdd\u5b58' }}</button>
        </div>

        <div v-if="state.modal === 'approve'" class="modal-body form-grid">
          <input v-model="state.approve.code" :placeholder="'\u7269\u6599\u7f16\u7801'">
          <input v-model="state.approve.newCode" :placeholder="'\u65b0\u7f16\u7801'">
          <button class="primary" @click="approveRow">{{ '\u786e\u8ba4\u94fe\u63a5\u7269\u6599\u7f16\u7801' }}</button>
        </div>

        <div v-if="state.modal === 'edit'" class="modal-body product-form">
          <input v-for="field in editFields" :key="field[0]" v-model="state.editRow[field[0]]" :type="fieldInputType(field[0])" :placeholder="field[1]">
          <button class="primary" @click="saveEdit">保存修改</button>
        </div>

        <div v-if="state.modal === 'quotePrice'" class="modal-body quote-modal">
          <div class="table-wrap compact">
            <table>
              <colgroup>
                <col style="width: 150px">
                <col style="width: 120px">
                <col style="width: 120px">
                <col style="width: 120px">
                <col style="width: 130px">
                <col style="width: 140px">
                <col style="width: 140px">
                <col style="width: 92px">
                <col style="width: 190px">
              </colgroup>
              <thead>
                <tr>
                  <th>{{ columnLabel('supplier_username') }}</th>
                  <th>{{ columnLabel('brand') }}</th>
                  <th>{{ columnLabel('code') }}</th>
                  <th>{{ columnLabel('new_code') }}</th>
                  <th>{{ columnLabel('craft_material') }}</th>
                  <th>{{ columnLabel('spec_model') }}</th>
                  <th>{{ columnLabel('common_model') }}</th>
                  <th>{{ columnLabel('purchase_price') }}</th>
                  <th>{{ columnLabel('price_valid_until') }}</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="item in state.quoteItems" :key="item.id">
                  <td>{{ item.supplier_username || item.supplierUsername }}</td>
                  <td>{{ item.brand }}</td>
                  <td>{{ item.code }}</td>
                  <td>{{ item.new_code || item.newCode }}</td>
                  <td>{{ item.craft_material || item.craftMaterial }}</td>
                  <td>{{ item.spec_model || item.specModel }}</td>
                  <td>{{ item.common_model || item.commonModel }}</td>
                  <td><input v-model="item.purchase_price" :placeholder="columnLabel('purchase_price')"></td>
                  <td><input v-model="item.price_valid_until" type="date" :placeholder="columnLabel('price_valid_until')"></td>
                </tr>
              </tbody>
            </table>
          </div>
          <div class="modal-body form-grid">
            <button class="primary" @click="saveAdminQuoteItems">保存报价</button>
          </div>
        </div>

        <div v-if="state.modal === 'usePrice'" class="modal-body">
          <div class="form-grid">
            <input v-model="state.pricingQuote.salePrice" placeholder="销售价">
            <input v-model="state.pricingQuote.priceValidUntil" type="date" placeholder="价格有效期限">
          </div>
          <div class="table-wrap compact price-option-table">
            <table>
              <thead>
                <tr><th>{{ '\u7269\u6599\u7f16\u7801' }}</th><th>{{ '\u6765\u6e90' }}</th><th>{{ '\u4f9b\u5e94\u5546' }}</th><th>{{ '\u4ef7\u683c' }}</th><th>{{ '\u6709\u6548\u671f' }}</th><th>{{ '\u64cd\u4f5c' }}</th></tr>
              </thead>
              <tbody>
                <tr v-for="option in state.pricingQuote.options" :key="option.quoteId">
                  <td>{{ state.pricingQuote.code }}</td>
                  <td>{{ option.source }}</td>
                  <td>{{ option.supplierUsername }}</td>
                  <td><input v-model="option.purchasePrice" :placeholder="'\u53c2\u8003\u4ef7\u683c'"></td>
                  <td>{{ option.priceValidUntil || '-' }}</td>
                  <td class="row-actions action-cell">
                    <button class="primary" @click="useSupplierQuote(option)">{{ '\u91c7\u7528\u4ef7\u683c' }}</button>
                  </td>
                </tr>
                <tr v-if="!state.pricingQuote.options?.length">
                  <td colspan="6">{{ '\u6682\u65e0\u53ef\u91c7\u7528\u4ef7\u683c' }}</td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>

        <div v-if="state.modal === 'priceTrend'" class="modal-body price-trend-modal">
          <div class="table-wrap compact">
            <table>
              <thead>
                <tr>
                  <th>鍝佺墝</th>
                  <th>物料编码</th>
                  <th>工艺/材质</th>
                  <th>规格型号</th>
                  <th v-for="field in priceTrendOrderFields" :key="field[0]" class="price-trend-cell">{{ field[1] }}</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="row in state.priceTrendRows" :key="row.id">
                  <td>{{ row.brand }}</td>
                  <td>{{ row.code }}</td>
                  <td>{{ row.craft_material || row.craftMaterial }}</td>
                  <td>{{ row.spec_model || row.specModel }}</td>
                  <td v-for="field in priceTrendOrderFields" :key="field[0]" class="price-trend-cell">{{ formatCell(valueOf(row, field[0])) }}</td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>

        <div v-if="state.modal === 'orderItemPrice'" class="modal-body form-grid">
          <input :value="state.priceItem.code" disabled>
          <input v-model="state.priceItem.purchasePrice" :placeholder="'\u4f9b\u5e94\u4ef7'">
          <input v-model="state.priceItem.salePrice" :placeholder="'\u9500\u552e\u4ef7'">
          <button class="primary" @click="saveOrderItemPrice">{{ '\u4fdd\u5b58\u4ef7\u683c' }}</button>
        </div>


        <div v-if="state.modal === 'quoteImport'" class="modal-body form-grid">
          <input type="file" accept=".xlsx" @change="state.importFile = $event.target.files[0]">
          <button class="primary" @click="importAdminQuotePrices">{{ '\u4e0a\u4f20\u5e76\u586b\u4ef7' }}</button>
        </div>

        <div v-if="state.modal === 'import'" class="modal-body form-grid">
          <input type="file" accept=".xlsx" @change="state.importFile = $event.target.files[0]">
          <button class="primary" @click="importTable">{{ '\u4e0a\u4f20\u5e76\u5bfc\u5165' }}</button>
        </div>

        <div v-if="state.modal === 'password'" class="modal-body form-grid">
          <input v-model="state.passwordForm.password" type="password" :placeholder="'\u65b0\u5bc6\u7801'">
          <input v-model="state.passwordForm.confirmPassword" type="password" :placeholder="'\u518d\u6b21\u8f93\u5165\u65b0\u5bc6\u7801'">
          <button class="primary" @click="changePassword">{{ '\u786e\u8ba4\u4fee\u6539' }}</button>
        </div>
      </section>
    </div>
  </main>
</template>






