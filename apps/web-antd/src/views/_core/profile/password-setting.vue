<script setup lang="ts">
import type { VbenFormSchema } from '#/adapter/form';

import { computed } from 'vue';

import { ProfilePasswordSetting, z } from '@vben/common-ui';

import { message } from 'ant-design-vue';

import { changePasswordApi } from '#/api';
import { $t } from '#/locales';
import { useAuthStore } from '#/store';

const authStore = useAuthStore();

const formSchema = computed((): VbenFormSchema[] => {
  return [
    {
      fieldName: 'oldPassword',
      label: $t('profile.oldPassword'),
      component: 'VbenInputPassword',
      componentProps: {
        placeholder: $t('profile.oldPasswordPlaceholder'),
      },
    },
    {
      fieldName: 'newPassword',
      label: $t('profile.newPassword'),
      component: 'VbenInputPassword',
      componentProps: {
        passwordStrength: true,
        placeholder: $t('profile.newPasswordPlaceholder'),
      },
      // 与后端 ChangePasswordRequest 的 @Size(min = 6, max = 32) 对齐，
      // 复用 ui.formRules 的通用文案（与「用户管理 → 重置密码」保持同一套提示）
      rules: z
        .string()
        .min(6, $t('ui.formRules.minLength', [$t('profile.newPassword'), 6]))
        .max(32, $t('ui.formRules.maxLength', [$t('profile.newPassword'), 32])),
    },
    {
      fieldName: 'confirmPassword',
      label: $t('profile.confirmPassword'),
      component: 'VbenInputPassword',
      componentProps: {
        passwordStrength: true,
        placeholder: $t('profile.confirmPasswordPlaceholder'),
      },
      dependencies: {
        rules(values) {
          const { newPassword } = values;
          return z
            .string({
              error: $t('ui.formRules.required', [
                $t('profile.confirmPassword'),
              ]),
            })
            .min(1, {
              message: $t('ui.formRules.required', [
                $t('profile.confirmPassword'),
              ]),
            })
            .refine((value) => value === newPassword, {
              message: $t('profile.passwordMismatch'),
            });
        },
        triggerFields: ['newPassword'],
      },
    },
  ];
});

async function handleSubmit(values: Record<string, any>) {
  // 接后端 PUT /api/auth/password（校验旧密码；超管也通过这里改自己的密码）
  await changePasswordApi({
    newPassword: values.newPassword,
    oldPassword: values.oldPassword,
  });
  // 后端改密时会递增令牌版本，当前会话与其它设备上的会话一并失效。
  // 这里显式登出并提示，避免用户在下一次请求时才撞见“登录已失效”而误以为是故障
  message.success($t('profile.passwordChanged'));
  await authStore.logout(false);
}
</script>
<template>
  <ProfilePasswordSetting
    class="w-1/3"
    :form-schema="formSchema"
    @submit="handleSubmit"
  />
</template>
