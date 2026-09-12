import { useEffect, type JSX } from 'react';
import { useTranslation } from 'react-i18next';
import { Navigate, Route, Routes, useLocation, useParams, useSearchParams } from 'react-router-dom';

import { ResetPasswordScreen } from '../features/auth';
import { NodeGalleryScreen, OperationsCanvasScreen } from '../features/canvas';
import { AcceptInvitationScreen } from '../features/workspace';
import { DEFAULT_LOCALE, isSupportedLocale } from '../i18n';
import { App, ProcessCanvasSection, TemplateUsageSection } from './App';

function LocalisedApp(): JSX.Element {
  const { locale } = useParams<{ locale: string }>();

  if (!isSupportedLocale(locale)) {
    return <Navigate to={`/${DEFAULT_LOCALE}`} replace />;
  }

  return <App locale={locale} />;
}

function LocalisedInvitation(): JSX.Element {
  const { locale } = useParams<{ locale: string }>();
  const [query] = useSearchParams();
  const { i18n } = useTranslation();
  const supported = isSupportedLocale(locale);

  useEffect(() => {
    if (supported && i18n.language !== locale) {
      void i18n.changeLanguage(locale);
    }
  }, [i18n, locale, supported]);

  if (!supported) {
    return <Navigate to={`/${DEFAULT_LOCALE}`} replace />;
  }

  return <AcceptInvitationScreen token={query.get('token') ?? ''} />;
}

function InvitationWithoutALocale(): JSX.Element {
  const { search } = useLocation();

  return <Navigate to={`/${DEFAULT_LOCALE}/invitation${search}`} replace />;
}

function LocalisedResetPassword(): JSX.Element {
  const { locale } = useParams<{ locale: string }>();
  const [query] = useSearchParams();
  const { i18n } = useTranslation();
  const supported = isSupportedLocale(locale);

  useEffect(() => {
    if (supported && i18n.language !== locale) {
      void i18n.changeLanguage(locale);
    }
  }, [i18n, locale, supported]);

  if (!supported) {
    return <Navigate to={`/${DEFAULT_LOCALE}`} replace />;
  }

  return <ResetPasswordScreen token={query.get('token') ?? ''} />;
}

function ResetPasswordWithoutALocale(): JSX.Element {
  const { search } = useLocation();

  return <Navigate to={`/${DEFAULT_LOCALE}/reset-password${search}`} replace />;
}

function LocalisedNodeGallery(): JSX.Element {
  const { locale } = useParams<{ locale: string }>();
  const { i18n } = useTranslation();
  const supported = isSupportedLocale(locale);

  useEffect(() => {
    if (supported && i18n.language !== locale) {
      void i18n.changeLanguage(locale);
    }
  }, [i18n, locale, supported]);

  if (!supported) {
    return <Navigate to={`/${DEFAULT_LOCALE}/canvas/gallery`} replace />;
  }

  return <NodeGalleryScreen />;
}

function LocalisedOperationsCanvas(): JSX.Element {
  const { locale } = useParams<{ locale: string }>();
  const { i18n } = useTranslation();
  const supported = isSupportedLocale(locale);

  useEffect(() => {
    if (supported && i18n.language !== locale) {
      void i18n.changeLanguage(locale);
    }
  }, [i18n, locale, supported]);

  if (!supported) {
    return <Navigate to={`/${DEFAULT_LOCALE}/canvas/gallery/process`} replace />;
  }

  return <OperationsCanvasScreen />;
}

function LocalisedProcessCanvas(): JSX.Element {
  const { locale, instanceId } = useParams<{ locale: string; instanceId: string }>();
  const { i18n } = useTranslation();
  const supported = isSupportedLocale(locale);

  useEffect(() => {
    if (supported && i18n.language !== locale) {
      void i18n.changeLanguage(locale);
    }
  }, [i18n, locale, supported]);

  if (!supported || instanceId === undefined) {
    return <Navigate to={`/${DEFAULT_LOCALE}`} replace />;
  }

  return (
    <App locale={locale}>
      {(session) => (
        <ProcessCanvasSection session={session} instanceId={instanceId} locale={locale} />
      )}
    </App>
  );
}

function LocalisedTemplateUsage(): JSX.Element {
  const { locale, templateId } = useParams<{ locale: string; templateId: string }>();
  const { i18n } = useTranslation();
  const supported = isSupportedLocale(locale);

  useEffect(() => {
    if (supported && i18n.language !== locale) {
      void i18n.changeLanguage(locale);
    }
  }, [i18n, locale, supported]);

  if (!supported || templateId === undefined) {
    return <Navigate to={`/${DEFAULT_LOCALE}`} replace />;
  }

  return (
    <App locale={locale}>
      {(session) => (
        <TemplateUsageSection session={session} templateId={templateId} locale={locale} />
      )}
    </App>
  );
}

export function AppRoutes(): JSX.Element {
  return (
    <Routes>
      <Route path="/" element={<Navigate to={`/${DEFAULT_LOCALE}`} replace />} />

      <Route path="/invitation" element={<InvitationWithoutALocale />} />
      <Route path="/:locale/invitation" element={<LocalisedInvitation />} />
      <Route path="/reset-password" element={<ResetPasswordWithoutALocale />} />
      <Route path="/:locale/reset-password" element={<LocalisedResetPassword />} />

      <Route path="/:locale/canvas/gallery/process" element={<LocalisedOperationsCanvas />} />
      <Route path="/:locale/canvas/gallery" element={<LocalisedNodeGallery />} />
      <Route path="/:locale/canvas/process/:instanceId" element={<LocalisedProcessCanvas />} />
      <Route path="/:locale/templates/:templateId" element={<LocalisedTemplateUsage />} />
      <Route path="/:locale" element={<LocalisedApp />} />
      <Route path="*" element={<Navigate to={`/${DEFAULT_LOCALE}`} replace />} />
    </Routes>
  );
}
