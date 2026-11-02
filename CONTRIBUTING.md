# Contributing to KinexMed

Thank you for contributing to KinexMed. As a clinical movement intelligence platform, all contributions must uphold strict standards for biomechanical validity, software security, and engineering rigor.

---

## 1. Development Standards & Commit Hygiene

### Conventional Commits
All commit messages must follow the Conventional Commits specification:
- Format: `<type>(<scope>): <short imperative subject>`
- Allowed types: `feat`, `fix`, `docs`, `style`, `refactor`, `perf`, `test`, `build`, `ci`, `chore`.
- Provide a multi-line body explaining the problem context, engineering approach, and impact scope.

### Cryptographic Signing
All commits must be cryptographically signed using SSH or GPG keys:
```bash
git config commit.gpgsign true
git commit -S -m "feat: your commit message"
```

### Style & Zero-Emoji Standard
- **Code & Messages:** Maintain a professional, clean standard. Strictly zero emojis across code, comments, documentation, and commit messages.
- **Language Runtimes:** Kotlin (Android SDK 34), Python 3.12 (FastAPI), TypeScript 5+ (React 18 / Vite).

---

## 2. Pre-Commit Verification Checklist

Before opening a pull request, ensure the entire multi-platform test suite passes locally:

```bash
# 1. Backend API & Playwright E2E Tests
pytest backend/tests -v

# 2. Android Unit Tests
cd android
./gradlew testDebugUnitTest
cd ..

# 3. Web Clinician Portal Verification
cd web
pnpm run build
pnpm run lint
cd ..
```

---

## 3. Pull Request Guidelines

1. **Target Feature Branches:** Open PRs against `main`. Do not push directly to `main`.
2. **Deterministic Evidence:** Any modification to exercise FSMs or geometry calculations must include unit test assertions covering valid repetitions, incomplete attempts, and low-confidence occlusions.
3. **CI Status:** All three GitHub Actions CI jobs (`android-unit-tests`, `web-check`, `backend-and-e2e`) must pass with green status before merging.
