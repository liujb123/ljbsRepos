<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { adminApi } from '@/api/admin'
import { useUiStore } from '@/stores/ui'
import type {
  AdminOptionGroupView,
  Allergen,
  ApiError,
  DishCategory,
  ProteinType,
  SelectionType,
  SpiceLevel
} from '@/types/api'
import { ALLERGENS, DISH_CATEGORIES, PROTEIN_TYPES, SPICE_LEVELS } from '@/types/api'
import { centsToAmount, parseCents } from '@/utils/money'

const props = defineProps<{ id?: string }>()

const router = useRouter()
const ui = useUiStore()

const isEdit = computed(() => Boolean(props.id))
const loading = ref(false)
const saving = ref(false)
const uploading = ref(false)
const formError = ref<string | null>(null)

const name = ref('')
const description = ref('')
const price = ref('')
const category = ref<DishCategory>('Chinese')
const spiceLevel = ref<SpiceLevel>('None')
const imageUrl = ref<string | null>(null)
const available = ref(true)
const allergens = ref<Allergen[]>([])
const proteins = ref<ProteinType[]>([])
const optionGroups = ref<AdminOptionGroupView[]>([])

function toggle<T>(list: T[], value: T): T[] {
  return list.includes(value) ? list.filter((item) => item !== value) : [...list, value]
}

function addGroup(): void {
  optionGroups.value.push({
    id: null,
    name: '',
    required: false,
    selectionType: 'SINGLE',
    options: [{ id: null, name: '', priceDelta: '0.00', available: true }]
  })
}

function removeGroup(index: number): void {
  optionGroups.value.splice(index, 1)
}

function addOption(group: AdminOptionGroupView): void {
  group.options.push({ id: null, name: '', priceDelta: '0.00', available: true })
}

function removeOption(group: AdminOptionGroupView, index: number): void {
  group.options.splice(index, 1)
}

onMounted(async () => {
  if (!props.id) {
    addGroup()
    return
  }
  loading.value = true
  try {
    const dish = await adminApi.getDish(Number(props.id))
    name.value = dish.name
    description.value = dish.description
    price.value = centsToAmount(parseCents(dish.price))
    category.value = dish.category
    spiceLevel.value = dish.spiceLevel
    imageUrl.value = dish.imageUrl
    available.value = dish.available
    allergens.value = [...dish.allergens]
    proteins.value = [...dish.proteins]
    optionGroups.value = dish.optionGroups.map((group) => ({
      id: group.id,
      name: group.name,
      required: group.required,
      selectionType: group.selectionType,
      options: group.options.map((option) => ({
        id: option.id,
        name: option.name,
        priceDelta: centsToAmount(parseCents(option.priceDelta)),
        available: option.available
      }))
    }))
  } catch (error) {
    formError.value = (error as ApiError).message
  } finally {
    loading.value = false
  }
})

async function onUpload(event: Event): Promise<void> {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return
  uploading.value = true
  try {
    const result = await adminApi.uploadImage(file)
    imageUrl.value = result.url
    ui.success('Image uploaded.')
  } catch (error) {
    ui.error((error as ApiError).message)
  } finally {
    uploading.value = false
    input.value = ''
  }
}

function validate(): string | null {
  if (!name.value.trim()) return 'Dish name is required.'
  if (!description.value.trim()) return 'Description is required.'
  if (!price.value || parseCents(price.value) <= 0) return 'Price must be greater than 0.'
  for (const group of optionGroups.value) {
    if (!group.name.trim()) return 'Every option group needs a name.'
    if (group.options.length === 0) return 'A required group must have at least one option.'
    for (const option of group.options) {
      if (!option.name.trim()) return 'Every option needs a name.'
    }
  }
  return null
}

async function save(): Promise<void> {
  if (saving.value) return
  const validationError = validate()
  if (validationError) {
    formError.value = validationError
    return
  }
  formError.value = null
  saving.value = true
  try {
    const payload = {
      name: name.value.trim(),
      description: description.value.trim(),
      price: centsToAmount(parseCents(price.value)),
      category: category.value,
      spiceLevel: spiceLevel.value,
      imageUrl: imageUrl.value,
      allergens: allergens.value,
      proteins: proteins.value,
      available: available.value,
      optionGroups: optionGroups.value.map((group) => ({
        id: group.id,
        name: group.name.trim(),
        required: group.required,
        selectionType: group.selectionType,
        options: group.options.map((option) => ({
          id: option.id,
          name: option.name.trim(),
          priceDelta: centsToAmount(parseCents(option.priceDelta)),
          available: option.available
        }))
      }))
    }
    if (isEdit.value) {
      await adminApi.updateDish(Number(props.id), payload)
      ui.success('Dish saved.')
    } else {
      await adminApi.createDish(payload)
      ui.success('Dish created.')
    }
    await router.push({ name: 'console-dishes' })
  } catch (error) {
    formError.value = (error as ApiError).message
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <section class="mx-auto max-w-3xl space-y-5">
    <header class="flex items-center justify-between gap-3">
      <h1 class="text-xl font-semibold text-slate-800">
        {{ isEdit ? 'Edit dish' : 'Add dish' }}
      </h1>
      <button type="button" class="wb-btn-secondary" @click="router.back()">Cancel</button>
    </header>

    <div v-if="loading" class="wb-card animate-pulse p-6">
      <div class="h-5 w-1/2 rounded bg-slate-200"></div>
    </div>

    <form v-else class="space-y-5" novalidate @submit.prevent="save">
      <div class="wb-card space-y-4 p-5">
        <div>
          <label class="wb-label" for="dish-name">Name</label>
          <input id="dish-name" v-model="name" type="text" class="wb-input" maxlength="100" />
        </div>

        <div>
          <label class="wb-label" for="dish-description">Description</label>
          <textarea
            id="dish-description"
            v-model="description"
            class="wb-input"
            rows="3"
            maxlength="500"
          ></textarea>
        </div>

        <div class="grid gap-4 sm:grid-cols-3">
          <div>
            <label class="wb-label" for="dish-price">Price</label>
            <input
              id="dish-price"
              v-model="price"
              type="number"
              min="0.01"
              step="0.01"
              class="wb-input"
              placeholder="22.50"
            />
          </div>
          <div>
            <label class="wb-label" for="dish-category">Category</label>
            <select id="dish-category" v-model="category" class="wb-input">
              <option v-for="value in DISH_CATEGORIES" :key="value" :value="value">{{ value }}</option>
            </select>
          </div>
          <div>
            <label class="wb-label" for="dish-spice">Spice level</label>
            <select id="dish-spice" v-model="spiceLevel" class="wb-input">
              <option v-for="value in SPICE_LEVELS" :key="value" :value="value">{{ value }}</option>
            </select>
          </div>
        </div>

        <div>
          <span class="wb-label">Allergens</span>
          <div class="flex flex-wrap gap-2">
            <button
              v-for="value in ALLERGENS"
              :key="value"
              type="button"
              class="wb-chip border"
              :class="
                allergens.includes(value)
                  ? 'border-brand-500 bg-brand-600 text-white'
                  : 'border-slate-300 bg-white text-slate-600'
              "
              @click="allergens = toggle(allergens, value)"
            >
              {{ value }}
            </button>
          </div>
        </div>

        <div>
          <span class="wb-label">Protein</span>
          <div class="flex flex-wrap gap-2">
            <button
              v-for="value in PROTEIN_TYPES"
              :key="value"
              type="button"
              class="wb-chip border"
              :class="
                proteins.includes(value)
                  ? 'border-brand-500 bg-brand-600 text-white'
                  : 'border-slate-300 bg-white text-slate-600'
              "
              @click="proteins = toggle(proteins, value)"
            >
              {{ value }}
            </button>
          </div>
        </div>

        <div class="grid gap-4 sm:grid-cols-2">
          <div>
            <span class="wb-label">Image</span>
            <div class="flex items-center gap-3">
              <div class="h-16 w-24 shrink-0 overflow-hidden rounded bg-slate-100">
                <img
                  v-if="imageUrl"
                  :src="imageUrl"
                  alt="Dish preview"
                  class="h-full w-full object-cover"
                />
              </div>
              <label class="wb-btn-secondary cursor-pointer">
                {{ uploading ? 'Uploading...' : 'Upload image' }}
                <input
                  type="file"
                  accept="image/jpeg,image/png,image/webp"
                  class="hidden"
                  :disabled="uploading"
                  @change="onUpload"
                />
              </label>
            </div>
            <p class="mt-1 text-xs text-slate-500">
              Image must be a JPG, PNG, or WebP file under 2 MB.
            </p>
          </div>

          <div>
            <span class="wb-label">Availability</span>
            <label class="flex items-center gap-2 text-sm text-slate-700">
              <input v-model="available" type="checkbox" class="h-4 w-4 accent-brand-600" />
              Available to employees
            </label>
          </div>
        </div>
      </div>

      <div class="wb-card space-y-4 p-5">
        <div class="flex items-center justify-between">
          <h2 class="text-base font-semibold text-slate-800">Option groups</h2>
          <button type="button" class="wb-btn-secondary" @click="addGroup">Add option group</button>
        </div>

        <div v-if="optionGroups.length === 0" class="text-sm text-slate-500">
          This dish has no customization options.
        </div>

        <div
          v-for="(group, groupIndex) in optionGroups"
          :key="groupIndex"
          class="space-y-3 rounded-lg border border-slate-200 p-4"
        >
          <div class="grid gap-3 sm:grid-cols-3">
            <div>
              <label class="wb-label">Group name</label>
              <input v-model="group.name" type="text" class="wb-input" maxlength="60" />
            </div>
            <div>
              <label class="wb-label">Selection type</label>
              <select v-model="group.selectionType" class="wb-input">
                <option :value="'SINGLE' as SelectionType">Single choice</option>
                <option :value="'MULTI' as SelectionType">Multiple choice</option>
              </select>
            </div>
            <div class="flex items-end justify-between gap-2">
              <label class="flex items-center gap-2 pb-2 text-sm text-slate-700">
                <input v-model="group.required" type="checkbox" class="h-4 w-4 accent-brand-600" />
                Required
              </label>
              <button type="button" class="wb-btn-danger" @click="removeGroup(groupIndex)">
                Remove
              </button>
            </div>
          </div>

          <div class="space-y-2">
            <div
              v-for="(option, optionIndex) in group.options"
              :key="optionIndex"
              class="flex flex-wrap items-center gap-2"
            >
              <input
                v-model="option.name"
                type="text"
                class="wb-input flex-1"
                placeholder="Option name"
                maxlength="60"
              />
              <input
                v-model="option.priceDelta"
                type="number"
                min="0"
                step="0.01"
                class="wb-input w-28"
                placeholder="Extra price"
              />
              <label class="flex items-center gap-1 text-xs text-slate-600">
                <input v-model="option.available" type="checkbox" class="h-4 w-4 accent-brand-600" />
                Available
              </label>
              <button type="button" class="wb-btn-secondary" @click="removeOption(group, optionIndex)">
                Remove
              </button>
            </div>
            <button type="button" class="wb-btn-secondary" @click="addOption(group)">Add option</button>
          </div>
        </div>
      </div>

      <p v-if="formError" class="rounded-lg bg-red-50 px-4 py-3 text-sm text-red-700">
        {{ formError }}
      </p>

      <div class="flex justify-end gap-2">
        <button type="button" class="wb-btn-secondary" @click="router.push({ name: 'console-dishes' })">
          Cancel
        </button>
        <button type="submit" class="wb-btn-primary" :disabled="saving">
          {{ saving ? 'Saving...' : 'Save' }}
        </button>
      </div>
    </form>
  </section>
</template>
