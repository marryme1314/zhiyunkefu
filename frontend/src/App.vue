<template>
  <router-view v-slot="{ Component, route }">
    <Transition :name="transitionName(route)" mode="out-in">
      <component :is="Component" :key="routeKey(route)" />
    </Transition>
  </router-view>
  <ConfirmDialog />
</template>

<script setup lang="ts">
import type { RouteLocationNormalizedLoaded } from 'vue-router'
import ConfirmDialog from './components/ConfirmDialog.vue'

function routeKey(route: RouteLocationNormalizedLoaded) {
  return route.path === '/login' ? 'login' : 'shell'
}

function transitionName(route: RouteLocationNormalizedLoaded) {
  return route.path === '/login' ? 'fade' : 'rise'
}
</script>

<style>
.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.4s ease;
}
.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}

.rise-enter-active {
  transition: opacity 0.55s ease, transform 0.55s cubic-bezier(0.22, 1, 0.36, 1);
}
.rise-leave-active {
  transition: opacity 0.35s ease;
}
.rise-enter-from {
  opacity: 0;
  transform: translateY(14px) scale(0.985);
}
.rise-leave-to {
  opacity: 0;
}
</style>
