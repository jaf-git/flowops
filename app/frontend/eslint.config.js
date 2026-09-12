import js from '@eslint/js';
import reactHooks from 'eslint-plugin-react-hooks';
import reactRefresh from 'eslint-plugin-react-refresh';
import globals from 'globals';
import tseslint from 'typescript-eslint';

/*
  ARCHITECTURE-01's frontend boundaries, enforced rather than described.

  The layout section has always closed with "these are lint boundary rules in the pipeline, not
  conventions", and until now they were conventions: a deep import into another feature's folder
  passed CI, and the only thing between the codebase and one was somebody noticing in review. An
  unenforced structural rule decays silently — that is the whole argument for the backend's ArchUnit
  suite, and the frontend had the argument without the tests.

  Two things to know before editing.

  ESLint's later configuration blocks *replace* a rule's options rather than merging them, so each
  block below states every pattern that applies to its files. The constants exist so that stays true
  by construction rather than by memory.

  And the two rules are not interchangeable. `no-restricted-imports` matches gitignore-style globs,
  where `*` happily matches `..` and there is no way to say "not `..`" — which is precisely what a
  relative crossing needs, since `../../auth/components/X` and `../../../shared/ui/Button` differ
  only there. Those two rules are therefore written as syntax selectors with a real regular
  expression. Both formulations were checked by planting a crossing and watching it be refused; the
  glob version passed the plant, which is how the difference was found rather than assumed.
*/
const PUBLIC_SURFACE_ONLY = {
  group: ['**/features/*/*', '**/features/*/*/**'],
  message:
    'A feature is reached through its index and nowhere else (ARCHITECTURE-01). Import the feature itself, and export what you need from its index.',
};

/*
  Stated as "no file inside a feature imports any feature", rather than naming the sibling. Naming
  siblings would mean editing this file every time a feature is added, and the one that got forgotten
  would be the one that crossed. A feature's own files reach each other relatively and never spell
  `features/` at all, so this only ever fires on a crossing.
*/
const NO_SIBLING_FEATURE = {
  group: ['**/features/*'],
  message:
    'No feature imports another (ARCHITECTURE-01). Where two must meet, they meet in `app` — or the shared thing moves to `shared`.',
};

const INWARD_ONLY = {
  group: ['../components/*', '../routes/*', './components/*', './routes/*'],
  message:
    'Inside a feature the direction is routes and components inward to hooks, and hooks to api and model (ARCHITECTURE-01). The inner layers do not reach back out.',
};

const MODEL_DEPENDS_ON_NOTHING = {
  group: ['../components/*', '../routes/*', '../hooks/*', '../api/*'],
  message:
    "A feature's model depends on nothing (ARCHITECTURE-01) — that is what makes its rules testable without rendering anything.",
};

const NAMED_EXPORTS_ONLY = {
  // A name is stable across the codebase (CODING-CONVENTIONS-01). Configuration files are outside
  // `src` because their tooling requires a default export.
  selector: 'ExportDefaultDeclaration',
  message: 'Use a named export.',
};

/*
  The same crossing as NO_SIBLING_FEATURE, spelled relatively.

  A file inside a feature has a shorter way to reach its neighbour than naming the features
  directory: from `features/workspace/components/`, the auth feature is `../../auth/components/…`,
  which mentions no feature at all. Two levels up from a layer folder *is* the features directory,
  and nothing legitimate lives there — everything genuinely shared is one level further out, under
  `shared/` or `i18n/`. So exactly two levels up from a layer, and exactly one from a feature's own
  index, is a crossing by construction, and the negative lookahead is what makes it "exactly".

  Re-exports are covered as well as imports: an index that writes `export { x } from '../auth'` has
  made the same crossing, and it is the file most likely to try.
*/
function crossesToASibling(levels, message) {
  const upOneLevel = '\\.\\.\\/';
  const source = `/^${upOneLevel.repeat(levels)}(?!\\.\\.)/`;

  return ['ImportDeclaration', 'ExportNamedDeclaration', 'ExportAllDeclaration'].map((node) => ({
    selector: `${node}[source.value=${source}]`,
    message,
  }));
}

const FROM_A_LAYER = crossesToASibling(
  2,
  'That is the feature next door, reached the short way (ARCHITECTURE-01). Features meet in `app`; genuinely shared things live in `shared`.',
);

const FROM_A_FEATURE_INDEX = crossesToASibling(
  1,
  'That is the feature next door (ARCHITECTURE-01). A feature index exposes its own folder and nobody else’s.',
);

/*
  DESIGN-SYSTEM-01 §15, enforced rather than described: *no component introduces a raw hex. Every
  value resolves to a token.*

  This exists because of how the Volt migration is being run. Thirteen agents are restyling a hundred
  and thirty files at once, and a literal colour is the one defect in that work that is invisible
  afterwards — a wrong gap is visible on the screen, a wrong `#f5f6f9` looks exactly right until the
  token behind it moves and one component silently stops following. `designTokens.test.ts` guards the
  token file and can say nothing at all about a component that declined to use it; this is the other
  half of that guarantee.

  **Hex only, and px and ms are deliberately not here.** The colour rule caught three files across the
  whole codebase, so it costs nothing; a `px` rule would fire on every SVG dimension, icon size and
  viewBox in the product and would be turned off within the day. A rule that gets disabled is worse
  than no rule, because the codebase then carries the claim without the check. Spacing drift is
  visible on the screen and is caught by the review that looks at the screen.

  Scoped to `src/**` and not to configuration or tests: a test asserting a computed colour has to
  name one, and `designTokens.test.ts`'s entire job is reading them.
*/
const NO_RAW_COLOUR = {
  selector: 'Literal[value=/#[0-9a-fA-F]{3}([0-9a-fA-F]{3})?\\b/]',
  message:
    'No component carries a literal colour (DESIGN-SYSTEM-01 §15). Use a token — `var(--ink-700)`, `var(--volt-500)` — and if none fits, the token is missing and belongs in index.css with its contrast row in designTokens.test.ts.',
};

function boundaries(patterns, syntax = []) {
  return {
    'no-restricted-imports': ['error', { patterns }],
    'no-restricted-syntax': ['error', NAMED_EXPORTS_ONLY, NO_RAW_COLOUR, ...syntax],
  };
}

export default tseslint.config(
  { ignores: ['dist', 'node_modules'] },
  {
    files: ['**/*.{ts,tsx}'],
    extends: [js.configs.recommended, ...tseslint.configs.recommended],
    languageOptions: {
      ecmaVersion: 2022,
      globals: globals.browser,
    },
    plugins: {
      'react-hooks': reactHooks,
      'react-refresh': reactRefresh,
    },
    rules: {
      ...reactHooks.configs.recommended.rules,

      'no-empty': ['error', { allowEmptyCatch: true }],
      'react-refresh/only-export-components': ['warn', { allowConstantExport: true }],
      '@typescript-eslint/no-explicit-any': 'error',
      '@typescript-eslint/explicit-module-boundary-types': 'error',
    },
  },
  {
    files: ['src/**/*.{ts,tsx}'],
    rules: boundaries([PUBLIC_SURFACE_ONLY]),
  },
  {
    files: ['src/features/**/*.{ts,tsx}'],
    rules: boundaries([PUBLIC_SURFACE_ONLY, NO_SIBLING_FEATURE]),
  },
  {
    // A feature's own root: its index, and nothing else belongs here.
    files: ['src/features/*/*.{ts,tsx}'],
    rules: boundaries([PUBLIC_SURFACE_ONLY, NO_SIBLING_FEATURE], FROM_A_FEATURE_INDEX),
  },
  {
    files: ['src/features/*/*/**/*.{ts,tsx}'],
    rules: boundaries([PUBLIC_SURFACE_ONLY, NO_SIBLING_FEATURE], FROM_A_LAYER),
  },
  {
    files: ['src/features/*/hooks/**/*.{ts,tsx}', 'src/features/*/api/**/*.{ts,tsx}'],
    rules: boundaries([PUBLIC_SURFACE_ONLY, NO_SIBLING_FEATURE, INWARD_ONLY], FROM_A_LAYER),
  },
  {
    files: ['src/features/*/model/**/*.{ts,tsx}'],
    rules: boundaries(
      [PUBLIC_SURFACE_ONLY, NO_SIBLING_FEATURE, MODEL_DEPENDS_ON_NOTHING],
      FROM_A_LAYER,
    ),
  },
  {
    // `shared` imports from nothing above it. A primitive that knows a feature is not a primitive.
    files: ['src/shared/**/*.{ts,tsx}'],
    rules: boundaries([
      {
        group: ['**/features/**', '**/app/**'],
        message:
          '`shared` is cross-feature and genuinely global (ARCHITECTURE-01). It may not know that a particular feature exists.',
      },
    ]),
  },
);
