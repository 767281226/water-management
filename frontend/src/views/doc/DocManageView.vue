<script setup lang="ts">
import { onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { docApi, LEVELS, LOG_ACTIONS, type CategoryNode, type DocLog, type DocQuery, type DocVO, type StorageInfo, type SyncResult } from '@/api/doc'
import { fmtSize } from '@/utils/file'
import { fmtTime } from '@/utils/format'
import FileIcon from '@/components/FileIcon.vue'
import DocFormDialog from './DocFormDialog.vue'
import PreviewDialog from './PreviewDialog.vue'

const tab = ref<'docs' | 'cats' | 'logs' | 'nas'>('docs')
const tree = ref<CategoryNode[]>([])

// ---------------- 资料列表 ----------------
const rows = ref<DocVO[]>([])
const total = ref(0)
const loading = ref(false)
const query = reactive<DocQuery>({ keyword: '', categoryId: null, status: '', storageType: '', manage: true, page: 1, size: 20 })
const dialog = ref(false)
const dialogMode = ref<'create' | 'edit' | 'replace'>('create')
const editing = ref<DocVO | null>(null)
const preview = ref<DocVO | null>(null)
const previewVisible = ref(false)

async function loadDocs() {
  loading.value = true
  try {
    const r = await docApi.search(query)
    rows.value = r.records
    total.value = r.total
  } finally {
    loading.value = false
  }
}
function searchDocs() {
  query.page = 1
  loadDocs()
}
function open(mode: 'create' | 'edit' | 'replace', d?: DocVO) {
  dialogMode.value = mode
  editing.value = d ?? null
  dialog.value = true
}
async function toggle(d: DocVO) {
  const next = d.status === 'ON' ? 'OFF' : 'ON'
  await docApi.setStatus(d.id, next)
  ElMessage.success(next === 'ON' ? '已上架' : '已下架')
  loadDocs()
}
async function remove(d: DocVO) {
  await ElMessageBox.confirm(`确定删除「${d.name}」吗？删除后员工无法查询，访问记录保留。`, '删除资料', { type: 'warning' })
  await docApi.remove(d.id)
  ElMessage.success('已删除')
  loadDocs()
  loadTree()
}

// ---------------- 分类 ----------------
async function loadTree() {
  tree.value = await docApi.categories()
}
async function editCategory(parent: CategoryNode | null, node?: CategoryNode) {
  const { value } = await ElMessageBox.prompt(node ? '修改分类名称' : parent ? `在「${parent.name}」下新增子分类` : '新增一级分类', node ? '编辑分类' : '新增分类', {
    inputValue: node?.name ?? '',
    inputValidator: (v) => (!!v && v.trim().length > 0 && v.length <= 64) || '请输入 1~64 个字的分类名称'
  })
  const f = { parentId: node ? node.parentId : parent?.id ?? null, name: value.trim(), sort: node?.sort ?? 99 }
  if (node) await docApi.updateCategory(node.id, f)
  else await docApi.createCategory(f)
  ElMessage.success('已保存')
  loadTree()
}
async function deleteCategory(node: CategoryNode) {
  await ElMessageBox.confirm(`确定删除分类「${node.name}」吗？`, '删除分类', { type: 'warning' })
  await docApi.deleteCategory(node.id)
  ElMessage.success('已删除')
  loadTree()
}

// ---------------- 日志 ----------------
const logs = ref<DocLog[]>([])
const logTotal = ref(0)
const lq = reactive({ keyword: '', action: '', page: 1, size: 20 })
async function loadLogs() {
  const r = await docApi.logs(lq)
  logs.value = r.records
  logTotal.value = r.total
}

// ---------------- 网盘 ----------------
const storage = ref<StorageInfo | null>(null)
const syncing = ref(false)
const lastResult = ref<SyncResult | null>(null)
async function loadStorage() {
  storage.value = await docApi.storage()
}
async function sync() {
  syncing.value = true
  try {
    lastResult.value = await docApi.sync()
    ElMessage.success('同步完成')
    loadStorage()
    loadTree()
  } finally {
    syncing.value = false
  }
}

watch(tab, (t) => {
  if (t === 'logs') loadLogs()
  if (t === 'nas') loadStorage()
  if (t === 'cats') loadTree()
})
onMounted(() => {
  loadDocs()
  loadTree()
})
</script>

<template>
  <div class="page">
    <div class="page-head">
      <div>
        <h3>资料维护</h3>
        <p>上传与维护资料、调整分类和密级、查看访问记录、同步内部网盘</p>
      </div>
      <el-button v-if="tab === 'docs'" type="primary" icon="Upload" @click="open('create')">上传资料</el-button>
    </div>
    <div class="card">
      <el-tabs v-model="tab">
        <el-tab-pane label="资料列表" name="docs" />
        <el-tab-pane label="分类管理" name="cats" />
        <el-tab-pane label="访问日志" name="logs" />
        <el-tab-pane label="网盘对接" name="nas" />
      </el-tabs>

      <!-- 资料列表 -->
      <template v-if="tab === 'docs'">
        <div class="toolbar">
          <el-input v-model="query.keyword" placeholder="名称 / 编号 / 文件名" clearable style="width: 220px" @keyup.enter="searchDocs" @clear="searchDocs" />
          <el-tree-select v-model="query.categoryId" :data="tree" :props="{ label: 'name', children: 'children' }" node-key="id" check-strictly
            default-expand-all clearable placeholder="全部分类" style="width: 170px" @change="searchDocs" />
          <el-select v-model="query.status" placeholder="全部状态" clearable style="width: 110px" @change="searchDocs">
            <el-option label="已上架" value="ON" /><el-option label="已下架" value="OFF" />
          </el-select>
          <el-select v-model="query.storageType" placeholder="全部来源" clearable style="width: 120px" @change="searchDocs">
            <el-option label="系统上传" value="LOCAL" /><el-option label="内部网盘" value="NAS" />
          </el-select>
          <el-button type="primary" icon="Search" @click="searchDocs">查询</el-button>
        </div>
        <el-table v-loading="loading" :data="rows" border stripe>
          <el-table-column label="资料" min-width="280">
            <template #default="{ row }">
              <div class="doc-cell">
                <FileIcon :ext="row.fileExt" :size="30" />
                <div>
                  <b>{{ row.code ? `${row.code} ` : '' }}{{ row.name }}</b>
                  <div class="muted small">{{ row.categoryPath }} · {{ fmtSize(row.fileSize) }}</div>
                </div>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="密级 / 审批" width="130">
            <template #default="{ row }">
              <el-tag :type="LEVELS[row.level as keyof typeof LEVELS].type" size="small" effect="plain">{{ row.levelLabel }}</el-tag>
              <el-tag v-if="!row.needApproval" size="small" type="success" style="margin-left: 4px">免审</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="来源" width="100">
            <template #default="{ row }"><el-tag size="small" :type="row.storageType === 'NAS' ? 'warning' : 'info'" effect="light">{{ row.storageType === 'NAS' ? '网盘' : '上传' }}</el-tag></template>
          </el-table-column>
          <el-table-column label="浏览 / 下载" width="100" align="center"><template #default="{ row }">{{ row.viewCount }} / {{ row.downloadCount }}</template></el-table-column>
          <el-table-column label="状态" width="110">
            <template #default="{ row }">
              <el-tag :type="row.status === 'ON' ? 'success' : 'info'" size="small">{{ row.status === 'ON' ? '已上架' : '已下架' }}</el-tag>
              <el-tooltip v-if="row.sourceMissing" content="网盘中的源文件已不存在" placement="top"><el-icon class="warn"><WarningFilled /></el-icon></el-tooltip>
            </template>
          </el-table-column>
          <el-table-column label="更新时间" width="140"><template #default="{ row }">{{ fmtTime(row.updatedAt) }}</template></el-table-column>
          <el-table-column label="操作" width="250" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" :disabled="!row.previewable || row.status !== 'ON'" @click="preview = row as DocVO; previewVisible = true">预览</el-button>
              <el-button link type="primary" @click="open('edit', row as DocVO)">编辑</el-button>
              <el-button v-if="row.storageType === 'LOCAL'" link type="primary" @click="open('replace', row as DocVO)">更新文件</el-button>
              <el-button link :type="row.status === 'ON' ? 'warning' : 'success'" @click="toggle(row as DocVO)">{{ row.status === 'ON' ? '下架' : '上架' }}</el-button>
              <el-button link type="danger" @click="remove(row as DocVO)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
        <div class="pager">
          <el-pagination v-model:current-page="query.page" v-model:page-size="query.size" :total="total" :page-sizes="[20, 50, 100]"
            layout="total, sizes, prev, pager, next" background @current-change="loadDocs" @size-change="searchDocs" />
        </div>
      </template>

      <!-- 分类管理 -->
      <template v-if="tab === 'cats'">
        <div class="toolbar"><el-button type="primary" icon="Plus" @click="editCategory(null)">新增一级分类</el-button></div>
        <el-table :data="tree" row-key="id" default-expand-all border :tree-props="{ children: 'children' }">
          <el-table-column prop="name" label="分类名称" min-width="220" />
          <el-table-column prop="docCount" label="上架资料数" width="120" align="right" />
          <el-table-column label="操作" width="240">
            <template #default="{ row }">
              <el-button v-if="!row.parentId" link type="primary" @click="editCategory(row as CategoryNode)">新增子分类</el-button>
              <el-button link type="primary" @click="editCategory(null, row as CategoryNode)">重命名</el-button>
              <el-button link type="danger" @click="deleteCategory(row as CategoryNode)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
        <div class="muted small" style="margin-top: 10px">资料分类最多两级；网盘同步时，一级 / 二级目录会自动对应到一级 / 二级分类。</div>
      </template>

      <!-- 访问日志 -->
      <template v-if="tab === 'logs'">
        <div class="toolbar">
          <el-input v-model="lq.keyword" placeholder="人员 / 资料名称" clearable style="width: 200px" @keyup.enter="lq.page = 1; loadLogs()" @clear="loadLogs" />
          <el-select v-model="lq.action" placeholder="全部操作" clearable style="width: 130px" @change="lq.page = 1; loadLogs()">
            <el-option v-for="(v, k) in LOG_ACTIONS" :key="k" :label="v" :value="k" />
          </el-select>
          <el-button type="primary" icon="Search" @click="lq.page = 1; loadLogs()">查询</el-button>
        </div>
        <el-table :data="logs" border stripe>
          <el-table-column label="时间" width="160"><template #default="{ row }">{{ fmtTime(row.createdAt, 'YYYY-MM-DD HH:mm:ss') }}</template></el-table-column>
          <el-table-column label="人员" width="110"><template #default="{ row }">{{ row.userName || '—' }}</template></el-table-column>
          <el-table-column label="操作" width="100">
            <template #default="{ row }"><el-tag size="small" :type="row.action === 'DOWNLOAD' ? 'success' : row.action === 'VIEW' ? 'info' : 'primary'">{{ LOG_ACTIONS[row.action] || row.action }}</el-tag></template>
          </el-table-column>
          <el-table-column prop="docName" label="资料" min-width="220" show-overflow-tooltip />
          <el-table-column label="说明" min-width="200" show-overflow-tooltip><template #default="{ row }">{{ row.detail || (row.requestId ? `申请 #${row.requestId}` : '—') }}</template></el-table-column>
          <el-table-column prop="ip" label="IP" width="130" />
        </el-table>
        <div class="pager">
          <el-pagination v-model:current-page="lq.page" :page-size="lq.size" :total="logTotal" layout="total, prev, pager, next" background @current-change="loadLogs" />
        </div>
      </template>

      <!-- 网盘对接 -->
      <template v-if="tab === 'nas' && storage">
        <div class="nas">
          <div class="nas-card" :class="{ on: storage.nasEnabled }">
            <div class="nas-icon"><el-icon :size="30"><Connection /></el-icon></div>
            <div class="nas-info">
              <b>{{ storage.nasEnabled ? '已对接内部网盘' : '尚未对接内部网盘' }}</b>
              <span>{{ storage.nasDescription }}</span>
              <span>上次同步：{{ storage.lastSync || '从未同步' }} · 网盘资料 {{ storage.nasDocs }} 份 · 系统上传 {{ storage.localDocs }} 份</span>
            </div>
            <el-button type="primary" size="large" icon="Refresh" :loading="syncing" :disabled="!storage.nasEnabled" @click="sync">立即同步</el-button>
          </div>
          <el-alert v-if="lastResult" type="success" :closable="false" show-icon style="margin-top: 14px"
            :title="`同步完成：扫描 ${lastResult.scanned} 个文件，新增 ${lastResult.added}，更新 ${lastResult.updated}，下架 ${lastResult.missing}，恢复 ${lastResult.restored}（耗时 ${(lastResult.costMs / 1000).toFixed(1)} 秒）`" />
          <div class="howto">
            <h4>同步规则</h4>
            <ul>
              <li>网盘一级目录 → 一级分类，二级目录 → 二级分类（更深的目录归入二级分类），根目录文件归入"未分类"；</li>
              <li>新文件默认「内部 / 需审批」，可在资料列表中调整密级和审批方式；文件名以标准号开头（如 SL 252-2017）时自动识别编号和年份；</li>
              <li>网盘中删除的文件会自动下架，重新放回后自动恢复；网盘资料的文件内容以网盘为准，系统不复制、不修改网盘文件。</li>
            </ul>
            <h4 v-if="!storage.nasEnabled">如何对接</h4>
            <ul v-if="!storage.nasEnabled">
              <li>将网盘共享目录挂载到服务器（Windows 可直接使用 \\NAS 名称\共享目录；Linux 使用 mount -t cifs / nfs 挂载）；</li>
              <li>在服务器上设置环境变量 <code>NAS_TYPE=mount</code>、<code>NAS_ROOT=挂载路径</code>，重启后端服务；</li>
              <li>网盘只支持 WebDAV 或厂商接口时，需按网盘产品开发对应的适配器（系统已预留扩展接口）。</li>
            </ul>
          </div>
        </div>
      </template>
    </div>

    <DocFormDialog v-model="dialog" :doc="editing" :mode="dialogMode" :tree="tree" @saved="loadDocs(); loadTree()" />
    <PreviewDialog v-model="previewVisible" :doc="preview" />
  </div>
</template>

<style scoped>
.doc-cell {
  display: flex;
  align-items: center;
  gap: 10px;
}
.doc-cell b {
  font-size: 13px;
}
.warn {
  color: #f59e0b;
  margin-left: 4px;
  vertical-align: middle;
}
.nas-card {
  display: flex;
  align-items: center;
  gap: 18px;
  padding: 22px;
  border-radius: 16px;
  background: linear-gradient(135deg, #f4f6fa, #eef1f6);
  border: 1px solid #e6ebf3;
}
.nas-card.on {
  background: linear-gradient(135deg, #eaf0ff 0%, #e3f6fd 100%);
  border-color: #d5e1ff;
}
.nas-icon {
  width: 60px;
  height: 60px;
  border-radius: 16px;
  background: #cbd5e1;
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  flex: none;
}
.nas-card.on .nas-icon {
  background: var(--hy-gradient);
  box-shadow: 0 8px 18px rgba(47, 107, 255, 0.3);
}
.nas-info {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
}
.nas-info b {
  font-size: 17px;
}
.nas-info span {
  font-size: 13px;
  color: var(--hy-text-2);
  word-break: break-all;
}
.howto {
  margin-top: 20px;
  font-size: 13px;
  color: var(--hy-text-2);
  line-height: 1.9;
}
.howto h4 {
  margin: 12px 0 4px;
  color: var(--hy-text);
  font-size: 14px;
}
.howto ul {
  margin: 0;
  padding-left: 20px;
}
.howto code {
  background: #f1f5fd;
  padding: 1px 6px;
  border-radius: 4px;
  color: var(--hy-primary);
}
@media (max-width: 768px) {
  .nas-card {
    flex-direction: column;
    align-items: flex-start;
  }
}
</style>
