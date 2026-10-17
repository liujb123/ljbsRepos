<script setup lang="ts">
import { computed } from 'vue'
import PriceText from './PriceText.vue'
import type { OptionGroupView } from '@/types/api'
import { parseCents } from '@/utils/money'

/**
 * Customization picker (PRD 3.2).
 *
 * Required groups start unselected on purpose: the employee must make an explicit choice, and
 * the server rejects a payload with an unanswered required group anyway.
 */
const props = defineProps<{
  groups: OptionGroupView[]
  selected: number[]
}>()

const emit = defineEmits<{ (event: 'update:selected', value: number[]): void }>()

const selectedSet = computed(() => new Set(props.selected))

function isSelected(optionId: number): boolean {
  return selectedSet.value.has(optionId)
}

function toggle(group: OptionGroupView, optionId: number): void {
  const next = new Set(props.selected)
  if (group.selectionType === 'SINGLE') {
    // Replace whatever was chosen in this single-choice group.
    for (const option of group.options) {
      next.delete(option.optionId)
    }
    next.add(optionId)
  } else if (next.has(optionId)) {
    next.delete(optionId)
  } else {
    next.add(optionId)
  }
  emit('update:selected', [...next])
}

const unansweredRequired = computed(() =>
  props.groups
    .filter((group) => group.required)
    .filter((group) => !group.options.some((option) => selectedSet.value.has(option.optionId)))
)
</script>

<template>
  <div class="space-y-5">
    <fieldset v-for="group in groups" :key="group.groupId" class="border-0 p-0">
      <legend class="mb-2 flex items-center gap-2 text-sm font-semibold text-slate-700">
        {{ group.name }}
        <span
          class="wb-chip"
          :class="group.required ? 'bg-brand-50 text-brand-700' : 'bg-slate-100 text-slate-500'"
        >
          {{ group.required ? 'Required' : 'Optional' }}
        </span>
      </legend>

      <div class="space-y-1">
        <label
          v-for="option in group.options"
          :key="option.optionId"
          class="flex cursor-pointer items-center justify-between rounded-lg border px-3 py-2 text-sm transition-colors"
          :class="[
            isSelected(option.optionId)
              ? 'border-brand-400 bg-brand-50'
              : 'border-slate-200 bg-white hover:bg-slate-50',
            option.available ? '' : 'cursor-not-allowed opacity-50'
          ]"
        >
          <span class="flex items-center gap-2">
            <input
              :type="group.selectionType === 'SINGLE' ? 'radio' : 'checkbox'"
              :name="`group-${group.groupId}`"
              :value="option.optionId"
              :checked="isSelected(option.optionId)"
              :disabled="!option.available"
              class="h-4 w-4 accent-brand-600"
              @change="toggle(group, option.optionId)"
            />
            <span class="text-slate-700">{{ option.name }}</span>
          </span>
          <PriceText
            v-if="parseCents(option.priceDelta) > 0"
            :amount="option.priceDelta"
            size="sm"
          />
        </label>
      </div>
    </fieldset>

    <p v-if="unansweredRequired.length > 0" class="text-xs text-amber-700">
      Please choose an option for "{{ unansweredRequired[0].name }}".
    </p>
  </div>
</template>
