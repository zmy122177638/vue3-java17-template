<script lang="ts" setup>
/**
 * 多语言文本录入组件（菜单标题等）。
 * 对外值为多语言 JSON 字符串：{"zh-CN":"系统管理","en-US":"System"}；
 * 内部按语言拆分为多个单行输入，任一语言变更即重新序列化为 JSON 抛出。
 * 既可作为表单字段（已注册为 I18nInput），也可独立使用。
 */
import { computed, ref, watch } from 'vue';

import { Input } from 'ant-design-vue';

import { $t } from '#/locales';

export interface I18nLangItem {
  label: string;
  value: string;
}

const props = withDefaults(
  defineProps<{
    disabled?: boolean;
    langs?: I18nLangItem[];
    modelValue?: string;
  }>(),
  {
    disabled: false,
    langs: undefined,
    modelValue: '',
  },
);

const emit = defineEmits<{
  change: [string];
  'update:modelValue': [string];
}>();

const defaultLangs = computed<I18nLangItem[]>(() => [
  { label: $t('common.i18nInput.zh'), value: 'zh-CN' },
  { label: $t('common.i18nInput.en'), value: 'en-US' },
]);

const langs = computed(() => props.langs ?? defaultLangs.value);

const texts = ref<Record<string, string>>({});

watch(
  () => props.modelValue,
  (value) => {
    texts.value = decode(value);
  },
  { immediate: true },
);

function decode(json?: string): Record<string, string> {
  if (!json) {
    return {};
  }
  try {
    const parsed = JSON.parse(json) as Record<string, string>;
    return parsed && typeof parsed === 'object' ? parsed : {};
  } catch {
    return { [langs.value[0]?.value ?? 'zh-CN']: json };
  }
}

function serialize(): string {
  const map: Record<string, string> = {};
  for (const lang of langs.value) {
    const text = texts.value[lang.value];
    if (text) {
      map[lang.value] = text;
    }
  }
  return Object.keys(map).length > 0 ? JSON.stringify(map) : '';
}

function onChange() {
  const json = serialize();
  if (json === props.modelValue) {
    return;
  }
  emit('update:modelValue', json);
  emit('change', json);
}
</script>
<template>
  <div class="flex w-full flex-col gap-2">
    <Input
      v-for="lang in langs"
      :key="lang.value"
      :disabled="disabled"
      :value="texts[lang.value] ?? ''"
      allow-clear
      @update:value="
        (val: string) => {
          texts[lang.value] = val;
          onChange();
        }
      "
    >
      <template #addonBefore>
        <span class="text-xs text-muted-foreground">{{ lang.label }}</span>
      </template>
    </Input>
  </div>
</template>
