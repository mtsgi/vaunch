import { createVaporApp } from "vue";
import App from "./App.vue";
import "./styles/main.css";

type RootVaporComponent = Parameters<typeof createVaporApp>[0];

// Vize@0.797.0 の型チェッカー (Corsa) は `<script setup vapor>` の `.vue` を
// 通常の VDOM コンポーネント型として生成する一方、コンパイラ出力は defineVaporComponent(__vapor: true) となる。
// そのためエントリポイントのみ明示的な型適合を行い、詳細を README の互換性メモに記録する。
createVaporApp(App as unknown as RootVaporComponent).mount("#app");
