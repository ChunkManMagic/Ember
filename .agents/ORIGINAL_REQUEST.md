# Original User Request

## 2026-09-27T01:24:59Z

# Teamwork Project Prompt

Audit and overhaul the "Ember" Android app (a media and scenario / roleplay creation companion) to improve its UI, streamline its workflows, and upgrade the quality of its idea generation.

Working directory: /data/data/com.termux/files/home/Ember
Integrity mode: development

## Requirements

### R1. UI/UX and Flow Overhaul
The team must review the current screens and user flows (e.g., `DiscoverScreen`, `ScenarioLabScreen`, `LibraryScreen`) to identify friction points or clunky designs. Implement a comprehensive redesign to make the app more intuitive, modern, and efficient to navigate.

### R2. Generation Logic Improvements
Review the core AI integration and idea generation logic. Improve the underlying prompts, data handling, or architecture to ensure it produces higher-quality, more creative media and scenario ideas.

### R3. Maintain Build Integrity
The Android app must successfully compile using gradle (`assembleDebug`) after all changes are made.

## Acceptance Criteria

### UX & Generation Improvement Verification (Agent-as-Judge)
- [ ] An independent evaluation verifies that the new UI flow is demonstrably more streamlined and visually cohesive than the original baseline.
- [ ] An independent evaluation verifies that the scenario/idea generation output quality is improved or more robust.

### Build Verification
- [ ] Running `./gradlew assembleDebug` in the `Ember` directory completes without errors.
