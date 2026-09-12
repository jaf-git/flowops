export { SuggestWorkPanel } from './components/SuggestWorkPanel';
export { ProposeWorkDialog } from './components/ProposeWorkDialog';
export { useWorkSuggestion } from './hooks/useWorkSuggestion';
export { useCreateAgreedWork } from './hooks/useCreateAgreedWork';
export { suggestWorkIn, startProcessFromDescriptions } from './api/chatAssistApi';
export type {
  SuggestedStep,
  WorkSuggestion,
  FieldSource,
  Sourced,
  AgreedWork,
} from './api/chatAssistApi';
