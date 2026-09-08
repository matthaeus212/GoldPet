#!/usr/bin/env python3
"""Fix: inject {ConfirmDialog} as last JSX child in files that are missing it."""
import re, os

BASE = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = os.path.join(BASE, 'src')

FILES = [
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

def inject(path):
    with open(path) as f:
        text = f.read()

    # Check if {ConfirmDialog} already appears as JSX (not just in destructuring)
    # The destructuring line contains it on the left side; JSX usage looks like `{ConfirmDialog}`
    # on its own line
    if re.search(r'^\s+\{ConfirmDialog\}', text, re.MULTILINE):
        print(f'  ~ {os.path.relpath(path)} (already has JSX ConfirmDialog)')
        return

    # Find the LAST closing root JSX tag just before `\n  );\n` or `\n    );\n`
    # Pattern: newline + indent + </Tag> + newline + indent + ); + newline
    last_match = None
    for m in re.finditer(r'\n(\s+)(</[A-Za-z]+>)\n(\s+)\);\s*\n', text):
        last_match = m

    if not last_match:
        print(f'  ! {os.path.relpath(path)} (pattern not found — manual fix needed)')
        return

    indent = last_match.group(1)
    insert_pos = last_match.start()
    new_text = text[:insert_pos] + f'\n{indent}{{ConfirmDialog}}' + text[insert_pos:]

    with open(path, 'w') as f:
        f.write(new_text)
    print(f'  ✓ {os.path.relpath(path)}')

for rel in FILES:
    inject(os.path.join(SRC, rel))
