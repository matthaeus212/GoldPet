#!/usr/bin/env python3
"""
migrate-confirm.py — Bulk confirm() → useConfirm() migration for admin/src files.
Handles:
  1. Import injection
  2. useConfirm() hook call injection in component body
  3. confirm() → await confirmDialog({}) replacement
  4. async keyword on enclosing handler functions
  5. {ConfirmDialog} insertion in JSX return
"""
import re, os, sys

CONFIRM_IMPORT = "import { useConfirm } from '@/hooks/useConfirm'"
HOOK_LINE = "  const { confirm: confirmDialog, ConfirmDialog } = useConfirm()"
DESTRUCTIVE_RE = re.compile(r'삭제|탈퇴|제거|비활성화|차단')

def variant_for(arg: str) -> str:
    return 'destructive' if DESTRUCTIVE_RE.search(arg) else 'default'

def opts_for(arg: str) -> str:
    v = variant_for(arg)
    if v == 'destructive':
        return f"{{ description: {arg}, variant: 'destructive' }}"
    return f"{{ description: {arg} }}"

def replace_confirm_in_text(text: str) -> str:
    """Replace !confirm(...) and confirm(msg) call patterns."""
    result = []
    i = 0
    while i < len(text):
        # Look for !(confirm( or !window.confirm( or confirm( or window.confirm(
        m = re.search(r'(!)\s*(?:window\.)?confirm\(|(?<![!])\b(?:window\.)?confirm\(', text[i:])
        if not m:
            result.append(text[i:])
            break
        start = i + m.start()
        result.append(text[i:start])
        negated = text[start] == '!'
        # Find the matching closing paren for confirm(
        paren_start = text.index('(', start)
        depth = 0
        j = paren_start
        while j < len(text):
            if text[j] == '(':
                depth += 1
            elif text[j] == ')':
                depth -= 1
                if depth == 0:
                    break
            j += 1
        arg = text[paren_start+1:j]
        opts = opts_for(arg)
        if negated:
            result.append(f"!(await confirmDialog({opts}))")
        else:
            result.append(f"await confirmDialog({opts})")
        i = j + 1
    return ''.join(result)

def inject_import(text: str) -> str:
    if CONFIRM_IMPORT in text:
        return text
    lines = text.split('\n')
    last_import = -1
    for idx, line in enumerate(lines):
        stripped = line.strip()
        if stripped.startswith('import ') or stripped.startswith('import{') or stripped.startswith('import type'):
            last_import = idx
    if last_import >= 0:
        lines.insert(last_import + 1, CONFIRM_IMPORT)
    return '\n'.join(lines)

def inject_hook_call(text: str) -> str:
    if 'useConfirm()' in text:
        return text
    lines = text.split('\n')
    # Find first `const queryClient = useQueryClient()` line — insert after it
    for idx, line in enumerate(lines):
        if 'useQueryClient()' in line and line.strip().startswith('const '):
            lines.insert(idx + 1, HOOK_LINE)
            return '\n'.join(lines)
    # Fallback: insert after the component function opening line
    for idx, line in enumerate(lines):
        stripped = line.strip()
        if re.match(r'export default function \w+\(', stripped) or \
           re.match(r'const \w+ = \(', stripped) or \
           re.match(r'const \w+: React\.FC', stripped):
            # Find the opening brace line
            brace_idx = idx
            for k in range(idx, min(idx + 5, len(lines))):
                if lines[k].rstrip().endswith('{'):
                    brace_idx = k
                    break
            lines.insert(brace_idx + 1, HOOK_LINE)
            return '\n'.join(lines)
    return text

def make_handlers_async(text: str) -> str:
    """Find arrow function handlers that contain await confirmDialog and lack async."""
    # Pattern: const handleX = () => { ... await confirmDialog ...
    # or const handleX = (param) => { ... await confirmDialog ...
    lines = text.split('\n')
    # Find lines that will contain await confirmDialog (after replacement)
    confirm_lines = set()
    for idx, line in enumerate(lines):
        if 'await confirmDialog(' in line:
            confirm_lines.add(idx)

    if not confirm_lines:
        return text

    # For each async confirmDialog, walk up to find enclosing arrow/function
    modified = set()
    for cidx in confirm_lines:
        # Walk backwards to find the nearest enclosing function/arrow
        brace_depth = 0
        for k in range(cidx - 1, -1, -1):
            line = lines[k]
            brace_depth += line.count('{') - line.count('}')
            # Check for arrow function or regular function declaration
            m = re.match(r'^(\s*const \w+ = )(\()(.+)$', line)
            if not m:
                m = re.match(r'^(\s*const \w+ = )(\(\))(.*)$', line)
            if m and '=>' in line and 'async' not in line.split('=>')[0]:
                # Make it async
                lines[k] = re.sub(r'^(\s*const \w+ = )(\()', r'\1async (\2'[:-1] + r'\1async \2', line)
                # Simpler: prepend async before the parens
                lines[k] = re.sub(r'^(\s*const \w+ = )(\()', lambda mm: mm.group(1) + 'async ' + mm.group(2), line)
                modified.add(k)
                break
            # Also match: const handleX = (id: number) => {
            if '=>' in line and re.search(r'const \w+\s*=\s*(?!\s*async)', line) and 'async' not in line.split('=>')[0]:
                lines[k] = re.sub(
                    r'(const \w+\s*=\s*)(\([^)]*\)\s*=>)',
                    lambda mm: mm.group(1) + 'async ' + mm.group(2),
                    line
                )
                modified.add(k)
                break
    return '\n'.join(lines)

def inject_confirm_dialog_jsx(text: str) -> str:
    """Insert {ConfirmDialog} as last child of root JSX element in return."""
    if '{ConfirmDialog}' in text:
        return text
    # Find the last closing root tag pattern just before );
    # Pattern: (  );\n} or  );\n};)  preceded by a closing tag line
    # We look for the return's closing ) - last occurrence
    # Strategy: find `\n  );\n` (or `\n    );\n`) at end of file and insert before the
    # last closing tag before it.

    # Find position of the last `\n  );` pattern
    # Most files: `\n    </XYZ>\n  );\n}`
    # Some: `\n        </div>\n    );\n};`

    close_return = re.search(r'\n(\s+)\);\s*\n\}', text[::-1])
    if not close_return:
        return text

    # Find the last closing JSX tag before the end of the file
    # Pattern: a line that is whitespace + </Something> or whitespace + </>
    last_closing_tag = None
    for m in re.finditer(r'\n(\s+)(</\w+>|</>)\s*\n', text):
        last_closing_tag = m

    if not last_closing_tag:
        return text

    indent = last_closing_tag.group(1)
    pos = last_closing_tag.start()
    tag_line = last_closing_tag.group(2)

    # Insert {ConfirmDialog} before this closing tag
    insertion = f'\n{indent}{{ConfirmDialog}}'
    return text[:pos] + insertion + text[pos:]

def process_file(path: str, inline_cases: bool = False):
    with open(path) as f:
        original = f.read()

    text = original
    text = inject_import(text)
    text = inject_hook_call(text)
    text = replace_confirm_in_text(text)
    text = make_handlers_async(text)
    text = inject_confirm_dialog_jsx(text)

    if text != original:
        with open(path, 'w') as f:
            f.write(text)
        print(f'  ✓ {os.path.relpath(path)}')
    else:
        print(f'  ~ {os.path.relpath(path)} (no changes)')

# --- Files to process (all non-inline-JSX confirm files) ---
BASE = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = os.path.join(BASE, 'src')

SIMPLE_FILES = [
    'pages/aiprofile/AIProfileLoadingTipsPage.tsx',
    'pages/aiprofile/AIProfilePage.tsx',
    'pages/chat/ChatManagementPage.tsx',
    'pages/community/CommunityDetailPage.tsx',
    'pages/community/CommunityPage.tsx',
    'pages/course/CourseDetailPage.tsx',
    'pages/course/CoursePage.tsx',
    'pages/data/BreedsPage.tsx',
    'pages/data/DataPage.tsx',
    'pages/economy/EconomyPage.tsx',
    'pages/emoticon/EmoticonManagementPage.tsx',
    'pages/lbs/LBSPage.tsx',
    'pages/notice/NoticeManagementPage.tsx',
    'pages/reports/ReportDetailPage.tsx',
    'pages/reports/ReportsPage.tsx',
    'pages/settings/ProfilePage.tsx',
    'pages/users/UserDetailPage.tsx',
    'pages/users/UsersPage.tsx',
    'pages/walk/WalkRankingManagementPage.tsx',
]

print('=== Processing simple (named handler) confirm files ===')
for rel in SIMPLE_FILES:
    process_file(os.path.join(SRC, rel))
print(f'\nDone. {len(SIMPLE_FILES)} files processed.')
print('\nINLINE JSX cases (AttributesPage, UserAttributesPage, MarketingPage, SystemPage) need manual edit.')
