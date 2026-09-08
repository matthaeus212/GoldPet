import { Project, SyntaxKind, CallExpression, Node } from 'ts-morph'
import * as fs from 'fs'
import * as path from 'path'

const IS_APPLY = process.argv.includes('--apply')

const SUCCESS_RE = /성공|완료|저장|등록|추가|삭제.*완료|수정.*완료|업데이트.*완료|승인|반영/
const ERROR_RE = /실패|오류|에러|error|Error|잘못|없습니다|부족|초과|만료|취소되었/

type Category = 'success' | 'error' | 'info' | 'review' | 'confirm'

interface Site {
  file: string
  absPath: string
  line: number
  originalText: string
  newText: string
  category: Category
  note?: string
}

function categorizeAlert(argText: string): Category {
  const inner = argText.replace(/^['"`]|['"`]$/g, '')
  if (ERROR_RE.test(inner) || ERROR_RE.test(argText)) return 'error'
  if (SUCCESS_RE.test(inner) || SUCCESS_RE.test(argText)) return 'success'
  return 'review'
}

function buildAlertReplacement(argText: string): { newText: string; category: Category } {
  const cat = categorizeAlert(argText)
  const fn = cat === 'success' ? 'toast.success' : cat === 'error' ? 'toast.error' : 'toast.info'
  return { newText: `${fn}(${argText})`, category: cat === 'review' ? 'review' : cat }
}

function buildConfirmReplacement(argText: string): string {
  return `await confirmDialog({ title: '확인', description: ${argText}, variant: 'default' })`
}

const project = new Project({
  tsConfigFilePath: path.resolve('tsconfig.app.json'),
  skipAddingFilesFromTsConfig: false,
})

const sites: Site[] = []
const confirmFiles = new Set<string>()
const asyncFunctions = new Set<string>()

for (const sourceFile of project.getSourceFiles()) {
  const absPath = sourceFile.getFilePath()
  if (!absPath.includes('/src/')) continue

  const relPath = path.relative(process.cwd(), absPath)

  sourceFile.forEachDescendant((node) => {
    if (node.getKind() !== SyntaxKind.CallExpression) return

    const callExpr = node as CallExpression
    const expr = callExpr.getExpression()
    const exprText = expr.getText()

    const isAlert = exprText === 'alert' || exprText === 'window.alert'
    const isConfirm = exprText === 'confirm' || exprText === 'window.confirm'
    if (!isAlert && !isConfirm) return

    const line = callExpr.getStartLineNumber()
    const args = callExpr.getArguments()
    const argText = args.length > 0 ? args[0].getText() : "''"
    const originalText = callExpr.getText()

    if (isAlert) {
      const { newText, category } = buildAlertReplacement(argText)
      const note = category === 'review' ? 'REVIEW: ambiguous category, defaulting to toast.info' : undefined
      sites.push({ file: relPath, absPath, line, originalText, newText, category, note })
    } else {
      confirmFiles.add(relPath)
      const newText = buildConfirmReplacement(argText)

      let parent: Node | undefined = callExpr.getParent()
      while (parent) {
        const kind = parent.getKind()
        if (
          kind === SyntaxKind.FunctionDeclaration ||
          kind === SyntaxKind.FunctionExpression ||
          kind === SyntaxKind.ArrowFunction ||
          kind === SyntaxKind.MethodDeclaration
        ) {
          const funcText = parent.getChildrenOfKind(SyntaxKind.Identifier)[0]?.getText() || '<anonymous>'
          asyncFunctions.add(`${relPath}:${parent.getStartLineNumber()} (${funcText})`)
          break
        }
        parent = parent.getParent()
      }

      sites.push({ file: relPath, absPath, line, originalText, newText, category: 'confirm' })
    }
  })
}

const alertSites = sites.filter((s) => s.category !== 'confirm')
const confirmSites = sites.filter((s) => s.category === 'confirm')
const successSites = alertSites.filter((s) => s.category === 'success')
const errorSites = alertSites.filter((s) => s.category === 'error')
const reviewSites = alertSites.filter((s) => s.category === 'review')
const allFiles = [...new Set(sites.map((s) => s.file))].sort()

// ── DRY-RUN artifacts ────────────────────────────────────────────────────────
const perFileTable = allFiles
  .map((f) => {
    const a = alertSites.filter((s) => s.file === f).length
    const c = confirmSites.filter((s) => s.file === f).length
    return `| ${f} | ${a} | ${c} |`
  })
  .join('\n')

const inventoryMd = `# P1 Alert/Confirm Inventory

## Totals
| Metric | Count |
|--------|-------|
| alert() sites | ${alertSites.length} |
| confirm() sites | ${confirmSites.length} |
| Total sites | ${sites.length} |
| Affected files | ${allFiles.length} |

## Alert Categorization
| Category | Count | Replacement |
|----------|-------|-------------|
| success | ${successSites.length} | toast.success() |
| error | ${errorSites.length} | toast.error() |
| review (→ toast.info) | ${reviewSites.length} | toast.info() — needs human review |
| confirm | ${confirmSites.length} | await confirmDialog() |

## Per-File Breakdown
| File | alert() | confirm() |
|------|---------|-----------|
${perFileTable}

## Pages Requiring \`useConfirm()\` Hook Injection
${[...confirmFiles].sort().map((f) => `- ${f}`).join('\n')}

## Event Handlers Requiring \`async\` Conversion (best-effort AST)
${[...asyncFunctions].sort().map((f) => `- ${f}`).join('\n')}
`

fs.writeFileSync('.p1-inventory.md', inventoryMd)

const sitesByFile = new Map<string, Site[]>()
for (const site of sites) {
  if (!sitesByFile.has(site.file)) sitesByFile.set(site.file, [])
  sitesByFile.get(site.file)!.push(site)
}

let dryrunMd = `# P1 Dry-Run Preview\n\n> Generated by codemod-alert-confirm.ts\n\n## Summary\n- **${alertSites.length}** alert() → toast replacements (${successSites.length} success, ${errorSites.length} error, ${reviewSites.length} review→info)\n- **${confirmSites.length}** confirm() → await confirmDialog() replacements\n- **${[...confirmFiles].length}** pages need \`useConfirm()\` hook injection\n- **${asyncFunctions.size}** enclosing functions need \`async\` keyword\n\n---\n`

for (const [file, fileSites] of [...sitesByFile.entries()].sort()) {
  dryrunMd += `\n## ${file}\n\n`
  for (const site of fileSites.sort((a, b) => a.line - b.line)) {
    dryrunMd += `**Line ${site.line}** [${site.category}${site.note ? ' ⚠️ REVIEW' : ''}]\n`
    dryrunMd += `\`\`\`diff\n- ${site.originalText}\n+ ${site.newText}\n\`\`\`\n`
    if (site.note) dryrunMd += `> Note: ${site.note}\n`
    dryrunMd += '\n'
  }
}

fs.writeFileSync('.p1-dryrun.md', dryrunMd)

if (!IS_APPLY) {
  console.log('Wrote .p1-inventory.md')
  console.log('Wrote .p1-dryrun.md')
  console.log(`\n=== DRY-RUN SUMMARY ===`)
  console.log(`alert() sites : ${alertSites.length}`)
  console.log(`  → success   : ${successSites.length}`)
  console.log(`  → error     : ${errorSites.length}`)
  console.log(`  → review    : ${reviewSites.length}`)
  console.log(`confirm() sites: ${confirmSites.length}`)
  console.log(`Affected files : ${allFiles.length}`)
  console.log(`useConfirm pages: ${confirmFiles.size}`)
  console.log(`async funcs needed: ${asyncFunctions.size}`)
  console.log(`\nNO source files were modified.`)
  process.exit(0)
}

// ── APPLY MODE — alerts only ──────────────────────────────────────────────────
console.log('=== APPLY MODE — replacing alert() → toast() ===\n')

// Group alert sites by absolute file path
const alertByFile = new Map<string, Site[]>()
for (const site of alertSites) {
  if (!alertByFile.has(site.absPath)) alertByFile.set(site.absPath, [])
  alertByFile.get(site.absPath)!.push(site)
}

let filesModified = 0
let replacementsApplied = 0

for (const [absPath, fileSites] of alertByFile) {
  let text = fs.readFileSync(absPath, 'utf-8')
  const orig = text

  // Build unique replacement map (originalText → newText)
  const replacements = new Map<string, string>()
  for (const site of fileSites) {
    replacements.set(site.originalText, site.newText)
  }

  // Apply each replacement (all occurrences)
  for (const [from, to] of replacements) {
    // Escape for use in regex — needed for template literals / special chars
    const escaped = from.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
    const re = new RegExp(escaped, 'g')
    text = text.replace(re, to)
    replacementsApplied += (orig.match(re) ?? []).length
  }

  // Inject `import { toast } from 'sonner'` if not already present
  if (!text.includes("from 'sonner'")) {
    // Find end of last import statement line
    const lines = text.split('\n')
    let lastImportIdx = -1
    for (let i = 0; i < lines.length; i++) {
      if (lines[i].trimStart().startsWith('import ')) lastImportIdx = i
    }
    const insertAt = lastImportIdx >= 0 ? lastImportIdx + 1 : 0
    lines.splice(insertAt, 0, "import { toast } from 'sonner'")
    text = lines.join('\n')
  }

  if (text !== orig) {
    fs.writeFileSync(absPath, text)
    filesModified++
    const relPath = path.relative(process.cwd(), absPath)
    console.log(`  ✓ ${relPath} (${fileSites.length} alert replacements)`)
  }
}

console.log(`\nApplied ${replacementsApplied} alert replacements across ${filesModified} files.`)
console.log('confirm() sites NOT auto-applied — see .p1-dryrun.md for manual migration guide.')
