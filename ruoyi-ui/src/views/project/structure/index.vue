<template>
  <div class="app-container">
    <el-form ref="queryRef" :model="queryParams" :inline="true" v-show="showSearch" label-width="80px">
      <el-form-item label="岗位名称" prop="jobName">
        <el-input
          v-model="queryParams.jobName"
          placeholder="请输入岗位名称"
          clearable
          style="width: 200px"
          @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
        <el-button icon="Refresh" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button type="primary" plain icon="Refresh" @click="getList">刷新</el-button>
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList" />
    </el-row>

    <el-table v-loading="loading" :data="graphList">
      <el-table-column label="岗位" prop="jobName" min-width="180" show-overflow-tooltip />
      <el-table-column label="职业编码" prop="jobCode" width="112" align="center" />
      <el-table-column label="等级" prop="level" width="110" align="center" />
      <el-table-column label="模式" width="72" align="center">
        <template #default="scope">
          <el-tag size="small" :type="scope.row.mode === 'kb' ? 'success' : 'warning'">
            {{ scope.row.mode === 'kb' ? 'KB' : 'LLM' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="审核" width="80" align="center">
        <template #default="scope">
          <el-tag size="small" :type="scope.row.reviewStatus === 'approved' ? 'success' : 'danger'">
            {{ scope.row.reviewStatus === 'approved' ? '通过' : '待审' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="Schema" prop="schemaVersion" width="90" align="center" />
      <el-table-column label="创建时间" prop="createTime" width="160" align="center" />
      <el-table-column label="操作" width="200" align="center" fixed="right">
        <template #default="scope">
          <el-button link type="primary" @click="handlePreview(scope.row)">预览</el-button>
          <el-button
            v-if="scope.row.reviewStatus !== 'approved'"
            link
            type="success"
            @click="handleReview(scope.row, 'approved')"
          >通过</el-button>
          <el-button
            v-else
            link
            type="warning"
            @click="handleReview(scope.row, 'pending')"
          >撤销</el-button>
          <el-button link type="danger" @click="handleDelete(scope.row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <pagination
      v-show="total > 0"
      :total="total"
      v-model:page="queryParams.pageNum"
      v-model:limit="queryParams.pageSize"
      @pagination="getList"
    />
  </div>
</template>

<script>
import {
  listCompetencyGraph,
  delCompetencyGraph,
  reviewCompetencyGraph
} from '@/api/project/competency'

export default {
  name: 'JobCompetencyStructure',
  data() {
    return {
      loading: true,
      showSearch: true,
      graphList: [],
      total: 0,
      queryParams: { pageNum: 1, pageSize: 10, jobName: undefined }
    }
  },
  created() {
    this.getList()
  },
  methods: {
    getList() {
      this.loading = true
      listCompetencyGraph(this.queryParams)
        .then(res => {
          this.graphList = res.rows || []
          this.total = res.total || 0
        })
        .finally(() => {
          this.loading = false
        })
    },
    handleQuery() {
      this.queryParams.pageNum = 1
      this.getList()
    },
    resetQuery() {
      this.resetForm('queryRef')
      this.handleQuery()
    },
    handlePreview(row) {
      this.$router.push({ path: '/competencyAdmin/competency', query: { id: row.id } })
    },
    handleReview(row, status) {
      const label = status === 'approved' ? '通过' : '撤销审核'
      this.$modal.confirm('确认' + label + '「' + row.jobName + '」这条结构视图？').then(() => {
        return reviewCompetencyGraph(row.id, status)
      }).then(() => {
        this.$modal.msgSuccess(label + '成功')
        this.getList()
      }).catch(() => {})
    },
    handleDelete(row) {
      this.$modal.confirm('确认删除「' + row.jobName + '」的这条结构视图？').then(() => {
        return delCompetencyGraph(row.id)
      }).then(() => {
        this.$modal.msgSuccess('删除成功')
        if (this.graphList.length === 1 && this.queryParams.pageNum > 1) {
          this.queryParams.pageNum -= 1
        }
        this.getList()
      }).catch(() => {})
    }
  }
}
</script>
