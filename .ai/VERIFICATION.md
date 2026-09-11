# Verification Record — BUG-002

Status: `COMPLETE`

Codex owns execution evidence here; Claude reviews it against the repo and task contract.

## Feasibility Review

Result: `DESIGN FEASIBLE`

Independent inspection confirmed `app/build.gradle` declared the incomplete coordinate `testImplementation 'junit:junit'`. Both sibling projects checked, ErdMusic and ErdStream, pin `junit:junit:4.13.2`, providing a consistent known-good version. The change remains test-scoped and cannot affect the shipped runtime dependency graph.

## Reproduction

Before editing, with the required Java 17 `JAVA_HOME`, `./gradlew lintDebug` failed at `:app:generateDebugUnitTestLintModel` while resolving `:app:debugUnitTestCompileClasspath`: `Could not find junit:junit:.` Lint analysis did not run.

## Root Cause

The Maven coordinate in `app/build.gradle:94` omitted its version. Gradle therefore attempted to resolve an empty JUnit version and could not construct the debug unit-test lint model.

## Files Changed

- `app/build.gradle`: pin the existing test-only JUnit dependency to `4.13.2`.
- `.ai/BUG_BACKLOG.md`: record two substantive warnings exposed once lint could run; no warning fixes were made.
- `.ai/VERIFICATION.md`: record BUG-002 reproduction and verification evidence.

## Regression Test

No test source or framework was added, as required by the contract. The direct regression check is successful execution of `lintDebug`; `testDebugUnitTest` also confirms the test configuration now resolves and remains `NO-SOURCE`.

## Commands Executed

Every Gradle invocation first used:

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
```

Relevant commands:

```bash
./gradlew lintDebug
./gradlew lintDebug assembleDebug assembleRelease testDebugUnitTest
rg -n "junit:junit(:|\\\")|junitVersion|junit.*4\\.13" /Users/david/Projects/ErdMusic/app/build.gradle.kts /Users/david/Projects/ErdStream -g 'build.gradle' -g 'build.gradle.kts' -g 'libs.versions.toml'
git diff --check
git diff -- app/build.gradle .ai/BUG_BACKLOG.md .ai/VERIFICATION.md
```

## Results

- Reproduction: PASS. The unmodified project failed exactly at `generateDebugUnitTestLintModel` with `Could not find junit:junit:.`.
- Version consistency: PASS. ErdMusic and ErdStream both use JUnit `4.13.2`.
- Build: PASS. `assembleDebug` and `assembleRelease` completed in the combined Gradle run.
- Tests: PASS. `testDebugUnitTest NO-SOURCE`, as expected because the project has no test sources.
- Lint: PASS. `lintDebug` completed, generated its HTML/text/XML reports, and reported 0 errors and 27 warnings.
- Runtime/device verification: Not required; only a `testImplementation` dependency changed.
- Diff hygiene: `git diff --check` passed before the verification record was finalized; final diff inspection follows this write.

## Residual Risk

Lint reports that an obsolete Compose lint registry causes ten Compose checks to be skipped, so the successful run is not complete coverage of those checks. It also reports direct battery-optimization exemption requests as Play policy warnings. Both are recorded in the backlog and intentionally not fixed under BUG-002.

## Contract Deviations

None. No lint warning was fixed, no Android test dependency was touched, and no runtime code changed.

READY FOR CLAUDE ADVERSARIAL REVIEW
