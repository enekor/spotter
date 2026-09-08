# SDD ledger — plan: docs/superpowers/plans/2026-09-08-remove-ai-programmatic-features.md

Base commit: 8b9ecc8 (branch feat/remove-ai-programmatic-features)
Mode: subagents edit only (no gradle, no git). User runs tests + commits at the end.

## Progress
Task 1: complete (WorkoutComparison.kt, WorkoutComparator.kt, WorkoutComparatorTest.kt) — no edits committed (commit at end)
Task 2: complete (WorkoutViewModel.kt, WorkoutScreen.kt, WorkoutDetailScreen.kt) — AI summary replaced by programmatic comparison; no leftover refs
Task 3: complete (NextExerciseSuggester.kt+test, WorkoutDao.kt, WorkoutRepository.kt, WorkoutViewModel.kt, WorkoutScreen.kt) — ghost suggestion card; old next-set AI suggestion removed; WorkoutViewModel no longer imports ai/
Task 4: complete (Routes.kt, SpotterNavGraph.kt, Home/WorkoutHub/Workout/History/Stats/Weight screens, deleted ui/chat/ChatScreen.kt incl inline ChatViewModel) — chat removed, zero refs. WeightScreen still has AutoAwesome for AI analysis card (removed in Task 5)
Task 5: complete (HomeViewModel uses MotivationalMessages.forHomeScreen; WeightViewModel/Screen AI analysis removed; deleted ui/health/HealthScreen.kt+HealthViewModel.kt; Routes/NavGraph/Stats/Settings onOpenHealth removed). KEPT HealthConnectManager + syncAllWithHC (non-AI metrics). importFromHealthConnect now orphaned but left (no schema impact). Remaining ai refs only in settings (Task 6)
Task 6: complete (SettingsRepository + SettingsScreen AI settings removed; build.gradle.kts dropped GROQ_API_KEY + Retrofit/OkHttp; local.properties.example + README + BUILD.md cleaned; deleted ai/ package). Global grep: zero com.n3k0chan.spotter.ai / Groq / GROQ_API_KEY in app/src. buildConfig left enabled.
All tasks implemented.
Final whole-branch review (opus): SHIP — no Critical/Important. 3 minors.
Minor cleanup applied by controller: removed dead Groq/chat strings (strings.xml) + orphaned retrofit/okhttp catalog aliases (libs.versions.toml). Unused-import minors left (warnings only).
DONE. Pending: user runs tests/build + commits.
