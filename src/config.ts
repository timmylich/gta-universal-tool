/**
 * Support channels. No payment SDK is involved: the app only opens a link or copies an address.
 */
export const REPO_URL = 'https://github.com/timmylich/gta-universal-tool';
export const ISSUES_URL = 'https://github.com/timmylich/gta-universal-tool/issues';
export const TELEGRAM_URL = 'https://t.me/timmylich_portfolio';

/** Tribute (cards, crypto, SBP): inside Telegram or in the browser. */
export const TRIBUTE_TELEGRAM_URL = 'https://t.me/tribute/app?startapp=dRVs';
export const TRIBUTE_WEB_URL = 'https://web.tribute.tg/d/RVs';

export interface Wallet {
  id: 'evm' | 'sol' | 'tron' | 'ton';
  /** Network names shown under the title. */
  networks: string;
  address: string;
}

export const WALLETS: Wallet[] = [
  // One EVM address works on every EVM-compatible network.
  { id: 'evm', networks: 'Ethereum · BNB Chain · Base · Arbitrum · Optimism · Polygon', address: '0xbd6921cc74513fd99037d7ffc2da8296886a05cd' },
  { id: 'sol', networks: 'Solana (SOL, USDC, USDT)', address: 'J6FiRPDzLkvbMWZ1MEsvUafJzkHNZB7W5BnQnRmN8nxc' },
  { id: 'tron', networks: 'TRON (TRX, USDT TRC-20)', address: 'TGbhpyk51XGBapxJUZc7L4Wfvraf1k92Uu' },
  { id: 'ton', networks: 'TON · Gram (wallet W5)', address: 'UQAAhZQ50_KoTa7Dw-BdQRybSlD15jifdHH-h1fB-ldvJVGs' },
];
