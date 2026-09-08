// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'stool_analysis_models.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

_$StoolAnalysisResponseImpl _$$StoolAnalysisResponseImplFromJson(
  Map<String, dynamic> json,
) => _$StoolAnalysisResponseImpl(
  id: (json['id'] as num).toInt(),
  petId: (json['petId'] as num).toInt(),
  petName: json['petName'] as String,
  imageUrl: json['imageUrl'] as String,
  status: json['status'] as String,
  overallScore: (json['overallScore'] as num?)?.toInt(),
  colorScore: (json['colorScore'] as num?)?.toInt(),
  colorAssessment: json['colorAssessment'] as String?,
  consistencyScore: (json['consistencyScore'] as num?)?.toInt(),
  consistencyAssessment: json['consistencyAssessment'] as String?,
  coatingScore: (json['coatingScore'] as num?)?.toInt(),
  coatingAssessment: json['coatingAssessment'] as String?,
  contentsScore: (json['contentsScore'] as num?)?.toInt(),
  contentsAssessment: json['contentsAssessment'] as String?,
  healthSummary: json['healthSummary'] as String?,
  healthTips: (json['healthTips'] as List<dynamic>?)
      ?.map((e) => e as String)
      .toList(),
  warnings: (json['warnings'] as List<dynamic>?)
      ?.map((e) => e as String)
      .toList(),
  disclaimer: json['disclaimer'] as String?,
  analyzedAt: json['analyzedAt'] == null
      ? null
      : DateTime.parse(json['analyzedAt'] as String),
  createdAt: DateTime.parse(json['createdAt'] as String),
);

Map<String, dynamic> _$$StoolAnalysisResponseImplToJson(
  _$StoolAnalysisResponseImpl instance,
) => <String, dynamic>{
  'id': instance.id,
  'petId': instance.petId,
  'petName': instance.petName,
  'imageUrl': instance.imageUrl,
  'status': instance.status,
  'overallScore': instance.overallScore,
  'colorScore': instance.colorScore,
  'colorAssessment': instance.colorAssessment,
  'consistencyScore': instance.consistencyScore,
  'consistencyAssessment': instance.consistencyAssessment,
  'coatingScore': instance.coatingScore,
  'coatingAssessment': instance.coatingAssessment,
  'contentsScore': instance.contentsScore,
  'contentsAssessment': instance.contentsAssessment,
  'healthSummary': instance.healthSummary,
  'healthTips': instance.healthTips,
  'warnings': instance.warnings,
  'disclaimer': instance.disclaimer,
  'analyzedAt': instance.analyzedAt?.toIso8601String(),
  'createdAt': instance.createdAt.toIso8601String(),
};

_$MonthlyScoreImpl _$$MonthlyScoreImplFromJson(Map<String, dynamic> json) =>
    _$MonthlyScoreImpl(
      year: (json['year'] as num).toInt(),
      month: (json['month'] as num).toInt(),
      averageScore: (json['averageScore'] as num).toDouble(),
      count: (json['count'] as num).toInt(),
    );

Map<String, dynamic> _$$MonthlyScoreImplToJson(_$MonthlyScoreImpl instance) =>
    <String, dynamic>{
      'year': instance.year,
      'month': instance.month,
      'averageScore': instance.averageScore,
      'count': instance.count,
    };

_$HealthTrendResponseImpl _$$HealthTrendResponseImplFromJson(
  Map<String, dynamic> json,
) => _$HealthTrendResponseImpl(
  petId: (json['petId'] as num).toInt(),
  petName: json['petName'] as String,
  monthlyScores: (json['monthlyScores'] as List<dynamic>)
      .map((e) => MonthlyScore.fromJson(e as Map<String, dynamic>))
      .toList(),
  overallTrend: (json['overallTrend'] as num?)?.toDouble(),
  trendMessage: json['trendMessage'] as String?,
);

Map<String, dynamic> _$$HealthTrendResponseImplToJson(
  _$HealthTrendResponseImpl instance,
) => <String, dynamic>{
  'petId': instance.petId,
  'petName': instance.petName,
  'monthlyScores': instance.monthlyScores,
  'overallTrend': instance.overallTrend,
  'trendMessage': instance.trendMessage,
};
