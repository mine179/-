import axios from 'axios'

const request = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080/api',
  timeout: 30000
})

request.interceptors.request.use(config => {
  const token = localStorage.getItem('token')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

request.interceptors.response.use(
  response => {
    if (response.config.responseType === 'blob') {
      return response
    }

    const result = response.data
    if (result.code === 1 || result.code === 200) {
      return result.data
    }

    return Promise.reject(new Error(result.msg || '请求失败'))
  },
  error => {
    const message = error.response?.data?.msg || error.message || '网络异常'
    return Promise.reject(new Error(message))
  }
)

export default request
