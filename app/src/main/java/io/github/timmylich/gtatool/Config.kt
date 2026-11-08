/** Support channels. No payment SDK is involved: the app only opens a link or copies an address. */
package io.github.timmylich.gtatool

const val REPO_URL = "https://github.com/timmylich/gta-universal-tool"
const val ISSUES_URL = "https://github.com/timmylich/gta-universal-tool/issues"
const val TELEGRAM_URL = "https://t.me/timmylich_portfolio"

/** Tribute (cards, crypto, SBP): inside Telegram or in the browser. */
const val TRIBUTE_TELEGRAM_URL = "https://t.me/tribute/app?startapp=dRVs"
const val TRIBUTE_WEB_URL = "https://web.tribute.tg/d/RVs"

/** [networks]: names shown under the title. */
data class Wallet(val id: String, val networks: String, val address: String)

val WALLETS = listOf(
  // One EVM address works on every EVM-compatible network.
  Wallet("evm", "Ethereum · BNB Chain · Base · Arbitrum · Optimism · Polygon", "0xbd6921cc74513fd99037d7ffc2da8296886a05cd"),
  Wallet("sol", "Solana (SOL, USDC, USDT)", "J6FiRPDzLkvbMWZ1MEsvUafJzkHNZB7W5BnQnRmN8nxc"),
  Wallet("tron", "TRON (TRX, USDT TRC-20)", "TGbhpyk51XGBapxJUZc7L4Wfvraf1k92Uu"),
  Wallet("ton", "TON · Gram (wallet W5)", "UQAAhZQ50_KoTa7Dw-BdQRybSlD15jifdHH-h1fB-ldvJVGs"),
)
