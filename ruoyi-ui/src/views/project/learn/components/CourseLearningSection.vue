<template>
  <section class="course-learning-section" :class="{ 'is-embedded': embedded }">
    <div class="course-section-head">
      <div class="course-section-title-wrap">
        <span class="course-section-icon">
          <el-icon><Reading /></el-icon>
        </span>
        <div>
          <h2 class="course-section-title">{{ title }}</h2>
          <p v-if="subtitle" class="course-section-subtitle">{{ subtitle }}</p>
        </div>
      </div>
      <span v-if="courses.length > 0" class="course-section-count">
        共 {{ courses.length }} 门课程
      </span>
    </div>

    <div v-if="courses.length > 0" class="course-section-grid">
      <CourseLearningCard
        v-for="(course, index) in courses"
        :key="course.id ?? index"
        :course="course"
        :compact="compact"
        :theme-index="course.themeIndex ?? index"
        @open="$emit('open', course)"
      />
    </div>

    <div v-else class="course-section-empty">
      <el-icon><Reading /></el-icon>
      <span>{{ emptyText }}</span>
    </div>
  </section>
</template>

<script setup>
import { Reading } from '@element-plus/icons-vue'
import CourseLearningCard from './CourseLearningCard.vue'

defineProps({
  title: {
    type: String,
    default: '课程学习'
  },
  subtitle: {
    type: String,
    default: ''
  },
  courses: {
    type: Array,
    default: () => []
  },
  emptyText: {
    type: String,
    default: '暂无课程数据'
  },
  embedded: {
    type: Boolean,
    default: false
  },
  compact: {
    type: Boolean,
    default: false
  }
})

defineEmits(['open'])
</script>

<style scoped>
.course-learning-section {
  margin-bottom: 20px;
  padding: 18px 20px 20px;
  background: #fff;
  border: 1px solid #e8ebf0;
  border-radius: 8px;
  box-shadow: 0 1px 4px rgba(31, 35, 41, 0.03);
}

.course-learning-section.is-embedded {
  margin-bottom: 0;
  padding: 18px 0 0;
  background: transparent;
  border: 0;
  border-top: 1px solid #eef0f3;
  border-radius: 0;
  box-shadow: none;
}

.course-section-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 14px;
}

.course-section-title-wrap {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
}

.course-section-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 34px;
  height: 34px;
  border-radius: 10px;
  color: #2f7cf6;
  background: #eef5ff;
}

.course-section-icon :deep(.el-icon) {
  font-size: 18px;
}

.course-section-title {
  margin: 0;
  color: #1f2329;
  font-size: 16px;
  font-weight: 600;
  line-height: 1.35;
}

.course-section-subtitle {
  margin: 3px 0 0;
  color: #9aa1ab;
  font-size: 12px;
  line-height: 1.4;
}

.course-section-count {
  flex: none;
  color: #9aa1ab;
  font-size: 12px;
}

.course-section-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(236px, 1fr));
  gap: 16px;
}

.course-section-empty {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  min-height: 96px;
  color: #a5a9b0;
  font-size: 13px;
  background: #fafbfc;
  border: 1px dashed #e5e8ed;
  border-radius: 8px;
}

.course-section-empty :deep(.el-icon) {
  font-size: 18px;
}

@media (max-width: 768px) {
  .course-learning-section {
    padding: 16px;
  }

  .course-learning-section.is-embedded {
    padding: 16px 0 0;
  }

  .course-section-grid {
    grid-template-columns: repeat(auto-fill, minmax(210px, 1fr));
    gap: 12px;
  }
}
</style>
