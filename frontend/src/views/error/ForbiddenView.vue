<template>
  <div class="page">
    <div class="card">
      <el-result icon="warning" title="无权访问" sub-title="您没有访问该页面的权限，如有需要请联系系统管理员。">
        <template #extra>
          <el-button type="primary" @click="$router.replace('/')">返回工作台</el-button>
        </template>
      </el-result>
    </div>
  </div>
</template>
