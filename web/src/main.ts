import { createApp } from 'vue'
import { createPinia } from 'pinia'
import App from './App.vue'
import router from './router'
import './assets/styles/main.css'

const app = createApp(App)

// Pinia 必须先于 router 安装，守卫中才能读取 store
app.use(createPinia())
app.use(router)
app.mount('#app')
