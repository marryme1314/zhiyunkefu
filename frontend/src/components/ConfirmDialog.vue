<template>
  <Teleport to="body">
    <Transition name="cfm">
      <div
        v-if="state.open"
        class="cfm-mask"
        role="dialog"
        aria-modal="true"
        @click.self="close(false)"
      >
        <div
          class="cfm-card"
          tabindex="-1"
          ref="card"
          @keydown.esc.prevent="close(false)"
        >
          <h3>{{ state.title }}</h3>
          <p class="cfm-msg">{{ state.message }}</p>
          <div class="cfm-actions">
            <button class="btn ghost" type="button" @click="close(false)">
              {{ state.cancelText }}
            </button>
            <button
              class="btn"
              :class="state.danger ? 'danger solid' : ''"
              type="button"
              @click="close(true)"
            >
              {{ state.confirmText }}
            </button>
          </div>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>

<script setup lang="ts">
import { nextTick, ref, watch } from 'vue'
import { finishConfirm, useConfirmState } from '../composables/confirm'

const state = useConfirmState()
const card = ref<HTMLElement | null>(null)

function close(ok: boolean) {
  finishConfirm(ok)
}

watch(
  () => state.open,
  async (open) => {
    if (!open) return
    await nextTick()
    card.value?.focus()
  }
)
</script>

<style scoped>
.cfm-mask {
  position: fixed;
  inset: 0;
  z-index: 2000;
  display: grid;
  place-items: center;
  padding: 24px;
  background: rgba(15, 23, 42, 0.45);
  backdrop-filter: blur(4px);
}
.cfm-card {
  width: min(420px, 100%);
  background: #fff;
  border-radius: 20px;
  padding: 22px 22px 18px;
  box-shadow: 0 24px 64px rgba(15, 23, 42, 0.22);
  outline: none;
}
.cfm-card h3 {
  margin: 0 0 10px;
  font-size: 17px;
  letter-spacing: -0.02em;
}
.cfm-msg {
  margin: 0;
  color: var(--muted);
  font-size: 14px;
  line-height: 1.65;
  white-space: pre-wrap;
}
.cfm-actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  margin-top: 20px;
}
.btn.danger.solid {
  background: var(--danger);
  color: #fff;
  border-color: var(--danger);
}
.btn.danger.solid:hover {
  background: #dc2626;
}
.cfm-enter-active,
.cfm-leave-active {
  transition: opacity 0.18s ease;
}
.cfm-enter-active .cfm-card,
.cfm-leave-active .cfm-card {
  transition: transform 0.18s ease, opacity 0.18s ease;
}
.cfm-enter-from,
.cfm-leave-to {
  opacity: 0;
}
.cfm-enter-from .cfm-card,
.cfm-leave-to .cfm-card {
  opacity: 0;
  transform: translateY(8px) scale(0.98);
}
</style>
