# Phase 2 — Jenkins single-pipeline bot commit (DRAFT)

Status: **Design complete, not yet deployed**. Applying this to `api/Jenkinsfile` requires:
1. Jenkins bot with git push rights to `main` (or configure a PAT/SSH deploy key)
2. `jq` available on Jenkins agent (already on dev server — verified 2026-04-19)
3. Decision on whether to run before or after the existing Deploy stage

## Goal

When backend DTOs change → Jenkins regenerates `api/openapi.json` +
`frontend/src/api/schema.d.ts` + `admin/src/api/schema.d.ts` and commits them to `main`
automatically, so the frontend/admin pipelines always build against a synced spec.

This eliminates the tri-pipeline race condition (backend ships a DTO change, frontend CI
runs against stale spec). The api Jenkins job is now the **single owner of the spec
artifact** per v3 plan.

## Proposed stages (insert between Build and Deploy)

```groovy
stage('Verify OpenAPI Spec') {
    steps {
        dir('api') {
            sh '''#!/bin/bash
                mkdir -p build
                cp openapi.json build/openapi-committed.json
                ./gradlew verifyOpenApiSpec
            '''
        }
    }
}

stage('Regenerate TS Types') {
    when {
        // Only run on branches we push to (skip PR builds)
        expression { env.BRANCH_NAME == 'main' || env.GIT_BRANCH == 'origin/main' }
    }
    steps {
        script {
            def specChanged = sh(
                script: "cd api && ./gradlew generateOpenApiDocs -q && git diff --quiet openapi.json",
                returnStatus: true
            ) != 0

            if (specChanged) {
                sh '''#!/bin/bash
                    set -e
                    cd frontend && npm ci --silent && npm run generate:api && cd ..
                    cd admin && npm ci --silent && npm run generate:api && cd ..
                '''
            } else {
                echo "openapi.json unchanged — skipping TS regen"
            }
        }
    }
}

stage('Bot Commit (spec sync)') {
    when {
        expression { env.BRANCH_NAME == 'main' || env.GIT_BRANCH == 'origin/main' }
    }
    steps {
        sh '''#!/bin/bash
            set -e
            if git diff --quiet api/openapi.json frontend/src/api/schema.d.ts admin/src/api/schema.d.ts 2>/dev/null; then
                echo "No spec/types drift — nothing to commit"
                exit 0
            fi

            git config user.name  "goldpet-jenkins-bot"
            git config user.email "jenkins-bot@goldpet.com"

            git add api/openapi.json frontend/src/api/schema.d.ts admin/src/api/schema.d.ts

            # Rebase + retry to handle concurrent pushes to main during build
            for attempt in 1 2 3; do
                if git pull --rebase --autostash origin main &&
                   git commit --allow-empty -m "chore(openapi): regenerate spec + ts types [bot]" &&
                   git push origin HEAD:main; then
                    echo "✓ Bot commit pushed on attempt $attempt"
                    exit 0
                fi
                git rebase --abort 2>/dev/null || true
                sleep $((attempt * 5))
            done

            echo "✗ Bot commit failed after 3 attempts — intervention required"
            exit 1
        '''
    }
}
```

## Alternative: Jenkins lockable-resources plugin

If the rebase loop turns out to be flaky, replace with:

```groovy
lock('openapi-bot-commit') {
    // commit + push without retry — plugin serializes concurrent access
}
```

## Edge cases

- **First-time run**: If `api/build/openapi-committed.json` is missing and verify runs
  before the bot commit stage, verify will fail (expected — prompt user to commit).
  Mitigation: the Regen stage runs `generateOpenApiDocs` independently from verify,
  so Bot Commit only needs a fresh `api/openapi.json` from the prior stage.
- **Flyway migration + spec drift**: If a backend migration introduces a new table but
  no DTO change, spec is unchanged. Gate passes. Good.
- **PR builds**: `when { expression }` skips bot commit on non-main builds. PR bots
  should use a separate status check that runs verify only.

## Validation plan before deploy

1. Dry-run the Regen stage on a test branch (create PR with a deliberate DTO change,
   confirm Jenkins detects it and would commit)
2. Grant bot push permission (GitHub deploy key or PAT)
3. Merge the Jenkinsfile changes during a maintenance window
4. Watch 3-5 builds for race conditions

Target deployment: after Phase 3 first domain migration (auth) merges, to avoid
coupling unrelated infra change with domain work.
