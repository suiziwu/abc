<template>
  <div class="app-container">
    <el-form ref="queryRef" :model="queryParams" :inline="true" v-show="showSearch" label-width="86px">
      <el-form-item label="职业名称" prop="jobName">
        <el-input
          v-model="queryParams.jobName"
          placeholder="请输入职业名称"
          clearable
          style="width: 190px"
          @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item label="职业编码" prop="jobCode">
        <el-input
          v-model="queryParams.jobCode"
          placeholder="如 4-04-05-01"
          clearable
          style="width: 160px"
          @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item label="产业链" prop="chainName">
        <el-input
          v-model="queryParams.chainName"
          placeholder="行业关键字"
          clearable
          style="width: 170px"
          @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item label="岗位群" prop="clusterName">
        <el-input
          v-model="queryParams.clusterName"
          placeholder="岗位群关键字"
          clearable
          style="width: 170px"
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
        <el-button
          type="primary"
          plain
          icon="Plus"
          @click="handleAdd"
          v-hasPermi="['project:chain:add']"
        >新增</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button
          type="danger"
          plain
          icon="Delete"
          :disabled="multiple"
          @click="handleDelete"
          v-hasPermi="['project:chain:remove']"
        >删除</el-button>
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList" />
    </el-row>

    <el-table v-loading="loading" :data="chainList" @selection-change="handleSelectionChange">
      <el-table-column type="selection" width="55" align="center" />
      <el-table-column label="职业名称" prop="jobName" min-width="170" show-overflow-tooltip />
      <el-table-column label="职业编码" prop="jobCode" width="110" />
      <el-table-column label="产业链 / 行业" prop="chainName" min-width="180" show-overflow-tooltip />
      <el-table-column label="环节" prop="stageName" width="130" show-overflow-tooltip />
      <el-table-column label="岗位群" prop="clusterName" min-width="180" show-overflow-tooltip />
      <el-table-column label="岗位群编码" prop="clusterCode" width="110" />
      <el-table-column label="数据来源" prop="source" min-width="200" show-overflow-tooltip />
      <el-table-column label="操作" width="120" align="center" fixed="right">
        <template #default="scope">
          <el-button
            link
            type="primary"
            @click="handleUpdate(scope.row)"
            v-hasPermi="['project:chain:edit']"
          >修改</el-button>
          <el-button
            link
            type="danger"
            @click="handleDelete(scope.row)"
            v-hasPermi="['project:chain:remove']"
          >删除</el-button>
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

    <el-dialog :title="title" v-model="open" width="640px" append-to-body>
      <el-form ref="chainRef" :model="form" :rules="rules" label-width="104px">
        <el-form-item label="职业名称" prop="jobName">
          <el-input v-model="form.jobName" placeholder="须与图谱中的岗位名称一致" />
        </el-form-item>
        <el-form-item label="职业编码" prop="jobCode">
          <el-input v-model="form.jobCode" placeholder="如 4-04-05-01" />
        </el-form-item>
        <el-form-item label="产业链/行业" prop="chainName">
          <el-input v-model="form.chainName" placeholder="如 信息传输、软件和信息技术服务业" />
        </el-form-item>
        <el-form-item label="行业编码" prop="chainCode">
          <el-input v-model="form.chainCode" placeholder="如 I / 65" />
        </el-form-item>
        <el-form-item label="产业链说明" prop="chainDesc">
          <el-input v-model="form.chainDesc" type="textarea" :rows="2" placeholder="取自官方分类原文" />
        </el-form-item>
        <el-form-item label="产业链环节" prop="stageName">
          <el-input v-model="form.stageName" placeholder="如 软件开发" />
        </el-form-item>
        <el-form-item label="环节说明" prop="stageDesc">
          <el-input v-model="form.stageDesc" type="textarea" :rows="2" />
        </el-form-item>
        <el-form-item label="岗位群" prop="clusterName">
          <el-input v-model="form.clusterName" placeholder="职业分类大典小类名称" />
        </el-form-item>
        <el-form-item label="岗位群编码" prop="clusterCode">
          <el-input v-model="form.clusterCode" placeholder="如 4-04-05" />
        </el-form-item>
        <el-form-item label="岗位群说明" prop="clusterDesc">
          <el-input v-model="form.clusterDesc" type="textarea" :rows="2" />
        </el-form-item>
        <el-form-item label="数据来源" prop="source">
          <el-input v-model="form.source" placeholder="官方标准名称" />
        </el-form-item>
        <el-form-item label="来源链接" prop="sourceUrl">
          <el-input v-model="form.sourceUrl" placeholder="https://" />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button @click="cancel">取 消</el-button>
          <el-button type="primary" @click="submitForm">确 定</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script>
import { listChain, getChain, addChain, updateChain, delChain } from '@/api/project/chain'

export default {
  name: 'JobIndustryChain',
  data() {
    return {
      loading: true,
      showSearch: true,
      ids: [],
      single: true,
      multiple: true,
      chainList: [],
      total: 0,
      title: '',
      open: false,
      queryParams: {
        pageNum: 1,
        pageSize: 10,
        jobName: undefined,
        jobCode: undefined,
        chainName: undefined,
        clusterName: undefined
      },
      form: {},
      rules: {
        jobName: [{ required: true, message: '职业名称不能为空', trigger: 'blur' }],
        chainName: [{ required: true, message: '产业链/行业不能为空', trigger: 'blur' }]
      }
    }
  },
  created() {
    this.getList()
  },
  methods: {
    getList() {
      this.loading = true
      listChain(this.queryParams)
        .then(res => {
          this.chainList = res.rows || []
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
    handleSelectionChange(selection) {
      this.ids = selection.map(item => item.id)
      this.single = selection.length !== 1
      this.multiple = !selection.length
    },
    handleAdd() {
      this.reset()
      this.open = true
      this.title = '新增岗位产业链'
    },
    handleUpdate(row) {
      this.reset()
      const id = row.id || this.ids[0]
      getChain(id).then(res => {
        this.form = res.data || {}
        this.open = true
        this.title = '修改岗位产业链'
      })
    },
    submitForm() {
      this.$refs.chainRef.validate(valid => {
        if (!valid) return
        const request = this.form.id ? updateChain(this.form) : addChain(this.form)
        request.then(() => {
          this.$modal.msgSuccess(this.form.id ? '修改成功' : '新增成功')
          this.open = false
          this.getList()
        })
      })
    },
    handleDelete(row) {
      const ids = row.id ? [row.id] : this.ids
      this.$modal.confirm('确认删除选中的 ' + ids.length + ' 条产业链记录？').then(() => {
        return delChain(ids.join(','))
      }).then(() => {
        this.$modal.msgSuccess('删除成功')
        this.getList()
      }).catch(() => {})
    },
    cancel() {
      this.open = false
      this.reset()
    },
    reset() {
      this.form = {
        id: undefined,
        jobName: '',
        jobCode: '',
        chainName: '',
        chainCode: '',
        chainDesc: '',
        stageName: '',
        stageDesc: '',
        clusterName: '',
        clusterCode: '',
        clusterDesc: '',
        source: '',
        sourceUrl: ''
      }
      if (this.$refs.chainRef) {
        this.$refs.chainRef.resetFields()
      }
    }
  }
}
</script>
