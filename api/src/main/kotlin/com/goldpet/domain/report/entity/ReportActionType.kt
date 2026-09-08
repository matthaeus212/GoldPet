package com.goldpet.domain.report.entity

enum class ReportActionType(val label: String) {
    DELETE_POST("게시글 삭제"),
    DELETE_COMMENT("댓글 삭제"),
    DELETE_MESSAGE("메시지 삭제"),
    SUSPEND_USER("계정 정지"),
    WARN_USER("경고 조치"),
    HIDE_POST("게시글 숨김"),
    HIDE_COMMENT("댓글 숨김"),
    HIDE_COURSE("코스 숨김"),
    HIDE_CHAT("채팅 숨김")  // V68 — Sprint 3 BLOCKER #5 자동 hide
}
