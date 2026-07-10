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
const hiddenColumns = ['source_type', 'sourceType', 'serial_no', 'serialNo']
const state = reactive({ items: [], priceItem: {}, modal: '', page: 1, pageSize: 15, columnWidths: {} })
const orderNo = computed(() => route.params.orderNo)
const pageTotal = computed(() => Math.max(1, Math.ceil(state.items.length / state.pageSize)))
const pagedItems = computed(() => {
  const start = (state.page - 1) * state.pageSize
  return state.items.slice(start, start + state.pageSize)
})
const columns = computed(() => Object.keys(pagedItems.value[0] || state.items[0] || {}).filter(key => !hiddenColumns.includes(key)))
const dataColumnWidths = computed(() => defaultColumnWidths(columns.value))

function toast(text) {
  message.value = text
  setTimeout(() => {
    if (message.value === text) message.value = ''
  }, 3000)
}

async function load() {
  state.items = await request.get(`/admin/orders/${orderNo.value}/items`)
  if (state.page > pageTotal.value) {
    state.page = pageTotal.value
  }
}

async function cancelOrderItem(row) {
  if (!window.confirm('确定作废这个产品吗？')) return
  await request.put(`/admin/order-items/${row.id}/cancel`)
  await load()
  toast('产品已作废')
}

function openPrice(row) {
  state.priceItem = {
    id: row.id,
    code: row.code,
    purchasePrice: row.purchase_price ?? row.purchasePrice ?? '',
    salePrice: row.sale_price ?? row.salePrice ?? ''
  }
  state.modal = 'price'
}

async function savePrice() {
  if (!state.priceItem.purchasePrice && !state.priceItem.salePrice) {
    toast('请填写供应价或销售价')
    return
  }
  await request.put(`/admin/order-items/${state.priceItem.id}/prices`, {
    purchasePrice: state.priceItem.purchasePrice || null,
    salePrice: state.priceItem.salePrice || null
  })
  state.modal = ''
  await load()
  toast('价格已保存')
}

function backToOrders() {
  router.push({ path: '/admin', query: { tab: 'orders' } })
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
  return columnStyle(state.columnWidths, 'adminOrderDetail', key, dataColumnWidths.value[key])
}

function resizeHeader(key, event) {
  startColumnResize(state.columnWidths, 'adminOrderDetail', columns.value, key, event)
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
              <td v-for="key in columns" :key="key">{{ formatCell(item[key]) }}</td>
              <td class="row-actions action-cell">
                <button class="primary" @click="openPrice(item)">添加价格</button>
                <button class="danger" :disabled="item.status === 'CANCELLED'" @click="cancelOrderItem(item)">产品作废</button>
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
        <header class="modal-head"><h3>添加价格</h3><button @click="state.modal = ''">关闭</button></header>
        <div class="modal-body form-grid">
          <input :value="state.priceItem.code" disabled>
          <input v-model="state.priceItem.purchasePrice" placeholder="供应价">
          <input v-model="state.priceItem.salePrice" placeholder="销售价">
          <button class="primary" @click="savePrice">保存价格</button>
        </div>
      </section>
    </div>
  </main>
</template>
