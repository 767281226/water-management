<script setup lang="ts">
import { onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { docApi, LEVELS, REQUEST_STATUS, type DocVO, type RequestVO } from '@/api/doc'
import { useAuthStore } from '@/stores/auth'
import { fmtSize } from '@/utils/file'
import { fmtTime } from '@/utils/format'
import FileIcon from '@/components/FileIcon.vue'
import RequestSteps from './RequestSteps.vue'
import PreviewDialog from './PreviewDialog.vue'

const auth = useAuthStore()
const tab = ref<'todo' | 'done'>('todo')
const todo = ref<RequestVO[]>([])
const done = ref<RequestVO[]>([])
const doneTotal = ref(0)
const q = reactive({ page: 1, size: 10 })
const selected = ref<number[]>([])
const loading = ref(false)
const preview = ref<DocVO | null>(null)
const previewVisible = ref(false)

async function load() {
  loading.value = true
  try {
    if (tab.value === 'todo') {
      todo.value = await docApi.todo()
      selected.value = []
    } else {
      const r = await docApi.done(q.page, q.size)
      done.value = r.records
      doneTotal.value = r.total
    }
  } finally {
    loading.value = false
  }
}

async function approve(r: RequestVO) {
  await docApi.approve(r.id)
  ElMessage.success(r.secret && r.steps.length > 1 && r.steps[0].status === 'PENDING' ? '已通过，已转公司领导终审' : '已通过')
  load()
}

async function reject(r: RequestVO) {
  const { value } = await ElMessageBox.prompt('申请人可以看到驳回原因', '驳回申请', {
    inputPlaceholder: '如：用途不明确，请说明具体项目',
    inputValidator: (v) => (!!v && v.trim().length > 0) || '请填写驳回原因'
  })
  await docApi.reject(r.id, value)
  ElMessage.success('已驳回')
  load()
}

async function batch() {
  await ElMessageBox.confirm(`确定通过选中的 ${selected.value.length} 条申请吗？`, '批量通过', { type: 'warning' })
  const n = await docApi.batchApprove(selected.value)
  ElMessage.success(`已通过 ${n} 条申请`)
  load()
}

async function openPreview(docId: number) {
  preview.value = await docApi.detail(docId)
  previewVisible.value = true
}

watch(tab, load)
onMounted(load)
</script>

<template>
  <div class="page">
    <div class="page-head">
      <div>
        <h3>下载审批</h3>
        <p>{{ auth.user?.dataScope === 'ALL' ? '全公司的下载申请，以及涉密资料终审' : '本部门员工的下载申请' }}</p>
      </div>
      <el-button v-if="tab === 'todo' && selected.length" type="success" icon="Select" @click="batch">批量通过（{{ selected.length }}）</el-button>
    </div>
    <div class="card">
      <el-tabs v-model="tab">
        <el-tab-pane name="todo"><template #label>待审批 <el-badge v-if="todo.length" :value="todo.length" class="tb" /></template></el-tab-pane>
        <el-tab-pane label="我已处理" name="done" />
      </el-tabs>

      <el-checkbox-group v-model="selected" v-loading="loading" class="group">
        <div v-for="r in tab === 'todo' ? todo : done" :key="r.id" class="req">
          <div class="rh">
            <div class="who">
              <el-checkbox v-if="tab === 'todo'" :value="r.id" />
              <div class="avatar">{{ r.applicantName?.slice(-1) }}</div>
              <div><b>{{ r.applicantName }}</b><span class="muted small"> {{ r.applicantDept || '' }} · {{ fmtTime(r.createdAt) }}</span></div>
            </div>
            <div>
              <el-tag v-if="r.secret" type="danger" effect="plain" round>含涉密资料</el-tag>
              <el-tag :type="REQUEST_STATUS[r.status].type" round style="margin-left: 6px">{{ REQUEST_STATUS[r.status].label }}</el-tag>
            </div>
          </div>
          <div class="purpose"><span>用途：</span>{{ r.purpose }}<template v-if="r.projectName"> · 关联项目：{{ r.projectName }}</template></div>
          <div class="items">
            <div v-for="it in r.items" :key="it.docId" class="item">
              <FileIcon :ext="it.fileExt" :size="28" />
              <div class="im">
                <b>{{ it.code ? `${it.code} ` : '' }}{{ it.name }}</b>
                <span><el-tag v-if="it.level" :type="LEVELS[it.level].type" size="small" effect="plain">{{ LEVELS[it.level].label }}</el-tag> {{ fmtSize(it.fileSize) }}</span>
              </div>
              <el-button v-if="it.available" size="small" link type="primary" @click="openPreview(it.docId)">查看</el-button>
            </div>
          </div>
          <div class="rf">
            <RequestSteps :request="r" />
            <div v-if="r.actions.approve" class="ops">
              <el-button type="danger" plain @click="reject(r)">驳回</el-button>
              <el-button type="success" @click="approve(r)">通过</el-button>
            </div>
          </div>
        </div>
      </el-checkbox-group>
      <el-empty v-if="!loading && !(tab === 'todo' ? todo : done).length" :description="tab === 'todo' ? '没有待审批的申请' : '暂无记录'" />
      <div v-if="tab === 'done'" class="pager">
        <el-pagination v-model:current-page="q.page" :page-size="q.size" :total="doneTotal" layout="total, prev, pager, next" background @current-change="load" />
      </div>
    </div>
    <PreviewDialog v-model="previewVisible" :doc="preview" />
  </div>
</template>

<style scoped>
/* el-checkbox-group 默认 font-size: 0、line-height: 0（用于消除复选框间距），这里包裹整个列表，需要恢复 */
.group {
  display: block;
  font-size: 14px;
  line-height: 1.5;
}
.tb {
  margin-left: 4px;
}
.req {
  border: 1px solid #eef2f7;
  border-radius: 14px;
  padding: 16px;
  margin-bottom: 14px;
  transition: box-shadow 0.15s;
}
.req:hover {
  box-shadow: var(--hy-shadow);
}
.rh {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
.who {
  display: flex;
  align-items: center;
  gap: 10px;
}
.who .muted {
  margin-left: 6px;
}
.avatar {
  width: 34px;
  height: 34px;
  border-radius: 50%;
  background: var(--hy-gradient);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 600;
}
.purpose {
  margin: 12px 0;
  font-size: 14px;
}
.purpose span {
  color: var(--hy-text-3);
}
.items {
  background: #f8faff;
  border-radius: 10px;
  padding: 2px 12px;
  margin-bottom: 12px;
}
.item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 0;
}
.im {
  flex: 1;
  min-width: 0;
}
.im b {
  display: block;
  font-size: 13px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.im span {
  font-size: 12px;
  color: var(--hy-text-3);
}
.rf {
  display: flex;
  justify-content: space-between;
  align-items: flex-end;
  gap: 12px;
  flex-wrap: wrap;
}
.ops {
  display: flex;
  gap: 8px;
}
</style>
