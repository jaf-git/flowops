import i18next from 'i18next';
import { initReactI18next } from 'react-i18next';

import en from './locales/en/common.json';

export const SUPPORTED_LOCALES = ['en'] as const;

export type SupportedLocale = (typeof SUPPORTED_LOCALES)[number];

export const DEFAULT_LOCALE: SupportedLocale = 'en';

export function isSupportedLocale(value: string | undefined): value is SupportedLocale {
  return value !== undefined && SUPPORTED_LOCALES.some((locale) => locale === value);
}

void i18next.use(initReactI18next).init({
  resources: {
    en: { common: en },
  },
  lng: DEFAULT_LOCALE,
  fallbackLng: DEFAULT_LOCALE,
  defaultNS: 'common',
  interpolation: { escapeValue: false },
});

export { i18next };
