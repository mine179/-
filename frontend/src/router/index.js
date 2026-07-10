import { createRouter, createWebHashHistory } from 'vue-router'
import LoginView from '../views/LoginView.vue'
import AdminView from '../views/AdminView.vue'
import AdminOrderDetailView from '../views/AdminOrderDetailView.vue'
import AdminQuoteDetailView from '../views/AdminQuoteDetailView.vue'
import SupplierView from '../views/SupplierView.vue'
import SupplierQuoteDetailView from '../views/SupplierQuoteDetailView.vue'
import CustomerView from '../views/CustomerView.vue'
import CustomerOrderDetailView from '../views/CustomerOrderDetailView.vue'

const routes = [
  { path: '/', name: 'login', component: LoginView },
  { path: '/admin', name: 'admin', component: AdminView },
  { path: '/admin/orders/:orderNo', name: 'admin-order-detail', component: AdminOrderDetailView },
  { path: '/admin/quotes/:orderNo/:supplierUsername', name: 'admin-quote-detail', component: AdminQuoteDetailView },
  { path: '/supplier', name: 'supplier', component: SupplierView },
  { path: '/supplier/quotes/:orderNo', name: 'supplier-quote-detail', component: SupplierQuoteDetailView },
  { path: '/customer', name: 'customer', component: CustomerView },
  { path: '/customer/orders/:orderNo', name: 'customer-order-detail', component: CustomerOrderDetailView }
]

const router = createRouter({
  history: createWebHashHistory(),
  routes
})

router.beforeEach((to) => {
  const user = JSON.parse(localStorage.getItem('user') || 'null')
  if (to.path !== '/' && !user) {
    return '/'
  }
})

export default router
