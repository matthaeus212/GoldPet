import 'package:freezed_annotation/freezed_annotation.dart';

part 'stool_analysis_models.freezed.dart';
part 'stool_analysis_models.g.dart';

@freezed
class StoolAnalysisResponse with _$StoolAnalysisResponse {
  const factory StoolAnalysisResponse({
    required int id,
    required int petId,
    required String petName,
    required String imageUrl,
    required String status,
    int? overallScore,
    int? colorScore,
    String? colorAssessment,
    int? consistencyScore,
    String? consistencyAssessment,
    int? coatingScore,
    String? coatingAssessment,
    int? contentsScore,
    String? contentsAssessment,
    String? healthSummary,
    List<String>? healthTips,
    List<String>? warnings,
    String? disclaimer,
    DateTime? analyzedAt,
    required DateTime createdAt,
  }) = _StoolAnalysisResponse;

  factory StoolAnalysisResponse.fromJson(Map<String, dynamic> json) =>
      _$StoolAnalysisResponseFromJson(json);
}

@freezed
class MonthlyScore with _$MonthlyScore {
  const factory MonthlyScore({
    required int year,
    required int month,
    required double averageScore,
    required int count,
  }) = _MonthlyScore;

  factory MonthlyScore.fromJson(Map<String, dynamic> json) =>
      _$MonthlyScoreFromJson(json);
}

@freezed
class HealthTrendResponse with _$HealthTrendResponse {
  const factory HealthTrendResponse({
    required int petId,
    required String petName,
    required List<MonthlyScore> monthlyScores,
    double? overallTrend,
    String? trendMessage,
  }) = _HealthTrendResponse;

  factory HealthTrendResponse.fromJson(Map<String, dynamic> json) =>
      _$HealthTrendResponseFromJson(json);
}
