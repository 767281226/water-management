<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { logApi, type LoginLog, type LogQuery, type OperLog } from '@/api/system'
import { fmtTime } from '@/utils/format'

const tab = ref<'oper' | 'login'>('oper')
const MODULES = ['用户管理', '部门管理', '角色权限', '参数配置', '个人中心', '登录']
const loading = ref(false)
const operRows = ref<OperLog[]>([])
const loginRows = ref<LoginLog[]>([])
const total = ref(0)
const range = ref<[string, string] | null>(null)
const query = reactive<LogQuery>({ keyword: '', module: '', success: '', page: 1, size: 20 })

async function load() {
  loading.value = true
  try {
    const q = { ...query, start: range.value?.[0], end: range.value?.[1] }
    if (tab.value === 'oper') {
      const r = await logApi.oper(q)
      operRows.value = r.records
      total.value = r.total
    } else {
      const r = await logApi.login({ ...q, module: '' })
      loginRows.value = r.records
      total.value = r.total
    }
  } finally {
    loading.value = false
  }
}

function search() {
  query.page = 1
  load()
}

function switchTab() {
  Object.assign(query, { keyword: '', module: '', success: '', page: 1 })
  range.value = null
  load()
}

function pretty(params: string | null) {
  if (!params) return '—'
  try {
    return JSON.stringify(JSON.parse(params), null, 2)
  } catch {
    return params
  }
}

onMounted(load)
</script>

<template>
  <div class="page">
    <div class="page-head">
      <div>
        <h3>日志审计</h3>
        <p>记录关键操作与登录情况，密码等敏感参数已脱敏</p>
      </div>
    </div>
    <div class="card">
      <el-tabs v-model="tab" @tab-change="switchTab">
        <el-tab-pane label="操作日志" name="oper" />
        <el-tab-pane label="登录日志" name="login" />
      </el-tabs>
      <div class="toolbar">
        <el-input v-model="query.keyword" :placeholder="tab === 'oper' ? '操作人 / 操作 / 参数' : '账号 / IP'" clearable style="width: 200px" @keyup.enter="search" @clear="search" />
        <el-select v-if="tab === 'oper'" v-model="query.module" placeholder="全部模块" clearable style="width: 130px" @change="search">
          <el-option v-for="m in MODULES" :key="m" :label="m" :value="m" />
        </el-select>
        <el-select v-model="query.success" placeholder="全部结果" clearable style="width: 110px" @change="search">
          <el-option label="成功" :value="true" />
          <el-option label="失败" :value="false" />
        </el-select>
        <el-date-picker v-model="range" type="daterange" value-format="YYYY-MM-DD" start-placeholder="开始日期" end-placeholder="结束日期" style="width: 260px" @change="search" />
        <el-button type="primary" icon="Search" @click="search">查询</el-button>
      </div>

      <el-table v-if="tab === 'oper'" v-loading="loading" :data="operRows" border stripe>
        <el-table-column type="expand">
          <template #default="{ row }">
            <div class="detail">
              <div><span>请求：</span>{{ row.method }} {{ row.uri }}</div>
              <div v-if="row.errorMsg"><span>失败原因：</span><b class="bad">{{ row.errorMsg }}</b></div>
              <div><span>参数：</span></div>
              <pre>{{ pretty(row.params) }}</pre>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="时间" width="160"><template #default="{ row }">{{ fmtTime(row.createdAt, 'YYYY-MM-DD HH:mm:ss') }}</template></el-table-column>
        <el-table-column label="操作人" width="140"><template #default="{ row }">{{ row.realName || '—' }} <span class="muted small">{{ row.username }}</span></template></el-table-column>
        <el-table-column prop="module" label="模块" width="110" />
        <el-table-column prop="action" label="操作" min-width="140" />
        <el-table-column label="结果" width="80">
          <template #default="{ row }"><el-tag :type="row.success ? 'success' : 'danger'" size="small">{{ row.success ? '成功' : '失败' }}</el-tag></template>
        </el-table-column>
        <el-table-column prop="ip" label="IP" width="130" />
        <el-table-column label="耗时" width="90" align="right"><template #default="{ row }">{{ row.costMs }} ms</template></el-table-column>
      </el-table>

      <el-table v-else v-loading="loading" :data="loginRows" border stripe>
        <el-table-column label="时间" width="160"><template #default="{ row }">{{ fmtTime(row.createdAt, 'YYYY-MM-DD HH:mm:ss') }}</template></el-table-column>
        <el-table-column prop="username" label="账号" width="130" />
        <el-table-column label="结果" width="80">
          <template #default="{ row }"><el-tag :type="row.success ? 'success' : 'danger'" size="small">{{ row.success ? '成功' : '失败' }}</el-tag></template>
        </el-table-column>
        <el-table-column prop="message" label="说明" min-width="200" />
        <el-table-column prop="ip" label="IP" width="130" />
        <el-table-column prop="userAgent" label="浏览器" min-width="220" show-overflow-tooltip />
      </el-table>

      <div class="pager">
        <el-pagination
          v-model:current-page="query.page"
          v-model:page-size="query.size"
          :total="total"
          :page-sizes="[20, 50, 100]"
          layout="total, sizes, prev, pager, next"
          background
          @current-change="load"
          @size-change="search"
        />
      </div>
    </div>
  </div>
</template>

<style scoped>
.detail {
  padding: 4px 16px;
  font-size: 13px;
}
.detail span {
  color: #8a94a6;
}
.detail pre {
  background: #f7f9fb;
  padding: 10px;
  border-radius: 6px;
  max-height: 260px;
  overflow: auto;
  margin: 6px 0 0;
}
.bad {
  color: var(--el-color-danger);
}
</style>
