<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import request from '../request'
import { columnLabel, formatCell } from '../data/columnLabels'
import { columnStyle, defaultColumnWidths, startColumnResize } from '../utils/columnResize'

const route = useRoute()
const router = useRouter()
const user = JSON.parse(localStorage.getItem('user') || '{}')
const message = ref('')
const orderNo = computed(() => route.params.orderNo)
const hiddenColumns = ['serial_no', 'serialNo', 'code', 'newCode', 'new_code', 'purchase_price', 'purchasePrice']

const state = reactive({
  rows: [],
  page: 1,
  pageSize: 1000,
  globalSearch: '',
  columnFilters: {},
  filterMenu: '',
  filterSearch: '',
  filterMenuStyle: {},
  columnOrder: [],
  columnWidths: {},
  dragColumn: ''
})

const pageSizeOptions = [1000, 2000, 3000, 5000]

const baseColumns = computed(() => Object.keys(state.rows[0] || {}).filter(key => !hiddenColumns.includes(key)))
const columns = computed(() => {
  const known = state.columnOrder.filter(key => baseColumns.value.includes(key))
  const missing = baseColumns.value.filter(key => !known.includes(key))
  return [...known, ...missing]
})
const filteredRows = computed(() => state.rows.filter(row => rowMatches(row)))
const pageTotal = computed(() => Math.max(1, Math.ceil(filteredRows.value.length / state.pageSize)))
const pagedRows = computed(() => {
  const start = (state.page - 1) * state.pageSize
  return filteredRows.value.slice(start, start + state.pageSize)
})
const dataColumnWidths = computed(() => defaultColumnWidths(columns.value))

function toast(text) {
  message.value = text
  setTimeout(() => { if (message.value === text) message.value = '' }, 3000)
}

async function load() {
  state.rows = await request.get(`/customer/orders/${orderNo.value}/items`)
  if (state.page > pageTotal.value) state.page = pageTotal.value
}

async function cancelOrderItem(row) {
  if (!window.confirm('确定作废这个产品吗？')) return
  await request.put(`/customer/order-items/${row.id}/cancel`)
  await load()
  toast('产品已作废')
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

function columnChoices(key) {
  const search = state.filterSearch.trim().toLowerCase()
  return allColumnValues(key).filter(item => !search || item.value.toLowerCase().includes(search))
}

function allColumnValues(key) {
  const counts = new Map()
  state.rows.forEach(row => {
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
  state.columnOrder = next
  state.dragColumn = ''
}

function headerStyle(key) {
  return columnStyle(state.columnWidths, 'detail', key, dataColumnWidths.value[key])
}

function resizeHeader(key, event) {
  startColumnResize(state.columnWidths, 'detail', columns.value, key, event)
}

function resetColumns() {
  state.columnOrder = [...baseColumns.value]
  state.columnWidths.detail = {}
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

function snakeToCamel(value) {
  return value.replace(/_([a-z])/g, (_, letter) => letter.toUpperCase())
}

function backToOrders() {
  router.push('/customer')
}

function logout() {
  localStorage.clear()
  router.push('/')
}

onMounted(load)
</script>

<template>
  <main class="workspace">
    <header class="topbar">
      <div class="topbar-actions">
        <button @click="backToOrders">返回</button>
        <strong>{{ orderNo }}</strong>
        <span>订单产品明细</span>
      </div>
      <div class="topbar-actions">
        <span>{{ user.username }}</span>
        <button @click="logout">退出</button>
      </div>
    </header>

    <p v-if="message" class="notice">{{ message }}</p>

    <section class="panel">
      <div class="panel-title-row">
        <h2>产品明细</h2>
      </div>

      <div class="table-tools">
        <input v-model="state.globalSearch" placeholder="搜索当前表" @input="state.page = 1">
        <button @click="clearFilters">清除筛选</button>
        <button @click="resetColumns">恢复列顺序</button>
      </div>

      <div class="table-wrap order-detail-table">
        <table>
          <colgroup>
            <col v-for="key in columns" :key="key" :style="headerStyle(key)">
            <col class="action-col">
          </colgroup>
          <thead>
            <tr>
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
            <tr v-for="item in pagedRows" :key="item.id">
              <td v-for="key in columns" :key="key">{{ formatCell(valueOf(item, key)) }}</td>
              <td class="row-actions action-cell">
                <button class="danger" :disabled="item.status === 'CANCELLED'" @click="cancelOrderItem(item)">产品作废</button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <div class="pager">
        <span>共 {{ filteredRows.length }} 条</span>
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
  </main>
</template>
