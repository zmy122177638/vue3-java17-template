<script lang="ts" setup>
import type { Recordable } from '@vben/types';

import type { SystemUser } from '#/api/system/user';

import { computed, nextTick, ref } from 'vue';

import { useVbenDrawer } from '@vben/common-ui';

import { message } from 'ant-design-vue';

import { useVbenForm } from '#/adapter/form';
import { createUser, getAssignableRoles, updateUser } from '#/api/system/user';
import { $t } from '#/locales';

import { useFormSchema } from '../data';

const emits = defineEmits(['success']);

const id = ref<number>();
const roleOptions = ref<{ label: string; value: number }[]>([]);

const [Form, formApi] = useVbenForm({
  schema: useFormSchema(roleOptions.value, false),
  showDefaultActions: false,
});

const [Drawer, drawerApi] = useVbenDrawer<null | SystemUser>({
  async onConfirm() {
    const { valid } = await formApi.validate();
    if (!valid) return;
    const values = (await formApi.getValues()) as Recordable<any>;
    // 只提交上行 DTO 声明的字段。整行回填 + 整行提交会把 id / createdAt / roleNames / editable
    // 混进请求体，那只是靠 Spring Boot 默认关闭 FAIL_ON_UNKNOWN_PROPERTIES 才没报错（隐式依赖）。
    // 另：编辑接口 UserUpdateRequest 不接受 username/password，因此按模式分别构造
    const common = {
      email: values.email,
      nickname: values.nickname,
      phone: values.phone,
      roleIds: values.roleIds,
      status: values.status,
    };
    drawerApi.lock();
    const request = id.value
      ? updateUser(id.value, common)
      : createUser({
          ...common,
          password: values.password,
          username: values.username,
        });
    request
      .then(() => {
        message.success($t('ui.actionMessage.operationSuccess'));
        emits('success');
        drawerApi.close();
      })
      .catch(() => {
        drawerApi.unlock();
      });
  },

  async onOpenChange(isOpen) {
    if (!isOpen) return;
    const data = drawerApi.getData();
    formApi.reset();
    id.value = data?.id;

    if (roleOptions.value.length === 0) {
      await loadRoles();
    }

    // 编辑态：重新生成 schema（禁用用户名、免填密码），回填角色
    formApi.setState({ schema: useFormSchema(roleOptions.value, !!data?.id) });

    await nextTick();
    if (data?.id) {
      // 只回填 schema 声明的字段（roleIds 由 UserItem 一并返回，无需再请求 /role-ids），
      // 避免 id / createdAt / roleNames / editable 进入表单状态
      formApi.setValues({
        email: data.email,
        nickname: data.nickname,
        phone: data.phone,
        roleIds: data.roleIds,
        status: data.status,
        username: data.username,
      });
    }
  },
});

defineExpose({ drawerApi });

async function loadRoles() {
  // 走后端“可分配角色”接口：普通管理员不会拿到内置 ADMIN 角色，
  // 因此界面上无法给他人授予管理员权限（后端同样会拦截）
  const roles = await getAssignableRoles();
  roleOptions.value = roles.map((role) => ({
    label: role.name,
    value: role.id,
  }));
  formApi.setState({ schema: useFormSchema(roleOptions.value, !!id.value) });
}

const getDrawerTitle = computed(() =>
  id.value
    ? $t('ui.actionTitle.edit', [$t('system.user.title')])
    : $t('ui.actionTitle.create', [$t('system.user.title')]),
);
</script>
<template>
  <Drawer class="w-full max-w-150" :title="getDrawerTitle">
    <Form />
  </Drawer>
</template>
