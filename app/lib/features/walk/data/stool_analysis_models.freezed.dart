// coverage:ignore-file
// GENERATED CODE - DO NOT MODIFY BY HAND
// ignore_for_file: type=lint
// ignore_for_file: unused_element, deprecated_member_use, deprecated_member_use_from_same_package, use_function_type_syntax_for_parameters, unnecessary_const, avoid_init_to_null, invalid_override_different_default_values_named, prefer_expression_function_bodies, annotate_overrides, invalid_annotation_target, unnecessary_question_mark

part of 'stool_analysis_models.dart';

// **************************************************************************
// FreezedGenerator
// **************************************************************************

T _$identity<T>(T value) => value;

final _privateConstructorUsedError = UnsupportedError(
  'It seems like you constructed your class using `MyClass._()`. This constructor is only meant to be used by freezed and you are not supposed to need it nor use it.\nPlease check the documentation here for more information: https://github.com/rrousselGit/freezed#adding-getters-and-methods-to-our-models',
);

StoolAnalysisResponse _$StoolAnalysisResponseFromJson(
  Map<String, dynamic> json,
) {
  return _StoolAnalysisResponse.fromJson(json);
}

/// @nodoc
mixin _$StoolAnalysisResponse {
  int get id => throw _privateConstructorUsedError;
  int get petId => throw _privateConstructorUsedError;
  String get petName => throw _privateConstructorUsedError;
  String get imageUrl => throw _privateConstructorUsedError;
  String get status => throw _privateConstructorUsedError;
  int? get overallScore => throw _privateConstructorUsedError;
  int? get colorScore => throw _privateConstructorUsedError;
  String? get colorAssessment => throw _privateConstructorUsedError;
  int? get consistencyScore => throw _privateConstructorUsedError;
  String? get consistencyAssessment => throw _privateConstructorUsedError;
  int? get coatingScore => throw _privateConstructorUsedError;
  String? get coatingAssessment => throw _privateConstructorUsedError;
  int? get contentsScore => throw _privateConstructorUsedError;
  String? get contentsAssessment => throw _privateConstructorUsedError;
  String? get healthSummary => throw _privateConstructorUsedError;
  List<String>? get healthTips => throw _privateConstructorUsedError;
  List<String>? get warnings => throw _privateConstructorUsedError;
  String? get disclaimer => throw _privateConstructorUsedError;
  DateTime? get analyzedAt => throw _privateConstructorUsedError;
  DateTime get createdAt => throw _privateConstructorUsedError;

  /// Serializes this StoolAnalysisResponse to a JSON map.
  Map<String, dynamic> toJson() => throw _privateConstructorUsedError;

  /// Create a copy of StoolAnalysisResponse
  /// with the given fields replaced by the non-null parameter values.
  @JsonKey(includeFromJson: false, includeToJson: false)
  $StoolAnalysisResponseCopyWith<StoolAnalysisResponse> get copyWith =>
      throw _privateConstructorUsedError;
}

/// @nodoc
abstract class $StoolAnalysisResponseCopyWith<$Res> {
  factory $StoolAnalysisResponseCopyWith(
    StoolAnalysisResponse value,
    $Res Function(StoolAnalysisResponse) then,
  ) = _$StoolAnalysisResponseCopyWithImpl<$Res, StoolAnalysisResponse>;
  @useResult
  $Res call({
    int id,
    int petId,
    String petName,
    String imageUrl,
    String status,
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
    DateTime createdAt,
  });
}

/// @nodoc
class _$StoolAnalysisResponseCopyWithImpl<
  $Res,
  $Val extends StoolAnalysisResponse
>
    implements $StoolAnalysisResponseCopyWith<$Res> {
  _$StoolAnalysisResponseCopyWithImpl(this._value, this._then);

  // ignore: unused_field
  final $Val _value;
  // ignore: unused_field
  final $Res Function($Val) _then;

  /// Create a copy of StoolAnalysisResponse
  /// with the given fields replaced by the non-null parameter values.
  @pragma('vm:prefer-inline')
  @override
  $Res call({
    Object? id = null,
    Object? petId = null,
    Object? petName = null,
    Object? imageUrl = null,
    Object? status = null,
    Object? overallScore = freezed,
    Object? colorScore = freezed,
    Object? colorAssessment = freezed,
    Object? consistencyScore = freezed,
    Object? consistencyAssessment = freezed,
    Object? coatingScore = freezed,
    Object? coatingAssessment = freezed,
    Object? contentsScore = freezed,
    Object? contentsAssessment = freezed,
    Object? healthSummary = freezed,
    Object? healthTips = freezed,
    Object? warnings = freezed,
    Object? disclaimer = freezed,
    Object? analyzedAt = freezed,
    Object? createdAt = null,
  }) {
    return _then(
      _value.copyWith(
            id: null == id
                ? _value.id
                : id // ignore: cast_nullable_to_non_nullable
                      as int,
            petId: null == petId
                ? _value.petId
                : petId // ignore: cast_nullable_to_non_nullable
                      as int,
            petName: null == petName
                ? _value.petName
                : petName // ignore: cast_nullable_to_non_nullable
                      as String,
            imageUrl: null == imageUrl
                ? _value.imageUrl
                : imageUrl // ignore: cast_nullable_to_non_nullable
                      as String,
            status: null == status
                ? _value.status
                : status // ignore: cast_nullable_to_non_nullable
                      as String,
            overallScore: freezed == overallScore
                ? _value.overallScore
                : overallScore // ignore: cast_nullable_to_non_nullable
                      as int?,
            colorScore: freezed == colorScore
                ? _value.colorScore
                : colorScore // ignore: cast_nullable_to_non_nullable
                      as int?,
            colorAssessment: freezed == colorAssessment
                ? _value.colorAssessment
                : colorAssessment // ignore: cast_nullable_to_non_nullable
                      as String?,
            consistencyScore: freezed == consistencyScore
                ? _value.consistencyScore
                : consistencyScore // ignore: cast_nullable_to_non_nullable
                      as int?,
            consistencyAssessment: freezed == consistencyAssessment
                ? _value.consistencyAssessment
                : consistencyAssessment // ignore: cast_nullable_to_non_nullable
                      as String?,
            coatingScore: freezed == coatingScore
                ? _value.coatingScore
                : coatingScore // ignore: cast_nullable_to_non_nullable
                      as int?,
            coatingAssessment: freezed == coatingAssessment
                ? _value.coatingAssessment
                : coatingAssessment // ignore: cast_nullable_to_non_nullable
                      as String?,
            contentsScore: freezed == contentsScore
                ? _value.contentsScore
                : contentsScore // ignore: cast_nullable_to_non_nullable
                      as int?,
            contentsAssessment: freezed == contentsAssessment
                ? _value.contentsAssessment
                : contentsAssessment // ignore: cast_nullable_to_non_nullable
                      as String?,
            healthSummary: freezed == healthSummary
                ? _value.healthSummary
                : healthSummary // ignore: cast_nullable_to_non_nullable
                      as String?,
            healthTips: freezed == healthTips
                ? _value.healthTips
                : healthTips // ignore: cast_nullable_to_non_nullable
                      as List<String>?,
            warnings: freezed == warnings
                ? _value.warnings
                : warnings // ignore: cast_nullable_to_non_nullable
                      as List<String>?,
            disclaimer: freezed == disclaimer
                ? _value.disclaimer
                : disclaimer // ignore: cast_nullable_to_non_nullable
                      as String?,
            analyzedAt: freezed == analyzedAt
                ? _value.analyzedAt
                : analyzedAt // ignore: cast_nullable_to_non_nullable
                      as DateTime?,
            createdAt: null == createdAt
                ? _value.createdAt
                : createdAt // ignore: cast_nullable_to_non_nullable
                      as DateTime,
          )
          as $Val,
    );
  }
}

/// @nodoc
abstract class _$$StoolAnalysisResponseImplCopyWith<$Res>
    implements $StoolAnalysisResponseCopyWith<$Res> {
  factory _$$StoolAnalysisResponseImplCopyWith(
    _$StoolAnalysisResponseImpl value,
    $Res Function(_$StoolAnalysisResponseImpl) then,
  ) = __$$StoolAnalysisResponseImplCopyWithImpl<$Res>;
  @override
  @useResult
  $Res call({
    int id,
    int petId,
    String petName,
    String imageUrl,
    String status,
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
    DateTime createdAt,
  });
}

/// @nodoc
class __$$StoolAnalysisResponseImplCopyWithImpl<$Res>
    extends
        _$StoolAnalysisResponseCopyWithImpl<$Res, _$StoolAnalysisResponseImpl>
    implements _$$StoolAnalysisResponseImplCopyWith<$Res> {
  __$$StoolAnalysisResponseImplCopyWithImpl(
    _$StoolAnalysisResponseImpl _value,
    $Res Function(_$StoolAnalysisResponseImpl) _then,
  ) : super(_value, _then);

  /// Create a copy of StoolAnalysisResponse
  /// with the given fields replaced by the non-null parameter values.
  @pragma('vm:prefer-inline')
  @override
  $Res call({
    Object? id = null,
    Object? petId = null,
    Object? petName = null,
    Object? imageUrl = null,
    Object? status = null,
    Object? overallScore = freezed,
    Object? colorScore = freezed,
    Object? colorAssessment = freezed,
    Object? consistencyScore = freezed,
    Object? consistencyAssessment = freezed,
    Object? coatingScore = freezed,
    Object? coatingAssessment = freezed,
    Object? contentsScore = freezed,
    Object? contentsAssessment = freezed,
    Object? healthSummary = freezed,
    Object? healthTips = freezed,
    Object? warnings = freezed,
    Object? disclaimer = freezed,
    Object? analyzedAt = freezed,
    Object? createdAt = null,
  }) {
    return _then(
      _$StoolAnalysisResponseImpl(
        id: null == id
            ? _value.id
            : id // ignore: cast_nullable_to_non_nullable
                  as int,
        petId: null == petId
            ? _value.petId
            : petId // ignore: cast_nullable_to_non_nullable
                  as int,
        petName: null == petName
            ? _value.petName
            : petName // ignore: cast_nullable_to_non_nullable
                  as String,
        imageUrl: null == imageUrl
            ? _value.imageUrl
            : imageUrl // ignore: cast_nullable_to_non_nullable
                  as String,
        status: null == status
            ? _value.status
            : status // ignore: cast_nullable_to_non_nullable
                  as String,
        overallScore: freezed == overallScore
            ? _value.overallScore
            : overallScore // ignore: cast_nullable_to_non_nullable
                  as int?,
        colorScore: freezed == colorScore
            ? _value.colorScore
            : colorScore // ignore: cast_nullable_to_non_nullable
                  as int?,
        colorAssessment: freezed == colorAssessment
            ? _value.colorAssessment
            : colorAssessment // ignore: cast_nullable_to_non_nullable
                  as String?,
        consistencyScore: freezed == consistencyScore
            ? _value.consistencyScore
            : consistencyScore // ignore: cast_nullable_to_non_nullable
                  as int?,
        consistencyAssessment: freezed == consistencyAssessment
            ? _value.consistencyAssessment
            : consistencyAssessment // ignore: cast_nullable_to_non_nullable
                  as String?,
        coatingScore: freezed == coatingScore
            ? _value.coatingScore
            : coatingScore // ignore: cast_nullable_to_non_nullable
                  as int?,
        coatingAssessment: freezed == coatingAssessment
            ? _value.coatingAssessment
            : coatingAssessment // ignore: cast_nullable_to_non_nullable
                  as String?,
        contentsScore: freezed == contentsScore
            ? _value.contentsScore
            : contentsScore // ignore: cast_nullable_to_non_nullable
                  as int?,
        contentsAssessment: freezed == contentsAssessment
            ? _value.contentsAssessment
            : contentsAssessment // ignore: cast_nullable_to_non_nullable
                  as String?,
        healthSummary: freezed == healthSummary
            ? _value.healthSummary
            : healthSummary // ignore: cast_nullable_to_non_nullable
                  as String?,
        healthTips: freezed == healthTips
            ? _value._healthTips
            : healthTips // ignore: cast_nullable_to_non_nullable
                  as List<String>?,
        warnings: freezed == warnings
            ? _value._warnings
            : warnings // ignore: cast_nullable_to_non_nullable
                  as List<String>?,
        disclaimer: freezed == disclaimer
            ? _value.disclaimer
            : disclaimer // ignore: cast_nullable_to_non_nullable
                  as String?,
        analyzedAt: freezed == analyzedAt
            ? _value.analyzedAt
            : analyzedAt // ignore: cast_nullable_to_non_nullable
                  as DateTime?,
        createdAt: null == createdAt
            ? _value.createdAt
            : createdAt // ignore: cast_nullable_to_non_nullable
                  as DateTime,
      ),
    );
  }
}

/// @nodoc
@JsonSerializable()
class _$StoolAnalysisResponseImpl implements _StoolAnalysisResponse {
  const _$StoolAnalysisResponseImpl({
    required this.id,
    required this.petId,
    required this.petName,
    required this.imageUrl,
    required this.status,
    this.overallScore,
    this.colorScore,
    this.colorAssessment,
    this.consistencyScore,
    this.consistencyAssessment,
    this.coatingScore,
    this.coatingAssessment,
    this.contentsScore,
    this.contentsAssessment,
    this.healthSummary,
    final List<String>? healthTips,
    final List<String>? warnings,
    this.disclaimer,
    this.analyzedAt,
    required this.createdAt,
  }) : _healthTips = healthTips,
       _warnings = warnings;

  factory _$StoolAnalysisResponseImpl.fromJson(Map<String, dynamic> json) =>
      _$$StoolAnalysisResponseImplFromJson(json);

  @override
  final int id;
  @override
  final int petId;
  @override
  final String petName;
  @override
  final String imageUrl;
  @override
  final String status;
  @override
  final int? overallScore;
  @override
  final int? colorScore;
  @override
  final String? colorAssessment;
  @override
  final int? consistencyScore;
  @override
  final String? consistencyAssessment;
  @override
  final int? coatingScore;
  @override
  final String? coatingAssessment;
  @override
  final int? contentsScore;
  @override
  final String? contentsAssessment;
  @override
  final String? healthSummary;
  final List<String>? _healthTips;
  @override
  List<String>? get healthTips {
    final value = _healthTips;
    if (value == null) return null;
    if (_healthTips is EqualUnmodifiableListView) return _healthTips;
    // ignore: implicit_dynamic_type
    return EqualUnmodifiableListView(value);
  }

  final List<String>? _warnings;
  @override
  List<String>? get warnings {
    final value = _warnings;
    if (value == null) return null;
    if (_warnings is EqualUnmodifiableListView) return _warnings;
    // ignore: implicit_dynamic_type
    return EqualUnmodifiableListView(value);
  }

  @override
  final String? disclaimer;
  @override
  final DateTime? analyzedAt;
  @override
  final DateTime createdAt;

  @override
  String toString() {
    return 'StoolAnalysisResponse(id: $id, petId: $petId, petName: $petName, imageUrl: $imageUrl, status: $status, overallScore: $overallScore, colorScore: $colorScore, colorAssessment: $colorAssessment, consistencyScore: $consistencyScore, consistencyAssessment: $consistencyAssessment, coatingScore: $coatingScore, coatingAssessment: $coatingAssessment, contentsScore: $contentsScore, contentsAssessment: $contentsAssessment, healthSummary: $healthSummary, healthTips: $healthTips, warnings: $warnings, disclaimer: $disclaimer, analyzedAt: $analyzedAt, createdAt: $createdAt)';
  }

  @override
  bool operator ==(Object other) {
    return identical(this, other) ||
        (other.runtimeType == runtimeType &&
            other is _$StoolAnalysisResponseImpl &&
            (identical(other.id, id) || other.id == id) &&
            (identical(other.petId, petId) || other.petId == petId) &&
            (identical(other.petName, petName) || other.petName == petName) &&
            (identical(other.imageUrl, imageUrl) ||
                other.imageUrl == imageUrl) &&
            (identical(other.status, status) || other.status == status) &&
            (identical(other.overallScore, overallScore) ||
                other.overallScore == overallScore) &&
            (identical(other.colorScore, colorScore) ||
                other.colorScore == colorScore) &&
            (identical(other.colorAssessment, colorAssessment) ||
                other.colorAssessment == colorAssessment) &&
            (identical(other.consistencyScore, consistencyScore) ||
                other.consistencyScore == consistencyScore) &&
            (identical(other.consistencyAssessment, consistencyAssessment) ||
                other.consistencyAssessment == consistencyAssessment) &&
            (identical(other.coatingScore, coatingScore) ||
                other.coatingScore == coatingScore) &&
            (identical(other.coatingAssessment, coatingAssessment) ||
                other.coatingAssessment == coatingAssessment) &&
            (identical(other.contentsScore, contentsScore) ||
                other.contentsScore == contentsScore) &&
            (identical(other.contentsAssessment, contentsAssessment) ||
                other.contentsAssessment == contentsAssessment) &&
            (identical(other.healthSummary, healthSummary) ||
                other.healthSummary == healthSummary) &&
            const DeepCollectionEquality().equals(
              other._healthTips,
              _healthTips,
            ) &&
            const DeepCollectionEquality().equals(other._warnings, _warnings) &&
            (identical(other.disclaimer, disclaimer) ||
                other.disclaimer == disclaimer) &&
            (identical(other.analyzedAt, analyzedAt) ||
                other.analyzedAt == analyzedAt) &&
            (identical(other.createdAt, createdAt) ||
                other.createdAt == createdAt));
  }

  @JsonKey(includeFromJson: false, includeToJson: false)
  @override
  int get hashCode => Object.hashAll([
    runtimeType,
    id,
    petId,
    petName,
    imageUrl,
    status,
    overallScore,
    colorScore,
    colorAssessment,
    consistencyScore,
    consistencyAssessment,
    coatingScore,
    coatingAssessment,
    contentsScore,
    contentsAssessment,
    healthSummary,
    const DeepCollectionEquality().hash(_healthTips),
    const DeepCollectionEquality().hash(_warnings),
    disclaimer,
    analyzedAt,
    createdAt,
  ]);

  /// Create a copy of StoolAnalysisResponse
  /// with the given fields replaced by the non-null parameter values.
  @JsonKey(includeFromJson: false, includeToJson: false)
  @override
  @pragma('vm:prefer-inline')
  _$$StoolAnalysisResponseImplCopyWith<_$StoolAnalysisResponseImpl>
  get copyWith =>
      __$$StoolAnalysisResponseImplCopyWithImpl<_$StoolAnalysisResponseImpl>(
        this,
        _$identity,
      );

  @override
  Map<String, dynamic> toJson() {
    return _$$StoolAnalysisResponseImplToJson(this);
  }
}

abstract class _StoolAnalysisResponse implements StoolAnalysisResponse {
  const factory _StoolAnalysisResponse({
    required final int id,
    required final int petId,
    required final String petName,
    required final String imageUrl,
    required final String status,
    final int? overallScore,
    final int? colorScore,
    final String? colorAssessment,
    final int? consistencyScore,
    final String? consistencyAssessment,
    final int? coatingScore,
    final String? coatingAssessment,
    final int? contentsScore,
    final String? contentsAssessment,
    final String? healthSummary,
    final List<String>? healthTips,
    final List<String>? warnings,
    final String? disclaimer,
    final DateTime? analyzedAt,
    required final DateTime createdAt,
  }) = _$StoolAnalysisResponseImpl;

  factory _StoolAnalysisResponse.fromJson(Map<String, dynamic> json) =
      _$StoolAnalysisResponseImpl.fromJson;

  @override
  int get id;
  @override
  int get petId;
  @override
  String get petName;
  @override
  String get imageUrl;
  @override
  String get status;
  @override
  int? get overallScore;
  @override
  int? get colorScore;
  @override
  String? get colorAssessment;
  @override
  int? get consistencyScore;
  @override
  String? get consistencyAssessment;
  @override
  int? get coatingScore;
  @override
  String? get coatingAssessment;
  @override
  int? get contentsScore;
  @override
  String? get contentsAssessment;
  @override
  String? get healthSummary;
  @override
  List<String>? get healthTips;
  @override
  List<String>? get warnings;
  @override
  String? get disclaimer;
  @override
  DateTime? get analyzedAt;
  @override
  DateTime get createdAt;

  /// Create a copy of StoolAnalysisResponse
  /// with the given fields replaced by the non-null parameter values.
  @override
  @JsonKey(includeFromJson: false, includeToJson: false)
  _$$StoolAnalysisResponseImplCopyWith<_$StoolAnalysisResponseImpl>
  get copyWith => throw _privateConstructorUsedError;
}

MonthlyScore _$MonthlyScoreFromJson(Map<String, dynamic> json) {
  return _MonthlyScore.fromJson(json);
}

/// @nodoc
mixin _$MonthlyScore {
  int get year => throw _privateConstructorUsedError;
  int get month => throw _privateConstructorUsedError;
  double get averageScore => throw _privateConstructorUsedError;
  int get count => throw _privateConstructorUsedError;

  /// Serializes this MonthlyScore to a JSON map.
  Map<String, dynamic> toJson() => throw _privateConstructorUsedError;

  /// Create a copy of MonthlyScore
  /// with the given fields replaced by the non-null parameter values.
  @JsonKey(includeFromJson: false, includeToJson: false)
  $MonthlyScoreCopyWith<MonthlyScore> get copyWith =>
      throw _privateConstructorUsedError;
}

/// @nodoc
abstract class $MonthlyScoreCopyWith<$Res> {
  factory $MonthlyScoreCopyWith(
    MonthlyScore value,
    $Res Function(MonthlyScore) then,
  ) = _$MonthlyScoreCopyWithImpl<$Res, MonthlyScore>;
  @useResult
  $Res call({int year, int month, double averageScore, int count});
}

/// @nodoc
class _$MonthlyScoreCopyWithImpl<$Res, $Val extends MonthlyScore>
    implements $MonthlyScoreCopyWith<$Res> {
  _$MonthlyScoreCopyWithImpl(this._value, this._then);

  // ignore: unused_field
  final $Val _value;
  // ignore: unused_field
  final $Res Function($Val) _then;

  /// Create a copy of MonthlyScore
  /// with the given fields replaced by the non-null parameter values.
  @pragma('vm:prefer-inline')
  @override
  $Res call({
    Object? year = null,
    Object? month = null,
    Object? averageScore = null,
    Object? count = null,
  }) {
    return _then(
      _value.copyWith(
            year: null == year
                ? _value.year
                : year // ignore: cast_nullable_to_non_nullable
                      as int,
            month: null == month
                ? _value.month
                : month // ignore: cast_nullable_to_non_nullable
                      as int,
            averageScore: null == averageScore
                ? _value.averageScore
                : averageScore // ignore: cast_nullable_to_non_nullable
                      as double,
            count: null == count
                ? _value.count
                : count // ignore: cast_nullable_to_non_nullable
                      as int,
          )
          as $Val,
    );
  }
}

/// @nodoc
abstract class _$$MonthlyScoreImplCopyWith<$Res>
    implements $MonthlyScoreCopyWith<$Res> {
  factory _$$MonthlyScoreImplCopyWith(
    _$MonthlyScoreImpl value,
    $Res Function(_$MonthlyScoreImpl) then,
  ) = __$$MonthlyScoreImplCopyWithImpl<$Res>;
  @override
  @useResult
  $Res call({int year, int month, double averageScore, int count});
}

/// @nodoc
class __$$MonthlyScoreImplCopyWithImpl<$Res>
    extends _$MonthlyScoreCopyWithImpl<$Res, _$MonthlyScoreImpl>
    implements _$$MonthlyScoreImplCopyWith<$Res> {
  __$$MonthlyScoreImplCopyWithImpl(
    _$MonthlyScoreImpl _value,
    $Res Function(_$MonthlyScoreImpl) _then,
  ) : super(_value, _then);

  /// Create a copy of MonthlyScore
  /// with the given fields replaced by the non-null parameter values.
  @pragma('vm:prefer-inline')
  @override
  $Res call({
    Object? year = null,
    Object? month = null,
    Object? averageScore = null,
    Object? count = null,
  }) {
    return _then(
      _$MonthlyScoreImpl(
        year: null == year
            ? _value.year
            : year // ignore: cast_nullable_to_non_nullable
                  as int,
        month: null == month
            ? _value.month
            : month // ignore: cast_nullable_to_non_nullable
                  as int,
        averageScore: null == averageScore
            ? _value.averageScore
            : averageScore // ignore: cast_nullable_to_non_nullable
                  as double,
        count: null == count
            ? _value.count
            : count // ignore: cast_nullable_to_non_nullable
                  as int,
      ),
    );
  }
}

/// @nodoc
@JsonSerializable()
class _$MonthlyScoreImpl implements _MonthlyScore {
  const _$MonthlyScoreImpl({
    required this.year,
    required this.month,
    required this.averageScore,
    required this.count,
  });

  factory _$MonthlyScoreImpl.fromJson(Map<String, dynamic> json) =>
      _$$MonthlyScoreImplFromJson(json);

  @override
  final int year;
  @override
  final int month;
  @override
  final double averageScore;
  @override
  final int count;

  @override
  String toString() {
    return 'MonthlyScore(year: $year, month: $month, averageScore: $averageScore, count: $count)';
  }

  @override
  bool operator ==(Object other) {
    return identical(this, other) ||
        (other.runtimeType == runtimeType &&
            other is _$MonthlyScoreImpl &&
            (identical(other.year, year) || other.year == year) &&
            (identical(other.month, month) || other.month == month) &&
            (identical(other.averageScore, averageScore) ||
                other.averageScore == averageScore) &&
            (identical(other.count, count) || other.count == count));
  }

  @JsonKey(includeFromJson: false, includeToJson: false)
  @override
  int get hashCode =>
      Object.hash(runtimeType, year, month, averageScore, count);

  /// Create a copy of MonthlyScore
  /// with the given fields replaced by the non-null parameter values.
  @JsonKey(includeFromJson: false, includeToJson: false)
  @override
  @pragma('vm:prefer-inline')
  _$$MonthlyScoreImplCopyWith<_$MonthlyScoreImpl> get copyWith =>
      __$$MonthlyScoreImplCopyWithImpl<_$MonthlyScoreImpl>(this, _$identity);

  @override
  Map<String, dynamic> toJson() {
    return _$$MonthlyScoreImplToJson(this);
  }
}

abstract class _MonthlyScore implements MonthlyScore {
  const factory _MonthlyScore({
    required final int year,
    required final int month,
    required final double averageScore,
    required final int count,
  }) = _$MonthlyScoreImpl;

  factory _MonthlyScore.fromJson(Map<String, dynamic> json) =
      _$MonthlyScoreImpl.fromJson;

  @override
  int get year;
  @override
  int get month;
  @override
  double get averageScore;
  @override
  int get count;

  /// Create a copy of MonthlyScore
  /// with the given fields replaced by the non-null parameter values.
  @override
  @JsonKey(includeFromJson: false, includeToJson: false)
  _$$MonthlyScoreImplCopyWith<_$MonthlyScoreImpl> get copyWith =>
      throw _privateConstructorUsedError;
}

HealthTrendResponse _$HealthTrendResponseFromJson(Map<String, dynamic> json) {
  return _HealthTrendResponse.fromJson(json);
}

/// @nodoc
mixin _$HealthTrendResponse {
  int get petId => throw _privateConstructorUsedError;
  String get petName => throw _privateConstructorUsedError;
  List<MonthlyScore> get monthlyScores => throw _privateConstructorUsedError;
  double? get overallTrend => throw _privateConstructorUsedError;
  String? get trendMessage => throw _privateConstructorUsedError;

  /// Serializes this HealthTrendResponse to a JSON map.
  Map<String, dynamic> toJson() => throw _privateConstructorUsedError;

  /// Create a copy of HealthTrendResponse
  /// with the given fields replaced by the non-null parameter values.
  @JsonKey(includeFromJson: false, includeToJson: false)
  $HealthTrendResponseCopyWith<HealthTrendResponse> get copyWith =>
      throw _privateConstructorUsedError;
}

/// @nodoc
abstract class $HealthTrendResponseCopyWith<$Res> {
  factory $HealthTrendResponseCopyWith(
    HealthTrendResponse value,
    $Res Function(HealthTrendResponse) then,
  ) = _$HealthTrendResponseCopyWithImpl<$Res, HealthTrendResponse>;
  @useResult
  $Res call({
    int petId,
    String petName,
    List<MonthlyScore> monthlyScores,
    double? overallTrend,
    String? trendMessage,
  });
}

/// @nodoc
class _$HealthTrendResponseCopyWithImpl<$Res, $Val extends HealthTrendResponse>
    implements $HealthTrendResponseCopyWith<$Res> {
  _$HealthTrendResponseCopyWithImpl(this._value, this._then);

  // ignore: unused_field
  final $Val _value;
  // ignore: unused_field
  final $Res Function($Val) _then;

  /// Create a copy of HealthTrendResponse
  /// with the given fields replaced by the non-null parameter values.
  @pragma('vm:prefer-inline')
  @override
  $Res call({
    Object? petId = null,
    Object? petName = null,
    Object? monthlyScores = null,
    Object? overallTrend = freezed,
    Object? trendMessage = freezed,
  }) {
    return _then(
      _value.copyWith(
            petId: null == petId
                ? _value.petId
                : petId // ignore: cast_nullable_to_non_nullable
                      as int,
            petName: null == petName
                ? _value.petName
                : petName // ignore: cast_nullable_to_non_nullable
                      as String,
            monthlyScores: null == monthlyScores
                ? _value.monthlyScores
                : monthlyScores // ignore: cast_nullable_to_non_nullable
                      as List<MonthlyScore>,
            overallTrend: freezed == overallTrend
                ? _value.overallTrend
                : overallTrend // ignore: cast_nullable_to_non_nullable
                      as double?,
            trendMessage: freezed == trendMessage
                ? _value.trendMessage
                : trendMessage // ignore: cast_nullable_to_non_nullable
                      as String?,
          )
          as $Val,
    );
  }
}

/// @nodoc
abstract class _$$HealthTrendResponseImplCopyWith<$Res>
    implements $HealthTrendResponseCopyWith<$Res> {
  factory _$$HealthTrendResponseImplCopyWith(
    _$HealthTrendResponseImpl value,
    $Res Function(_$HealthTrendResponseImpl) then,
  ) = __$$HealthTrendResponseImplCopyWithImpl<$Res>;
  @override
  @useResult
  $Res call({
    int petId,
    String petName,
    List<MonthlyScore> monthlyScores,
    double? overallTrend,
    String? trendMessage,
  });
}

/// @nodoc
class __$$HealthTrendResponseImplCopyWithImpl<$Res>
    extends _$HealthTrendResponseCopyWithImpl<$Res, _$HealthTrendResponseImpl>
    implements _$$HealthTrendResponseImplCopyWith<$Res> {
  __$$HealthTrendResponseImplCopyWithImpl(
    _$HealthTrendResponseImpl _value,
    $Res Function(_$HealthTrendResponseImpl) _then,
  ) : super(_value, _then);

  /// Create a copy of HealthTrendResponse
  /// with the given fields replaced by the non-null parameter values.
  @pragma('vm:prefer-inline')
  @override
  $Res call({
    Object? petId = null,
    Object? petName = null,
    Object? monthlyScores = null,
    Object? overallTrend = freezed,
    Object? trendMessage = freezed,
  }) {
    return _then(
      _$HealthTrendResponseImpl(
        petId: null == petId
            ? _value.petId
            : petId // ignore: cast_nullable_to_non_nullable
                  as int,
        petName: null == petName
            ? _value.petName
            : petName // ignore: cast_nullable_to_non_nullable
                  as String,
        monthlyScores: null == monthlyScores
            ? _value._monthlyScores
            : monthlyScores // ignore: cast_nullable_to_non_nullable
                  as List<MonthlyScore>,
        overallTrend: freezed == overallTrend
            ? _value.overallTrend
            : overallTrend // ignore: cast_nullable_to_non_nullable
                  as double?,
        trendMessage: freezed == trendMessage
            ? _value.trendMessage
            : trendMessage // ignore: cast_nullable_to_non_nullable
                  as String?,
      ),
    );
  }
}

/// @nodoc
@JsonSerializable()
class _$HealthTrendResponseImpl implements _HealthTrendResponse {
  const _$HealthTrendResponseImpl({
    required this.petId,
    required this.petName,
    required final List<MonthlyScore> monthlyScores,
    this.overallTrend,
    this.trendMessage,
  }) : _monthlyScores = monthlyScores;

  factory _$HealthTrendResponseImpl.fromJson(Map<String, dynamic> json) =>
      _$$HealthTrendResponseImplFromJson(json);

  @override
  final int petId;
  @override
  final String petName;
  final List<MonthlyScore> _monthlyScores;
  @override
  List<MonthlyScore> get monthlyScores {
    if (_monthlyScores is EqualUnmodifiableListView) return _monthlyScores;
    // ignore: implicit_dynamic_type
    return EqualUnmodifiableListView(_monthlyScores);
  }

  @override
  final double? overallTrend;
  @override
  final String? trendMessage;

  @override
  String toString() {
    return 'HealthTrendResponse(petId: $petId, petName: $petName, monthlyScores: $monthlyScores, overallTrend: $overallTrend, trendMessage: $trendMessage)';
  }

  @override
  bool operator ==(Object other) {
    return identical(this, other) ||
        (other.runtimeType == runtimeType &&
            other is _$HealthTrendResponseImpl &&
            (identical(other.petId, petId) || other.petId == petId) &&
            (identical(other.petName, petName) || other.petName == petName) &&
            const DeepCollectionEquality().equals(
              other._monthlyScores,
              _monthlyScores,
            ) &&
            (identical(other.overallTrend, overallTrend) ||
                other.overallTrend == overallTrend) &&
            (identical(other.trendMessage, trendMessage) ||
                other.trendMessage == trendMessage));
  }

  @JsonKey(includeFromJson: false, includeToJson: false)
  @override
  int get hashCode => Object.hash(
    runtimeType,
    petId,
    petName,
    const DeepCollectionEquality().hash(_monthlyScores),
    overallTrend,
    trendMessage,
  );

  /// Create a copy of HealthTrendResponse
  /// with the given fields replaced by the non-null parameter values.
  @JsonKey(includeFromJson: false, includeToJson: false)
  @override
  @pragma('vm:prefer-inline')
  _$$HealthTrendResponseImplCopyWith<_$HealthTrendResponseImpl> get copyWith =>
      __$$HealthTrendResponseImplCopyWithImpl<_$HealthTrendResponseImpl>(
        this,
        _$identity,
      );

  @override
  Map<String, dynamic> toJson() {
    return _$$HealthTrendResponseImplToJson(this);
  }
}

abstract class _HealthTrendResponse implements HealthTrendResponse {
  const factory _HealthTrendResponse({
    required final int petId,
    required final String petName,
    required final List<MonthlyScore> monthlyScores,
    final double? overallTrend,
    final String? trendMessage,
  }) = _$HealthTrendResponseImpl;

  factory _HealthTrendResponse.fromJson(Map<String, dynamic> json) =
      _$HealthTrendResponseImpl.fromJson;

  @override
  int get petId;
  @override
  String get petName;
  @override
  List<MonthlyScore> get monthlyScores;
  @override
  double? get overallTrend;
  @override
  String? get trendMessage;

  /// Create a copy of HealthTrendResponse
  /// with the given fields replaced by the non-null parameter values.
  @override
  @JsonKey(includeFromJson: false, includeToJson: false)
  _$$HealthTrendResponseImplCopyWith<_$HealthTrendResponseImpl> get copyWith =>
      throw _privateConstructorUsedError;
}
