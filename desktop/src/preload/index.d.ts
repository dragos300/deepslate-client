import type { DeepslateApi } from './index'

declare global {
  interface Window {
    deepslate: DeepslateApi
  }
}

export {}
