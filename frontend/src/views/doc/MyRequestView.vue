<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { docApi, downloadDoc, LEVELS, REQUEST_STATUS, type RequestVO } from '@/api/doc'
import { fmtSize } from '@/utils/file'
import { fmtTime } from '@/utils/format'
import FileIcon from '@/components/FileIcon.vue'
import RequestSteps from './RequestSteps.vue'

const router = useRouter()
const rows = ref<RequestVO[]>([])
const total = ref(0)
const loading = ref(false)
const q = reactive({ page: 1, size: 10 })

async function load() {
  loading.value = true
  try {
    const r = await docApi.myRequests(q.page, q.size)
    rows.value = r.records
    total.value = r.total
  } finally {
    loading.value = false
  }
}

async function cancel(r: RequestVO) {
  await ElMessageBox.confirm('确定撤回该下载申请吗？', '撤回申请', { type: 'warning' })
  await docApi.cancel(r.id)
  ElMessage.success('已撤回')
  load()
}

async function download(r: RequestVO, docId: number) {
  await downloadDoc(docId, r.id)
  setTimeout(load, 800)
}

onMounted(load)
</script>

<template>
  <div class="page">
    <div class="page-head">
      <div>
        <h3>我的下载申请</h3>
        <p>审批通过后在有效期内可以下载，每份资料限下载次数（见参数配置 › 资料设置）</p>
      </div>
      <el-button type="primary" icon="Search" @click="router.push('/doc/search')">去资料库</el-button>
    </div>

    <div v-loading="loading">
      <div v-for="r in rows" :key="r.id" class="card req">
        <div class="rh">
          <div>
            <el-tag :type="REQUEST_STATUS[r.status].type" effect="dark" round>{{ REQUEST_STATUS[r.status].label }}</el-tag>
            <el-tag v-if="r.secret" type="danger" effect="plain" round style="margin-left: 6px">含涉密资料</el-tag>
            <span class="muted small" style="margin-left: 8px">申请时间 {{ fmtTime(r.createdAt) }}</span>
          </div>
          <el-button v-if="r.actions.cancel" size="small" @click="cancel(r)">撤回</el-button>
        </div>
        <div class="purpose"><span>用途：</span>{{ r.purpose }}<template v-if="r.projectName"> · 关联项目：{{ r.projectName }}</template></div>
        <div class="items">
          <div v-for="it in r.items" :key="it.docId" class="item">
            <FileIcon :ext="it.fileExt" :size="30" />
            <div class="im">
              <b>{{ it.code ? `${it.code} ` : '' }}{{ it.name }}</b>
              <span>
                <el-tag v-if="it.level" :type="LEVELS[it.level].type" size="small" effect="plain">{{ LEVELS[it.level].label }}</el-tag>
                {{ fmtSize(it.fileSize) }}
                <template v-if="r.status === 'APPROVED'"> · 已下载 {{ it.downloadCount }} / {{ r.maxDownloads }} 次</template>
              </span>
            </div>
            <template v-if="r.actions.download">
              <el-button v-if="it.available && it.downloadCount < r.maxDownloads" type="success" size="small" icon="Download" @click="download(r, it.docId)">下载</el-button>
              <el-tag v-else type="info" size="small">{{ it.available ? '次数已用完' : '资料已下架' }}</el-tag>
            </template>
          </div>
        </div>
        <RequestSteps :request="r" />
        <div v-if="r.status === 'APPROVED'" class="expire" :class="{ over: !r.actions.download }">
          <el-icon><Clock /></el-icon>{{ r.actions.download ? `下载有效期至 ${fmtTime(r.expireAt)}` : `已过期（${fmtTime(r.expireAt)}）` }}
        </div>
      </div>
      <el-empty v-if="!loading && !rows.length" class="card" description="还没有下载申请" />
    </div>

    <div class="pager">
      <el-pagination v-model:current-page="q.page" :page-size="q.size" :total="total" layout="total, prev, pager, next" background @current-change="load" />
    </div>
  </div>
</template>

<style scoped>
.req {
  margin-bottom: 16px;
}
.rh {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
.purpose {
  margin: 12px 0;
  font-size: 14px;
}
.purpose span {
  color: var(--hy-text-3);
}
.items {
  border: 1px solid #eef2f7;
  border-radius: 12px;
  padding: 4px 12px;
  margin-bottom: 14px;
}
.item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 0;
  border-bottom: 1px dashed #eef2f7;
}
.item:last-child {
  border-bottom: none;
}
.im {
  flex: 1;
  min-width: 0;
}
.im b {
  display: block;
  font-size: 14px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.im span {
  font-size: 12px;
  color: var(--hy-text-3);
}
.expire {
  margin-top: 12px;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  color: #10b981;
  background: #e3f8f0;
  padding: 4px 12px;
  border-radius: 14px;
}
.expire.over {
  color: var(--hy-text-3);
  background: #f1f4f9;
}
</style>
