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
const supplierUsername = computed(() => route.params.supplierUsername)

const columns = [
  'id', 'order_no', 'customer_username', 'supplier_username', 'brand', 'code', 'new_code',
  'color', 'category', 'craft_material', 'spec_model', 'common_model', 'size_value',
  'resolution', 'model_remark', 'purchase_price', 'status', 'updated_at'
]
const state = reactive({ items: [], editItem: {}, modal: '', page: 1, pageSize: 15, columnWidths: {} })
const pageTotal = computed(() => Math.max(1, Math.ceil(state.items.length / state.pageSize)))
const pagedItems = computed(() => {
  const start = (state.page - 1) * state.pageSize
  return state.items.slice(start, start + state.pageSize)
})
const dataColumnWidths = computed(() => defaultColumnWidths(columns))

function toast(text) {
  message.value = text
  setTimeout(() => { if (message.value === text) message.value = '' }, 3000)
}

async function load() {
  state.items = await request.get(`/admin/quote-orders/${orderNo.value}/items`, {
    params: { supplierUsername: supplierUsername.value }
  })
  if (state.page > pageTotal.value) state.page = pageTotal.value
}

function backToQuotes() {
  router.push({ path: '/admin', query: { tab: 'quotes' } })
}

function openPrice(item) {
  state.editItem = { ...item }
  state.modal = 'price'
}

async function savePrice() {
  if (!state.editItem.purchase_price) {
    toast('请填写供应价')
    return
  }
  await request.put(`/admin/quotes/${state.editItem.id}`, {
    purchasePrice: state.editItem.purchase_price
  })
  state.modal = ''
  await load()
  toast('报价已保存')
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

function headerStyle(key) {
  return columnStyle(state.columnWidths, 'adminQuoteDetail', key, dataColumnWidths.value[key])
}

function resizeHeader(key, event) {
  startColumnResize(state.columnWidths, 'adminQuoteDetail', columns, key, event)
}

function statusClass(status) {
  const value = String(status || '').toUpperCase()
  if (value === 'WAIT_SUPPLIER_PRICE') return 'status-submitted'
  if (value === 'SUPPLIER_PRICED') return 'status-linked'
  return ''
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
        <button @click="backToQuotes">返回</button>
        <strong>{{ orderNo }}</strong>
        <span>{{ supplierUsername }}</span>
      </div>
      <div class="topbar-actions">
        <span>{{ user.username }}</span>
        <button @click="logout">退出</button>
      </div>
    </header>

    <p v-if="message" class="notice">{{ message }}</p>

    <section class="panel">
      <div class="panel-title-row">
        <h2>报价产品明细</h2>
      </div>
      <div class="table-wrap order-detail-table">
        <table>
          <colgroup>
            <col v-for="key in columns" :key="key" :style="headerStyle(key)">
            <col class="action-col">
          </colgroup>
          <thead>
            <tr>
              <th v-for="key in columns" :key="key" :style="headerStyle(key)" class="resizable-th">
                {{ columnLabel(key) }}
                <span class="col-resizer" @mousedown="resizeHeader(key, $event)"></span>
              </th>
              <th class="action-cell">操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="item in pagedItems" :key="item.id">
              <td v-for="key in columns" :key="key">
                <span :class="key === 'status' ? statusClass(item[key]) : ''">{{ formatCell(item[key]) }}</span>
              </td>
              <td class="row-actions action-cell">
                <button class="primary" @click="openPrice(item)">报价</button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <div class="pager">
        <span>共 {{ state.items.length }} 条</span>
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

    <div v-if="state.modal === 'price'" class="modal-mask" @click.self="state.modal = ''">
      <section class="modal">
        <header class="modal-head"><h3>填写供应价</h3><button @click="state.modal = ''">关闭</button></header>
        <div class="modal-body form-grid">
          <input :value="state.editItem.code" disabled>
          <input v-model="state.editItem.purchase_price" placeholder="供应价">
          <button class="primary" @click="savePrice">保存报价</button>
        </div>
      </section>
    </div>
  </main>
</template>
