<template>
  <div class="app-container industry-page">
    <div class="hero">
      <div class="hero-main">
        <div class="hero-tag">TALENT · INDUSTRY MAP</div>
        <h1>行业编码对照表</h1>
        <p>BOSS 直聘行业分类体系 · 一级 / 二级编码与职位列表入口</p>
        <div class="hero-meta">
          <span>数据来源：数据库 talent_industry_code</span>
          <span class="dot" />
          <span>更新于 {{ stats.updatedAt || '-' }}</span>
        </div>
      </div>
      <div class="hero-stats">
        <div class="stat">
          <div class="num">{{ stats.total || 0 }}</div>
          <div class="label">行业总数</div>
        </div>
        <div class="stat">
          <div class="num">{{ stats.level1Count || 0 }}</div>
          <div class="label">一级行业</div>
        </div>
        <div class="stat">
          <div class="num">{{ stats.level2Count || 0 }}</div>
          <div class="label">二级行业</div>
        </div>
      </div>
    </div>

    <div class="body-grid">
      <el-card shadow="never" class="side-card">
        <div class="side-head">
          <span class="side-title">一级行业</span>
          <span class="side-count">{{ parents.length }}</span>
        </div>
        <div class="parent-list">
          <div
            class="parent-item"
            :class="{ active: activeParent === '' }"
            @click="activeParent = ''"
          >
            <span class="parent-dot all" />
            <span class="parent-name">全部行业</span>
            <span class="parent-badge">{{ rows.length }}</span>
          </div>
          <div
            v-for="item in parents"
            :key="item.code"
            class="parent-item"
            :class="{ active: activeParent === item.code }"
            @click="activeParent = item.code"
          >
            <span class="parent-dot" />
            <span class="parent-name">{{ item.name }}</span>
            <span class="parent-badge">{{ item.children }}</span>
          </div>
        </div>
      </el-card>

      <el-card shadow="never" class="main-card">
        <div class="toolbar">
          <el-input
            v-model="keyword"
            class="search-input"
            placeholder="搜索行业名称 / 编码 / 上级行业"
            clearable
          />
          <el-radio-group v-model="level" size="default" class="level-group">
            <el-radio-button value="">全部层级</el-radio-button>
            <el-radio-button value="一级">一级</el-radio-button>
            <el-radio-button value="二级">二级</el-radio-button>
          </el-radio-group>
          <div class="result-tip">共 {{ filteredRows.length }} 条</div>
        </div>

        <el-table
          v-loading="loading"
          :data="pagedRows"
          class="industry-table"
          row-key="code"
          :header-cell-style="{ background: '#f6f9ff', color: '#32405c', fontWeight: 600 }"
        >
          <el-table-column prop="code" label="行业编码" width="120">
            <template #default="{ row }">
              <span class="code-chip">{{ row.code || '-' }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="name" label="行业名称" min-width="200">
            <template #default="{ row }">
              <span class="name-text">{{ row.name }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="level" label="层级" width="100">
            <template #default="{ row }">
              <span class="level-pill" :class="row.level === '一级' ? 'level-1' : 'level-2'">
                {{ row.level || '-' }}
              </span>
            </template>
          </el-table-column>
          <el-table-column prop="parentName" label="上级行业" min-width="150">
            <template #default="{ row }">
              <span class="parent-text">{{ row.parentName || '—' }}</span>
              <span v-if="row.parentCode" class="parent-code">{{ row.parentCode }}</span>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="120" align="right">
            <template #default="{ row }">
              <el-button link type="primary" @click="openPreview(row)">查看岗位</el-button>
            </template>
          </el-table-column>
          <template #empty>
            <el-empty description="没有匹配的行业" :image-size="80" />
          </template>
        </el-table>

        <el-pagination
          v-model:current-page="page"
          class="pager"
          background
          layout="total, prev, pager, next, jumper"
          :page-size="pageSize"
          :total="filteredRows.length"
        />
      </el-card>
    </div>

    <el-dialog
      v-model="previewVisible"
      :title="'爬取岗位预览 · ' + previewTitle"
      width="900px"
      append-to-body
    >
      <div class="preview-meta">
        <span>{{ previewMeta.fileName || '-' }}</span>
        <span class="dot" />
        <span>更新于 {{ previewMeta.updatedAt || '-' }}</span>
        <span class="dot" />
        <span>文件共 {{ previewMeta.total || 0 }} 条 · 本行业命中 {{ previewMeta.matched || 0 }} 条</span>
      </div>
      <el-table
        v-loading="previewLoading"
        :data="previewPaged"
        max-height="420"
        class="preview-table"
        :header-cell-style="{ background: '#f6f9ff', color: '#32405c', fontWeight: 600 }"
      >
        <el-table-column prop="jobTitle" label="职位名称" min-width="200" />
        <el-table-column prop="industryName" label="二级行业" width="160" />
        <el-table-column prop="description" label="职位描述" min-width="360" show-overflow-tooltip>
          <template #default="{ row }">
            <span class="desc-text">{{ row.description || '—' }}</span>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty description="该行业暂无已爬取的岗位" :image-size="70" />
        </template>
      </el-table>
      <el-pagination
        v-model:current-page="previewPage"
        class="preview-pager"
        background
        layout="total, prev, pager, next"
        :page-size="previewPageSize"
        :total="previewRows.length"
      />
      <template #footer>
        <el-button @click="previewVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { getIndustryCodes, getIndustryJobs } from '@/api/project/talent'

const loading = ref(false)
const rows = ref([])
const stats = ref({})
const keyword = ref('')
const level = ref('')
const activeParent = ref('')
const page = ref(1)
const pageSize = 10

const previewVisible = ref(false)
const previewLoading = ref(false)
const previewTitle = ref('')
const previewRows = ref([])
const previewMeta = ref({})
const previewPage = ref(1)
const previewPageSize = 8
const previewPaged = computed(() => {
  const start = (previewPage.value - 1) * previewPageSize
  return previewRows.value.slice(start, start + previewPageSize)
})

const parents = computed(() => {
  const list = rows.value.filter((item) => item.level === '一级')
  return list.map((item) => ({
    code: item.code,
    name: item.name,
    children: rows.value.filter((row) => row.parentCode === item.code).length
  }))
})

const filteredRows = computed(() => {
  const word = keyword.value.trim().toLowerCase()
  return rows.value.filter((row) => {
    if (level.value && row.level !== level.value) {
      return false
    }
    if (activeParent.value && row.parentCode !== activeParent.value && row.code !== activeParent.value) {
      return false
    }
    if (!word) {
      return true
    }
    return [row.name, row.code, row.parentName, row.parentCode]
      .filter(Boolean)
      .some((item) => String(item).toLowerCase().indexOf(word) >= 0)
  })
})

const pagedRows = computed(() => {
  const start = (page.value - 1) * pageSize
  return filteredRows.value.slice(start, start + pageSize)
})

watch([keyword, level, activeParent], () => {
  page.value = 1
})

async function openPreview(row) {
  previewVisible.value = true
  previewLoading.value = true
  previewTitle.value = row.name
  previewRows.value = []
  previewMeta.value = {}
  previewPage.value = 1
  try {
    const res = await getIndustryJobs({ code: row.code, name: row.name, level: row.level })
    previewRows.value = res.data?.rows || []
    previewMeta.value = res.data || {}
  } finally {
    previewLoading.value = false
  }
}

onMounted(async () => {
  loading.value = true
  try {
    const res = await getIndustryCodes()
    rows.value = res.data?.rows || []
    stats.value = res.data || {}
  } finally {
    loading.value = false
  }
})
</script>

<style scoped>
.industry-page {
  min-height: calc(100vh - 84px);
  background: #f5f7fb;
  padding: 18px 20px 24px;
}

.hero {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 24px;
  padding: 26px 32px;
  border-radius: 18px;
  overflow: hidden;
  color: #fff;
  background:
    radial-gradient(circle at 82% 18%, rgba(120, 180, 255, 0.35), transparent 46%),
    radial-gradient(circle at 12% 90%, rgba(56, 189, 248, 0.22), transparent 42%),
    linear-gradient(118deg, #0e2550 0%, #16418f 52%, #1d63d6 100%);
  box-shadow: 0 14px 34px rgba(17, 55, 128, 0.24);
}

.hero::after {
  content: '';
  position: absolute;
  right: -60px;
  top: -90px;
  width: 260px;
  height: 260px;
  border-radius: 50%;
  border: 1px solid rgba(255, 255, 255, 0.16);
  box-shadow: inset 0 0 60px rgba(255, 255, 255, 0.06);
}

.hero-tag {
  font-size: 11px;
  letter-spacing: 2.4px;
  color: rgba(198, 224, 255, 0.9);
  margin-bottom: 8px;
}

.hero h1 {
  margin: 0;
  font-size: 27px;
  font-weight: 700;
  letter-spacing: 0.5px;
}

.hero p {
  margin: 8px 0 0;
  font-size: 13px;
  color: rgba(222, 237, 255, 0.86);
}

.hero-meta {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-top: 16px;
  font-size: 12px;
  color: rgba(206, 227, 255, 0.78);
}

.hero-meta .dot {
  width: 4px;
  height: 4px;
  border-radius: 50%;
  background: rgba(180, 214, 255, 0.7);
}

.hero-stats {
  position: relative;
  z-index: 1;
  display: grid;
  grid-template-columns: repeat(3, 104px);
  gap: 12px;
}

.stat {
  padding: 14px 12px;
  border-radius: 12px;
  text-align: center;
  background: rgba(255, 255, 255, 0.12);
  border: 1px solid rgba(255, 255, 255, 0.18);
  backdrop-filter: blur(6px);
}

.stat .num {
  font-size: 24px;
  font-weight: 700;
  line-height: 1.1;
}

.stat .label {
  margin-top: 4px;
  font-size: 12px;
  color: rgba(219, 235, 255, 0.85);
}

.body-grid {
  display: grid;
  grid-template-columns: 252px minmax(0, 1fr);
  gap: 16px;
  margin-top: 16px;
}

.side-card {
  border: none;
  border-radius: 14px;
  box-shadow: 0 6px 20px rgba(23, 43, 84, 0.06);
}

.side-card :deep(.el-card__body) {
  padding: 16px 12px;
}

.side-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 8px 12px;
  border-bottom: 1px solid #eef2f9;
}

.side-title {
  font-size: 14px;
  font-weight: 600;
  color: #1f2d4a;
}

.side-count {
  font-size: 12px;
  color: #7a8aa8;
}

.parent-list {
  display: flex;
  flex-direction: column;
  gap: 4px;
  margin-top: 10px;
  max-height: 620px;
  overflow-y: auto;
}

.parent-item {
  display: flex;
  align-items: center;
  gap: 9px;
  padding: 9px 10px;
  border-radius: 9px;
  cursor: pointer;
  transition: all 0.18s ease;
  border-left: 3px solid transparent;
}

.parent-item:hover {
  background: #f3f7ff;
}

.parent-item.active {
  background: linear-gradient(90deg, #e9f2ff 0%, #f6faff 100%);
  border-left-color: #2f6fed;
}

.parent-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #8db4f2;
  flex: none;
}

.parent-dot.all {
  background: #2f6fed;
}

.parent-name {
  flex: 1;
  font-size: 13px;
  color: #2c3a58;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.parent-item.active .parent-name {
  color: #1d4fbf;
  font-weight: 600;
}

.parent-badge {
  min-width: 28px;
  padding: 1px 7px;
  border-radius: 10px;
  text-align: center;
  font-size: 11px;
  color: #5b6d90;
  background: #edf2fb;
}

.parent-item.active .parent-badge {
  color: #1d4fbf;
  background: #dbe9ff;
}

.main-card {
  border: none;
  border-radius: 14px;
  box-shadow: 0 6px 20px rgba(23, 43, 84, 0.06);
}

.main-card :deep(.el-card__body) {
  padding: 18px 20px 16px;
}

.toolbar {
  display: flex;
  align-items: center;
  gap: 14px;
  flex-wrap: wrap;
  margin-bottom: 14px;
}

.search-input {
  width: 300px;
}

.level-group :deep(.el-radio-button__inner) {
  padding: 8px 16px;
  border-color: #e2e9f5;
  color: #4b5c7c;
}

.level-group :deep(.el-radio-button__original-radio:checked + .el-radio-button__inner) {
  background: #2f6fed;
  border-color: #2f6fed;
  box-shadow: -1px 0 0 0 #2f6fed;
}

.result-tip {
  margin-left: auto;
  font-size: 12px;
  color: #7a8aa8;
}

.industry-table :deep(.el-table__row) {
  transition: background 0.15s ease;
}

.industry-table :deep(.el-table__row:hover > td.el-table__cell) {
  background: #f7faff !important;
}

.code-chip {
  display: inline-block;
  padding: 2px 9px;
  border-radius: 8px;
  font-family: 'JetBrains Mono', Consolas, monospace;
  font-size: 12px;
  color: #2456c4;
  background: #eef4ff;
  border: 1px solid #dce8ff;
}

.name-text {
  font-weight: 600;
  color: #1f2d4a;
}

.level-pill {
  display: inline-block;
  padding: 2px 10px;
  border-radius: 10px;
  font-size: 12px;
}

.level-1 {
  color: #9a5b00;
  background: #fff4e0;
  border: 1px solid #ffe3b3;
}

.level-2 {
  color: #1d4fbf;
  background: #e9f2ff;
  border: 1px solid #d6e6ff;
}

.parent-text {
  color: #4c5c7a;
}

.parent-code {
  margin-left: 6px;
  font-size: 11px;
  color: #93a1bb;
}

.no-link {
  color: #b6c0d2;
}

.preview-meta {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 12px;
  font-size: 12px;
  color: #7a8aa8;
}

.preview-meta .dot {
  width: 4px;
  height: 4px;
  border-radius: 50%;
  background: #c3cee0;
}

.preview-table :deep(.el-table__row) {
  cursor: default;
}

.desc-text {
  color: #4c5c7a;
}

.preview-pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 12px;
}

.pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 14px;
}

@media (max-width: 1100px) {
  .hero {
    flex-direction: column;
    align-items: flex-start;
  }
  .hero-stats {
    grid-template-columns: repeat(3, minmax(90px, 1fr));
    width: 100%;
  }
  .body-grid {
    grid-template-columns: 1fr;
  }
  .parent-list {
    max-height: 240px;
  }
}
</style>
