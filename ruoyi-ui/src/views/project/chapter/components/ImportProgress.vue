﻿<template>
  <transition name="slide-fade">
    <div v-if="task" class="import-progress">
      <div class="ip-head">
        <div class="ip-title">
          <el-icon class="ip-icon"><Loading v-if="running" /><CircleCheck v-else-if="succeeded" /><CircleClose v-else /></el-icon>
          <span>{{ succeeded ? '解析完成' : (failed ? '解析失败' : '正在解析') }}</span>
        </div>
        <el-button link size="small" :icon="Close" @click="emit('close')" />
      </div>

      <el-progress
        :percentage="Number(task.progress) || 0"
        :status="failed ? 'exception' : (succeeded ? 'success' : undefined)"
        :stroke-width="10"
      />

      <div class="ip-stage">{{ stageLabel }}</div>
      <div class="ip-message">{{ task.message || '正在处理…' }}</div>

      <div class="ip-stats">
        <el-tag size="small" effect="plain">逐字稿 {{ task.transcriptCount || 0 }}</el-tag>
        <el-tag size="small" effect="plain">知识点 {{ task.pointCount || 0 }}</el-tag>
        <el-tag size="small" effect="plain">题目 {{ task.questionCount || 0 }}</el-tag>
        <el-tag size="small" effect="plain">知识库 {{ task.kbCount || 0 }}</el-tag>
      </div>
    </div>
  </transition>
</template>

<script setup name="ImportProgress">
import { Close, Loading, CircleCheck, CircleClose } from '@element-plus/icons-vue'

const props = defineProps({
  /** 视频导入任务（含 progress / stage / status / message / 统计字段） */
  task: { type: Object, default: null },
  /** 当前阶段的中文说明 */
  stageLabel: { type: String, default: '处理中' }
})
const emit = defineEmits(['close'])

const running = computed(() => props.task && props.task.status !== '2' && props.task.status !== '3')
const succeeded = computed(() => props.task?.status === '2')
const failed = computed(() => props.task?.status === '3')
</script>

<style scoped>
.import-progress {
  position: fixed;
  right: 24px;
  bottom: 24px;
  z-index: 3000;
  width: 340px;
  padding: 14px 16px 12px;
  border-radius: 12px;
  background: #ffffff;
  border: 1px solid #e6ecf5;
  box-shadow: 0 10px 30px rgba(15, 23, 42, 0.14);
}

.ip-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
}

.ip-title {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 14px;
  font-weight: 600;
  color: #1f2937;
}

.ip-icon {
  font-size: 16px;
  color: #0d9488;
}

.ip-stage {
  margin-top: 10px;
  font-size: 13px;
  font-weight: 600;
  color: #0f172a;
}

.ip-message {
  margin-top: 4px;
  font-size: 12px;
  line-height: 1.6;
  color: #909399;
  word-break: break-all;
  max-height: 44px;
  overflow: hidden;
}

.ip-stats {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 10px;
}

/* 进入/离开动画 */
.slide-fade-enter-active,
.slide-fade-leave-active {
  transition: all 0.25s ease;
}

.slide-fade-enter-from,
.slide-fade-leave-to {
  opacity: 0;
  transform: translateY(12px);
}
</style>
