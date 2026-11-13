<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { docApi, downloadDoc, LEVELS, type CategoryNode, type DocLevel, type DocQuery, type DocVO } from '@/api/doc'
import { fmtSize } from '@/utils/file'
import { fmtTime } from '@/utils/format'
import FileIcon from '@/components/FileIcon.vue'
import PreviewDialog from './PreviewDialog.vue'
import ApplyDialog from './ApplyDialog.vue'

const router = useRouter()
const tree = ref<CategoryNode[]>([])
const rows = ref<DocVO[]>([])
const total = ref(0)
const loading = ref(false)
const query = reactive<DocQuery>({ keyword: '', categoryId: null, level: '', page: 1, size: 20 })
const selected = ref<DocVO[]>([])
const preview = ref<DocVO | null>(null)
const previewVisible = ref(false)
const applyDocs = ref<DocVO[]>([])
const applyVisible = ref(false)

const allCount = computed(() => tree.value.reduce((s, c) => s + c.docCount, 0))
const levels = Object.entries(LEVELS) as [DocLevel, (typeof LEVELS)[DocLevel]][]
const selectable = computed(() => selected.value.filter((d) => !d.access.canDownload && !d.access.pending && d.needApproval))
const currentTitle = computed(() => {
  for (const c of tree.value) {
    if (c.id === query.categoryId) return c.name
    const s = c.children.find((x) => x.id === query.categoryId)
    if (s) return `${c.name} / ${s.name}`
  }
  return '全部资料'
})

async function load() {
  loading.value = true
  try {
    const r = await docApi.search(query)
    rows.value = r.records
    total.value = r.total
    selected.value = []
  } finally {
    loading.value = false
  }
}

function search() {
  query.page = 1
  load()
}

function pick(id: number | null) {
  query.categoryId = id
  search()
}

function toggle(d: DocVO, on: boolean) {
  selected.value = on ? [...selected.value, d] : selected.value.filter((x) => x.id !== d.id)
}

function openPreview(d: DocVO) {
  preview.value = d
  previewVisible.value = true
}

function openApply(docs: DocVO[]) {
  applyDocs.value = docs
  previewVisible.value = false
  applyVisible.value = true
}

async function refreshTree() {
  tree.value = await docApi.categories()
}

onMounted(() => {
  load()
  refreshTree()
})
</script>

<template>
  <div class="page">
    <div class="page-head">
      <div>
        <h3>资料查询</h3>
        <p>规范、公司成果、行业资料统一查询；浏览和预览对全体员工开放，下载需要审批</p>
      </div>
      <div class="btns">
        <el-button v-if="selectable.length" type="primary" icon="Download" @click="openApply(selectable)">批量申请下载（{{ selectable.length }}）</el-button>
        <el-button v-perm="'doc:request'" icon="Tickets" @click="router.push('/doc/request')">我的下载申请</el-button>
      </div>
    </div>

    <div class="layout">
      <!-- 分类 -->
      <aside class="card cats">
        <div class="cat" :class="{ active: !query.categoryId }" @click="pick(null)">
          <el-icon><Files /></el-icon><span>全部资料</span><em>{{ allCount }}</em>
        </div>
        <div v-for="c in tree" :key="c.id">
          <div class="cat" :class="{ active: query.categoryId === c.id }" @click="pick(c.id)">
            <el-icon><Folder /></el-icon><span>{{ c.name }}</span><em>{{ c.docCount }}</em>
          </div>
          <div v-for="s in c.children" :key="s.id" class="cat sub" :class="{ active: query.categoryId === s.id }" @click="pick(s.id)">
            <span>{{ s.name }}</span><em>{{ s.docCount || '' }}</em>
          </div>
        </div>
      </aside>

      <section class="main">
        <!-- 手机端分类 -->
        <div class="chips">
          <span class="chip" :class="{ active: !query.categoryId }" @click="pick(null)">全部</span>
          <span v-for="c in tree" :key="c.id" class="chip" :class="{ active: query.categoryId === c.id }" @click="pick(c.id)">{{ c.name }}</span>
        </div>

        <div class="card">
          <div class="search-bar">
            <el-input v-model="query.keyword" size="large" placeholder="搜索资料名称 / 标准编号，如 SL 252、防洪标准" clearable @keyup.enter="search" @clear="search">
              <template #prefix><el-icon><Search /></el-icon></template>
              <template #append><el-button @click="search">搜索</el-button></template>
            </el-input>
            <el-select v-model="query.level" placeholder="全部密级" clearable size="large" style="width: 130px" @change="search">
              <el-option v-for="[k, v] in levels" :key="k" :label="v.label" :value="k" />
            </el-select>
          </div>
          <div class="result-head muted small">{{ currentTitle }} · 共 {{ total }} 份</div>

          <div v-loading="loading" class="list">
            <div v-for="d in rows" :key="d.id" class="row">
              <el-checkbox v-if="d.needApproval && !d.access.canDownload && !d.access.pending" :model-value="selected.some((x) => x.id === d.id)"
                @update:model-value="(v: boolean | string | number) => toggle(d, !!v)" />
              <span v-else class="cb-space" />
              <FileIcon :ext="d.fileExt" />
              <div class="info" @click="d.previewable && openPreview(d)">
                <div class="title">
                  <span v-if="d.code" class="code">{{ d.code }}</span>
                  <span class="name">{{ d.name }}</span>
                  <el-tag v-if="d.isNew" size="small" type="danger" effect="dark" round>新</el-tag>
                </div>
                <div class="meta">
                  <el-tag :type="LEVELS[d.level].type" size="small" effect="plain">{{ d.levelLabel }}</el-tag>
                  <el-tag v-if="!d.needApproval" size="small" type="success" effect="light">免审下载</el-tag>
                  <span>{{ d.categoryPath }}</span>
                  <span v-if="d.year">{{ d.year }} 年</span>
                  <span>{{ fmtSize(d.fileSize) }}</span>
                  <span><el-icon><View /></el-icon>{{ d.viewCount }}</span>
                  <span><el-icon><Download /></el-icon>{{ d.downloadCount }}</span>
                  <span class="hide-m">更新于 {{ fmtTime(d.updatedAt, 'YYYY-MM-DD') }}</span>
                </div>
              </div>
              <div class="ops">
                <el-tooltip :disabled="d.previewable" content="该格式暂不支持在线预览" placement="top">
                  <el-button :disabled="!d.previewable" @click="openPreview(d)">预览</el-button>
                </el-tooltip>
                <el-button v-if="d.access.canDownload" type="success" @click="downloadDoc(d.id, d.access.requestId)">
                  下载<template v-if="d.access.remaining !== null">（剩 {{ d.access.remaining }} 次）</template>
                </el-button>
                <el-button v-else-if="d.access.pending" disabled>审批中</el-button>
                <el-button v-else v-perm="'doc:request'" type="primary" @click="openApply([d])">申请下载</el-button>
              </div>
            </div>
            <el-empty v-if="!loading && !rows.length" description="没有找到相关资料" />
          </div>

          <div class="pager">
            <el-pagination v-model:current-page="query.page" v-model:page-size="query.size" :total="total" :page-sizes="[10, 20, 50]"
              layout="total, sizes, prev, pager, next" background @current-change="load" @size-change="search" />
          </div>
        </div>
      </section>
    </div>

    <PreviewDialog v-model="previewVisible" :doc="preview" @apply="(d: DocVO) => openApply([d])" />
    <ApplyDialog v-model="applyVisible" :docs="applyDocs" @done="load" />
  </div>
</template>

<style scoped>
.btns {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}
.layout {
  display: grid;
  grid-template-columns: 230px minmax(0, 1fr);
  gap: 18px;
  align-items: start;
}
.cats {
  padding: 12px 10px;
  position: sticky;
  top: 80px;
}
.cat {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 9px 12px;
  border-radius: 10px;
  cursor: pointer;
  color: var(--hy-text-2);
  font-size: 14px;
  transition: background 0.15s;
}
.cat:hover {
  background: #f3f6fd;
}
.cat span {
  flex: 1;
}
.cat em {
  font-style: normal;
  font-size: 12px;
  color: var(--hy-text-3);
}
.cat.sub {
  padding-left: 38px;
  font-size: 13px;
}
.cat.active {
  background: var(--el-color-primary-light-9);
  color: var(--hy-primary);
  font-weight: 600;
}
.chips {
  display: none;
  gap: 8px;
  overflow-x: auto;
  margin-bottom: 12px;
}
.chip {
  padding: 6px 14px;
  border-radius: 16px;
  background: #fff;
  border: 1px solid var(--hy-line);
  white-space: nowrap;
  cursor: pointer;
  font-size: 13px;
}
.chip.active {
  background: var(--hy-gradient);
  color: #fff;
  border-color: transparent;
}
.search-bar {
  display: flex;
  gap: 10px;
}
.result-head {
  margin: 14px 0 4px;
}
.row {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 14px 6px;
  border-bottom: 1px solid #eef2f7;
}
.row:last-child {
  border-bottom: none;
}
.cb-space {
  width: 14px;
  flex: none;
}
.info {
  flex: 1;
  min-width: 0;
  cursor: pointer;
}
.title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 15px;
}
.code {
  color: var(--hy-text-3);
  font-weight: 600;
  flex: none;
}
.name {
  font-weight: 600;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.info:hover .name {
  color: var(--hy-primary);
}
.meta {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-top: 6px;
  font-size: 12px;
  color: var(--hy-text-3);
  flex-wrap: wrap;
}
.meta span {
  display: inline-flex;
  align-items: center;
  gap: 3px;
}
.ops {
  display: flex;
  gap: 8px;
  flex: none;
}
@media (max-width: 900px) {
  .layout {
    grid-template-columns: minmax(0, 1fr);
  }
  .cats {
    display: none;
  }
  .chips {
    display: flex;
  }
}
@media (max-width: 768px) {
  .search-bar {
    flex-direction: column;
  }
  .search-bar .el-select {
    width: 100% !important;
  }
  .row {
    flex-wrap: wrap;
    gap: 10px;
  }
  .info {
    flex-basis: calc(100% - 90px);
  }
  .ops {
    width: 100%;
    justify-content: flex-end;
  }
  .hide-m {
    display: none !important;
  }
}
</style>
