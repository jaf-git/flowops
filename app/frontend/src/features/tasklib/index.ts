export { TaskTemplatesScreen } from './routes/TaskTemplatesScreen';
export { TemplateUsageScreen } from './routes/TemplateUsageScreen';

export { TemplateSuggestionStrip } from './components/TemplateSuggestionStrip';
export { useStampTask, useTemplateSuggestions } from './hooks/useTaskTemplates';

export { fetchTemplate, fetchTemplateLibrary, fetchTemplateUsage } from './api/taskTemplateApi';
export { TemplatePreviewDialog } from './components/TemplatePreviewDialog';
