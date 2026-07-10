<script setup>
import { computed, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import request from '../request'

const router = useRouter()
const busy = ref(false)
const message = ref('')
const roles = [
  { key: 'SUPPLIER', label: '供应商', hint: '提交产品，填写供应价' },
  { key: 'CUSTOMER', label: '客户', hint: '下载模板，上传订单' },
  { key: 'ADMIN', label: '总后台', hint: '账号、审核、六表管理' }
]
const form = reactive({ role: 'SUPPLIER', username: '', password: '' })
const roleName = computed(() => roles.find(role => role.key === form.role)?.label || '')

async function login() {
  busy.value = true
  message.value = ''
  try {
    const session = await request.post('/login', form)
    localStorage.setItem('token', session.token)
    localStorage.setItem('user', JSON.stringify(session.user))
    if (session.user.role === 'ADMIN') router.push('/admin')
    if (session.user.role === 'SUPPLIER') router.push('/supplier')
    if (session.user.role === 'CUSTOMER') router.push('/customer')
  } catch (error) {
    message.value = error.message
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <main class="login-screen">
    <div class="brand">
      <p>供应商到客户</p>
      <h1>产品编码、订单和报价管理</h1>
    </div>

    <div class="login-panel">
      <div class="role-grid">
        <button
          v-for="role in roles"
          :key="role.key"
          class="role-card"
          :class="{ active: form.role === role.key }"
          @click="form.role = role.key"
        >
          <strong>{{ role.label }}</strong>
          <span>{{ role.hint }}</span>
        </button>
      </div>

      <p v-if="message" class="notice">{{ message }}</p>
      <form class="form" @submit.prevent="login">
        <h2>{{ roleName }}登录</h2>
        <input v-model="form.username" placeholder="账号" required>
        <input v-model="form.password" placeholder="密码" type="password" required>
        <button class="primary" :disabled="busy">登录</button>
      </form>
    </div>
  </main>
</template>
