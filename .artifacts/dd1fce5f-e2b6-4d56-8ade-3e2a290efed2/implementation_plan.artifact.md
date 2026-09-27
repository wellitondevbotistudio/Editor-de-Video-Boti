# Fix All Lint Errors and Warnings

Resolve all ESLint errors and warnings across the codebase to ensure high code quality, prevent potential React lifecycle issues, and remove unused code/variables.

## Proposed Changes

### Component and Route Cleanup
Fix unescaped characters, unused imports/variables, and missing Hook dependencies across various component files.

---

#### [MODIFY] [+not-found.tsx](file:///C:/Users/migue/OneDrive/Área de Trabalho/VideoEditor-main/app/+not-found.tsx)
- Escape the unescaped literal single quote `'` character with `&apos;`.

#### [MODIFY] [index.tsx (tabs)](file:///C:/Users/migue/OneDrive/Área de Trabalho/VideoEditor-main/app/(tabs)/index.tsx)
- Remove unused `FlatList` import.

#### [MODIFY] [premium.tsx (tabs)](file:///C:/Users/migue/OneDrive/Área de Trabalho/VideoEditor-main/app/(tabs)/premium.tsx)
- Remove unused `shadow` destructuring or variable.

#### [MODIFY] [captions.tsx](file:///C:/Users/migue/OneDrive/Área de Trabalho/VideoEditor-main/app/captions.tsx)
- Remove unused `Animated` import.

#### [MODIFY] [onboarding.tsx](file:///C:/Users/migue/OneDrive/Área de Trabalho/VideoEditor-main/app/onboarding.tsx)
- Remove unused `radius` import/variable.

#### [MODIFY] [ColorPanel.tsx](file:///C:/Users/migue/OneDrive/Área de Trabalho/VideoEditor-main/components/editor/ColorPanel.tsx)
- Remove unused `fontSize` and `fontWeight` theme variables.

#### [MODIFY] [EditPanel.tsx](file:///C:/Users/migue/OneDrive/Área de Trabalho/VideoEditor-main/components/editor/EditPanel.tsx)
- Remove unused `Text` import, and unused theme variables (`fontSize`, `fontWeight`, `androidFont`).

#### [MODIFY] [EffectsPanel.tsx](file:///C:/Users/migue/OneDrive/Área de Trabalho/VideoEditor-main/components/editor/EffectsPanel.tsx)
- Remove unused `fontSize` theme variable.

#### [MODIFY] [OverlayPanel.tsx](file:///C:/Users/migue/OneDrive/Área de Trabalho/VideoEditor-main/components/editor/OverlayPanel.tsx)
- Remove unused `fontSize` and `fontWeight` theme variables.

#### [MODIFY] [Timeline.tsx](file:///C:/Users/migue/OneDrive/Área de Trabalho/VideoEditor-main/components/editor/Timeline.tsx)
- Remove unused `radius` and `fontSize` theme variables.

#### [MODIFY] [ToolGrid.tsx](file:///C:/Users/migue/OneDrive/Área de Trabalho/VideoEditor-main/components/editor/ToolGrid.tsx)
- Remove unused `fontSize` theme variable.

#### [MODIFY] [Toolbar.tsx](file:///C:/Users/migue/OneDrive/Área de Trabalho/VideoEditor-main/components/editor/Toolbar.tsx)
- Remove unused `fontSize` theme variable.

#### [MODIFY] [Slider.tsx](file:///C:/Users/migue/OneDrive/Área de Trabalho/VideoEditor-main/components/ui/Slider.tsx)
- Add missing dependency `compute` to the `useMemo` dependency array.

---

## Verification Plan

### Automated Tests
- Run `npx expo lint` via shell to verify that 0 problems remain.
