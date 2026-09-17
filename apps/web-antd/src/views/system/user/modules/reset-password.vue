<script lang="ts" setup>
import { ref } from 'vue';

import { useVbenDrawer } from '@vben/common-ui';

import { message } from 'ant-design-vue';

import { useVbenForm, z } from '#/adapter/form';
import { resetUserPassword } from '#/api/system/user';
import { $t } from '#/locales';

interface ResetPasswordData {
  id: number;
  username: string;
}

const emits = defineEmits(['success']);

const userId = ref<number>();
const username = ref('');

const [Form, formApi] = useVbenForm({
  commonConfig: {
    componentProps: { class: 'w-full' },
  },
  schema: [
    {
      component: 'InputPassword',
      componentProps: { autocomplete: 'new-password' },
      fieldName: 'password',
      help: $t('system.user.passwordHelp'),
      label: $t('system.user.newPassword'),
      // 与后端 ResetPasswordRequest 的 @Size(min = 6, max = 32) 对齐
      rules: z
        .string()
        .min(6, $t('ui.formRules.minLength', [$t('system.user.password'), 6]))
        .max(
          32,
          $t('ui.formRules.maxLength', [$t('system.user.password'), 32]),
        ),
    },
    {
      component: 'InputPassword',
      componentProps: { autocomplete: 'new-password' },
      fieldName: 'confirmPassword',
      label: $t('system.user.confirmPassword'),
      rules: 'required',
    },
  ],
  showDefaultActions: false,
  wrapperClass: 'grid-cols-1',
});

const [Drawer, drawerApi] = useVbenDrawer<ResetPasswordData>({
  async onConfirm() {
    const { valid } = await formApi.validate();
    if (!valid) {
      return;
    }
    const values = (await formApi.getValues()) as {
      confirmPassword: string;
      password: string;
    };
    // 一致性校验放在提交前：vben 对"必填"字段会剥掉 zod 的 refine 外层包装，
    // 用 dependencies.rules 做 refine 会破坏 required 语义，这里用最直白的方式兜住
    if (values.password !== values.confirmPassword) {
      message.error($t('system.user.passwordMismatch'));
      return;
    }
    if (!userId.value) {
      return;
    }
    drawerApi.lock();
    try {
      await resetUserPassword(userId.value, values.password);
      // 后端重置密码时会递增令牌版本，该用户所有会话（含其它设备）已被踢下线
      message.success($t('system.user.resetPasswordDone'));
      emits('success');
      drawerApi.close();
    } finally {
      drawerApi.unlock();
    }
  },

  async onOpenChange(isOpen) {
    if (!isOpen) {
      return;
    }
    const data = drawerApi.getData();
    userId.value = data?.id;
    username.value = data?.username ?? '';
    formApi.reset();
  },
});

defineExpose({ drawerApi });
</script>
<template>
  <Drawer
    class="w-full max-w-120"
    :title="`${$t('system.user.resetPassword')} - ${username}`"
  >
    <Form />
  </Drawer>
</template>
