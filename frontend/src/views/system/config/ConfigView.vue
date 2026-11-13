<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { configApi, type ConfigItem, type ScoreRule } from '@/api/system'
import { useAuthStore } from '@/stores/auth'
import { fmtTime } from '@/utils/format'

const auth = useAuthStore()
const canEdit = computed(() => auth.hasPerm('system:config:edit'))
const tab = ref('score')
const rules = ref<ScoreRule[]>([])
const fill = ref<ConfigItem[]>([])
const security = ref<ConfigItem[]>([])
const project = ref<ConfigItem[]>([])
const saving = ref(false)

const weightSum = computed(() => rules.value.filter((r) => r.enabled).reduce((s, r) => s + (r.weight || 0), 0))

const CHANNELS = [{ value: 'SITE', label: '站内消息' }]

async function load() {
  ;[rules.value, fill.value, security.value, project.value] = await Promise.all([
    configApi.scoreRules(),
    configApi.list('fill'),
    configApi.list('security'),
    configApi.list('project')
  ])
}

function addRule() {
  rules.value.push({ id: null, ruleKey: '', name: '', weight: 0, description: '', sort: rules.value.length + 1, enabled: true })
}

async function saveRules() {
  if (rules.value.some((r) => !r.ruleKey || !r.name)) return ElMessage.warning('请填写指标编码和名称')
  if (weightSum.value !== 100) return ElMessage.warning(`启用指标的权重合计必须为 100%，当前为 ${weightSum.value}%`)
  saving.value = true
  try {
    await configApi.saveScoreRules(rules.value.map((r, i) => ({ ...r, sort: i + 1 })))
    ElMessage.success('考评指标已保存')
    rules.value = await configApi.scoreRules()
  } finally {
    saving.value = false
  }
}

async function saveConfigs(items: ConfigItem[]) {
  saving.value = true
  try {
    await configApi.update(items.map((c) => ({ key: c.key, value: c.value })))
    ElMessage.success('已保存')
    load()
  } finally {
    saving.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="page">
    <div class="page-head">
      <div>
        <h3>参数配置</h3>
        <p>月度考评指标、周报填报规则与账号安全设置</p>
      </div>
    </div>
    <div class="card">
      <el-tabs v-model="tab">
        <el-tab-pane label="考评指标" name="score">
          <el-alert type="info" :closable="false" show-icon style="margin-bottom: 12px"
            title="月度考评按「指标得分 × 权重」计算总分（满分 100）。启用指标的权重合计必须为 100%。" />
          <el-table :data="rules" border>
            <el-table-column label="指标编码" width="150">
              <template #default="{ row }"><el-input v-model="row.ruleKey" :disabled="!canEdit || !!row.id" placeholder="如 quality" /></template>
            </el-table-column>
            <el-table-column label="指标名称" width="170">
              <template #default="{ row }"><el-input v-model="row.name" :disabled="!canEdit" /></template>
            </el-table-column>
            <el-table-column label="权重（%）" width="150">
              <template #default="{ row }"><el-input-number v-model="row.weight" :min="0" :max="100" :disabled="!canEdit" style="width: 120px" /></template>
            </el-table-column>
            <el-table-column label="评分说明" min-width="240">
              <template #default="{ row }"><el-input v-model="row.description" :disabled="!canEdit" /></template>
            </el-table-column>
            <el-table-column label="启用" width="80" align="center">
              <template #default="{ row }"><el-switch v-model="row.enabled" :disabled="!canEdit" /></template>
            </el-table-column>
            <el-table-column v-if="canEdit" label="操作" width="80" align="center">
              <template #default="{ $index }"><el-button link type="danger" @click="rules.splice($index, 1)">删除</el-button></template>
            </el-table-column>
          </el-table>
          <div class="foot">
            <span>权重合计：<b :class="{ bad: weightSum !== 100 }">{{ weightSum }}%</b></span>
            <div v-if="canEdit">
              <el-button icon="Plus" @click="addRule">添加指标</el-button>
              <el-button type="primary" :loading="saving" @click="saveRules">保存</el-button>
            </div>
          </div>
        </el-tab-pane>

        <el-tab-pane label="填报规则" name="fill">
          <el-form label-width="150px" class="cfg-form">
            <el-form-item v-for="c in fill" :key="c.key" :label="c.name">
              <el-checkbox-group
                v-if="c.key === 'fill.notify.channels'"
                :model-value="c.value.split(',').filter(Boolean)"
                :disabled="!canEdit"
                @update:model-value="(v) => (c.value = (v as string[]).join(','))"
              >
                <el-checkbox v-for="ch in CHANNELS" :key="ch.value" :value="ch.value">{{ ch.label }}</el-checkbox>
              </el-checkbox-group>
              <el-input v-else v-model="c.value" :disabled="!canEdit" maxlength="100" />
              <div class="hint">{{ c.remark }}<span v-if="c.remark"> · </span>更新于 {{ fmtTime(c.updatedAt) }}</div>
            </el-form-item>
            <el-form-item v-if="canEdit">
              <el-button type="primary" :loading="saving" @click="saveConfigs(fill)">保存</el-button>
            </el-form-item>
          </el-form>
          <el-alert type="warning" :closable="false" show-icon title="逾期判定已按以上截止时间执行；自动提醒将在消息功能上线后按提醒时间发送。" />
        </el-tab-pane>

        <el-tab-pane label="项目设置" name="project">
          <el-form label-width="150px" class="cfg-form">
            <el-form-item v-for="c in project" :key="c.key" :label="c.name">
              <el-input v-model="c.value" :disabled="!canEdit" maxlength="200" />
              <div class="hint">{{ c.remark }}</div>
            </el-form-item>
            <el-form-item v-if="canEdit">
              <el-button type="primary" :loading="saving" @click="saveConfigs(project)">保存</el-button>
            </el-form-item>
          </el-form>
        </el-tab-pane>

        <el-tab-pane label="安全设置" name="security">
          <el-form label-width="150px" class="cfg-form">
            <el-form-item v-for="c in security" :key="c.key" :label="c.name">
              <el-input v-if="c.key === 'security.init-password'" v-model="c.value" :disabled="!canEdit" show-password type="password" />
              <el-input-number v-else :model-value="Number(c.value)" :min="1" :max="c.key.endsWith('minutes') ? 1440 : 20" :disabled="!canEdit"
                @update:model-value="(v: number | undefined) => (c.value = String(v ?? ''))" />
              <div class="hint">{{ c.remark }}</div>
            </el-form-item>
            <el-form-item v-if="canEdit">
              <el-button type="primary" :loading="saving" @click="saveConfigs(security)">保存</el-button>
            </el-form-item>
          </el-form>
        </el-tab-pane>
      </el-tabs>
    </div>
  </div>
</template>

<style scoped>
.foot {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 14px;
  flex-wrap: wrap;
  gap: 10px;
}
.bad {
  color: var(--el-color-danger);
}
.cfg-form {
  max-width: 640px;
}
.hint {
  width: 100%;
  font-size: 12px;
  color: #8a94a6;
  line-height: 1.6;
  margin-top: 2px;
}
</style>
