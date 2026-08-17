/// <reference types="vite/client" />

import type { DeepslateApi } from '../../preload/index'

declare global {
  interface Window {
    deepslate: DeepslateApi
  }
}

export {}
