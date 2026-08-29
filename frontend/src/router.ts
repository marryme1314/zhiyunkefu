import { createRouter, createWebHistory } from 'vue-router'
import LoginView from './views/LoginView.vue'
import AppShell from './views/AppShell.vue'
import ChatView from './views/ChatView.vue'
import KnowledgeView from './views/KnowledgeView.vue'
import FeedbackView from './views/FeedbackView.vue'
import AdminDashboardView from './views/AdminDashboardView.vue'

export function getRole(): string {
  return localStorage.getItem('role') || 'USER'
}

export function isAdmin(): boolean {
  return getRole() === 'ADMIN'
}

export const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/login', component: LoginView },
    {
      path: '/',
      component: AppShell,
      children: [
        { path: '', redirect: '/chat' },
        { path: 'chat', component: ChatView },
        { path: 'admin', component: AdminDashboardView, meta: { admin: true } },
        { path: 'knowledge', component: KnowledgeView, meta: { admin: true } },
        { path: 'feedback', component: FeedbackView, meta: { admin: true } }
      ]
    }
  ]
})

router.beforeEach((to) => {
  const token = localStorage.getItem('token')
  if (to.path !== '/login' && !token) {
    return '/login'
  }
  if (to.path === '/login' && token) {
    return isAdmin() ? '/admin' : '/chat'
  }
  if (to.meta.admin && !isAdmin()) {
    return '/chat'
  }
  return true
})
