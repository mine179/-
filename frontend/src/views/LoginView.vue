<script setup>
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import request from '../request'

const router = useRouter()
const busy = ref(false)
const message = ref('')
const form = reactive({ username: '', password: '' })

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
      <p>{{ '\u4f9b\u5e94\u5546\u5230\u5ba2\u6237' }}</p>
      <h1>{{ '\u4ea7\u54c1\u7f16\u7801\u3001\u8ba2\u5355\u548c\u62a5\u4ef7\u7ba1\u7406' }}</h1>
    </div>

    <div class="login-panel">
      <p v-if="message" class="notice">{{ message }}</p>
      <form class="form" @submit.prevent="login">
        <h2>{{ '\u767b\u5f55' }}</h2>
        <input v-model="form.username" :placeholder="'\u8d26\u53f7'" required>
        <input v-model="form.password" :placeholder="'\u5bc6\u7801'" type="password" required>
        <button class="primary" :disabled="busy">{{ busy ? '\u767b\u5f55\u4e2d...' : '\u767b\u5f55' }}</button>
      </form>
    </div>
  </main>
</template>
